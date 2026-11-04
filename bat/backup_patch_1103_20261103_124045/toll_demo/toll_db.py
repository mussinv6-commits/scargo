"""
관제 시연의 DB 저장/조회 (최종합본 DB 7·8·9·10장을 먼저 실행해 둔 DB 가 필요)

[구조] 번호판 OCR 은 인천항 출차에서 한 번만 한다 (departure_logs). 톨게이트·검문소 계측 결과는 도로공사 연계(시연은 Mock)로 받아
       차량 번호로 매칭해 저장·표시한다. 우리는 단속·차단을 하지 않고, 확정 판정이 나오면 진출 권고를 만들어 관제자가 지시를 보낸다.

저장 (지점을 지날 때마다 한 트랜잭션):
  검문소: overload_checks (초과 확정이면 1차 + 재측정 2행). 첫 계측의 check_id 가 통행 묶음 키(trip_key)
  톨게이트: toll_passages(통과, trip_key) + overload_checks(측정) + toll_passages.fare_won(도로공사가 통보한 통행료)
  통행 시작 시: departure_logs(출차 OCR), toll_trip_quotes(예상 통행료, 심야할인 근거), toll_trip_positions(현재 위치)
  W-14: toll_route_events(이탈·복귀·검문소 경고), toll_trip_positions 갱신, 이탈로 톨게이트를 못 지나면 toll_trip_quotes 재계산
  관제자: toll_control_actions (진출 권고 지시 등)
조회: trips_data (W-12 계측 현황 · W-13 차량 관제 · W-14 지도가 같은 데이터를 본다)
"""
import json

import psycopg2
import psycopg2.extras

TOLL_LIMIT_KG = 40000
LIMIT_SQL = "LEAST(40000, COALESCE(NULLIF(vs.gross_weight_kg, 0), vs.empty_weight_kg + vs.max_payload_kg, 40000))"
AXLE_COLS = ', '.join(f'axle{i}_weight' for i in range(1, 9))

_points = {}
N_POINTS = 3


def connect(url):
    return psycopg2.connect(url, connect_timeout=3)


def _load_points(cur):
    if not _points:
        cur.execute("SELECT point_code, seq, station_id, tollgate_id, kind, toll_role FROM v_toll_leg1_points")
        for r in cur.fetchall():
            _points[r[0]] = dict(seq=r[1], station_id=r[2], tollgate_id=r[3], kind=r[4], role=r[5])
        if len(_points) < N_POINTS:
            _points.clear()
            raise RuntimeError('v_toll_leg1_points 에 지점 3곳이 없습니다. 팀 통합 DB + 팀DB_추가분.sql를 먼저 실행하세요.')
    return _points


def load_rates(cur):
    """DB 의 toll_fare_rates 로 toll_trip.RATES 를 덮어쓴다 (요금표를 DB 에서 관리)."""
    import toll_trip
    cur.execute("SELECT section_code, vehicle_class, amount, fare_basis FROM toll_fare_rates")
    rows = cur.fetchall()
    if rows:
        toll_trip.RATES.clear()
        for sec, cls, amt, basis in rows:
            toll_trip.RATES[(sec, int(cls))] = dict(amount=int(amt), basis=basis)


def ready(url):
    """최종합본 DB(7·8·9장)가 적용된 DB 인지 확인하고 요금표를 불러온다 -> (bool, 메시지)"""
    try:
        c = connect(url)
        try:
            cur = c.cursor()
            _load_points(cur)
            cur.execute("SELECT 1 FROM v_toll_leg1_trip_current LIMIT 1")
            cur.execute("SELECT 1 FROM toll_control_actions LIMIT 1")          # 10장 테이블 확인
            load_rates(cur)
            return True, ''
        finally:
            c.close()
    except Exception as e:
        return False, f'{type(e).__name__}: {e}'


def jsonable(row):
    out = {}
    for k, v in row.items():
        if hasattr(v, 'isoformat'):
            v = v.isoformat()
        elif v.__class__.__name__ == 'Decimal':
            v = float(v)
        out[k] = v
    return out


def _reason_text(r):
    return {'총중량': '총중량 초과', '축하중': '축하중 초과', '총중량+축하중': '총중량·축하중 초과'}.get(r, None)


def _insert_check(cur, pts, trip, key, m, ev, retry, violation, passed, reason, fine, reoff):
    v = trip['vehicle']
    axles = list(m['axle_kg'])[:8]
    a = axles + [0] * (8 - len(axles))
    ocr = ev.get('ocr') or {}
    cols = ['vehicle_no', 'usage_purpose', 'empty_weight_kg', 'total_weight_kg', 'max_payload_kg', 'vehicle_axle_count', 'axle_count']
    vals = [trip['plate'], v.get('usage_purpose'), v.get('empty_weight_kg'), sum(axles), v.get('max_payload_kg'),
            v.get('axle_count'), len(axles)]
    for i in range(8):
        cols += [f'axle{i + 1}_weight', f'axle{i + 1}_wheel_left', f'axle{i + 1}_wheel_right']
        vals += [a[i], a[i] // 2, a[i] - a[i] // 2]
    cols += ['is_violation', 'violation_reason', 'retry_count', 'is_passed', 'station_id', 'check_type', 'trip_key',
             'fine_manwon', 'is_reoffense', 'checked_at']
    vals += [violation, reason, retry, passed, pts[key]['station_id'], 'HIGHWAY', trip.get('trip_key'), fine, reoff]
    ph = ', '.join(['%s'] * len(vals)) + ', clock_timestamp()'
    cur.execute(f"INSERT INTO overload_checks ({', '.join(cols)}) VALUES ({ph}) RETURNING check_id", vals)
    cid = cur.fetchone()[0]
    if trip.get('trip_key') is None:                              # 통행의 첫 검사: 자기 check_id 가 묶음 키
        trip['trip_key'] = cid
        cur.execute("UPDATE overload_checks SET trip_key = %s WHERE check_id = %s", (cid, cid))
        q = trip['quote']
        cur.execute("""INSERT INTO toll_trip_quotes (trip_key, vehicle_no, vehicle_class, departed_at, expected_minutes, base_total, expected_total,
                                                     has_estimate, night_excluded, fined_trips_1y, lines, route_type)
                       VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
                    (cid, trip['plate'], q['vehicle_class'], q['departed_at'], q['expected_minutes'], q['base_total'], q['expected_total'],
                     q['has_estimate'], q['night_excluded'], q['fined_trips_1y'], json.dumps(q['lines'], ensure_ascii=False), trip.get('route_type', 'BASIC')))
        cur.execute("INSERT INTO toll_trip_positions (trip_key, km, route_status, route_type) VALUES (%s, 0, 'NORMAL', %s) ON CONFLICT DO NOTHING",
                    (cid, trip.get('route_type', 'BASIC')))
        if trip.get('departure_id'):
            cur.execute("UPDATE departure_logs SET trip_key = %s WHERE departure_id = %s", (cid, trip['departure_id']))
    return cid


def save_event(url, trip, ev):
    """지점 1곳 처리 결과를 저장한다. 첫 지점이면 trip['trip_key'] 가 채워진다."""
    key = ev['key']
    ocr = ev.get('ocr') or {}
    conf = ocr.get('confidence')
    c = connect(url)
    try:
        cur = c.cursor()
        pts = _load_points(cur)
        pt = pts[key]
        reoff = ev['action'].endswith('REOFFENSE')
        first_reason = _reason_text(ev['over_reason'])
        if pt['kind'] == 'CHECKPOINT':
            if ev['retest']:                                       # 검문소 1차 과적 -> 재측정
                _insert_check(cur, pts, trip, key, ev, ev, 0, True, False, first_reason, 0, False)
                rt = ev['retest']
                final_over = rt['over']
                _insert_check(cur, pts, trip, key, rt, ev, 1, final_over, not final_over,
                              _reason_text(rt['over_reason']) if final_over else None, ev['fine_manwon'], reoff)
            else:
                over = ev['verdict'] == 'OVER'
                _insert_check(cur, pts, trip, key, ev, ev, 0, over, True, first_reason if over else None, ev['fine_manwon'], reoff)
        else:                                                      # 톨게이트: 통과 기록 + 측정 + (요금)
            entry = trip.get('ticket_pid') if pt['role'] == 'CLOSED_EXIT' else None
            cur.execute("""INSERT INTO toll_passages (tollgate_id, recognized_plate_no, vehicle_no, recognition_status, passage_status, passed_at,
                                                      plate_confidence, front_image_url, plate_crop_url, entry_passage_id, trip_key)
                           VALUES (%s, %s, %s, 'SUCCESS', 'PASSED', clock_timestamp(), %s, %s, %s, %s, %s) RETURNING passage_id""",
                        (pt['tollgate_id'], trip['plate'], trip['plate'], None, None, None, entry, trip.get('trip_key')))      # 톨게이트는 OCR 안 함: 출차 때 읽은 번호로 도로공사 결과를 매칭
            pid = cur.fetchone()[0]
            over = ev['verdict'] == 'OVER'
            _insert_check(cur, pts, trip, key, ev, ev, 0, over, True, first_reason if over else None, ev['fine_manwon'], reoff)
            if pt['role'] == 'CLOSED_ENTRY':
                trip['ticket_pid'] = pid                          # 폐쇄식 통행권 발권: 정산 지점이 이 통과 기록을 가리킨다
            pay = ev.get('payment')
            if pay:
                closed = pay['system'] == 'CLOSED'
                cur.execute("UPDATE toll_passages SET fare_won = %s, fare_discount_pct = %s WHERE passage_id = %s",
                            (pay['amount'], pay['discount_pct'], pid))
        c.commit()
    except Exception:
        c.rollback()
        if len(trip['events']) <= 1:                              # 첫 지점 저장이 롤백됐으면 묶음 키도 무효
            trip['trip_key'] = None
        raise
    finally:
        c.close()


def fined_trips_1y(url, plate):
    """최근 1년 안에 과태료가 부과된 통행 수 (v_toll_night_exclusion). 2회 이상이면 심야할인 제외."""
    rows = _rows(url, "SELECT fined_trips_1y FROM v_toll_night_exclusion WHERE vehicle_no = %s", (plate,))
    return int(rows[0]['fined_trips_1y']) if rows else 0




def _rows(url, sql, args=None):
    c = connect(url)
    try:
        cur = c.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cur.execute(sql, args)
        return [dict(r) for r in cur.fetchall()]
    finally:
        c.close()


def _exec(url, sql, args=None, returning=False):
    c = connect(url)
    try:
        cur = c.cursor()
        cur.execute(sql, args)
        out = cur.fetchone()[0] if returning else None
        c.commit()
        return out
    except Exception:
        c.rollback()
        raise
    finally:
        c.close()


# ------------------------------- 출차 · 경로 이벤트 · 위치 · 관제 조치 저장 -------------------------------
def save_departure(url, plate, confidence, result, vehicle_no=None, crop_url=None, front_url=None):
    """인천항 출차 OCR 기록. result: DEPARTED / OCR_FAILED / UNREGISTERED -> departure_id"""
    return _exec(url, """INSERT INTO departure_logs (recognized_plate_no, vehicle_no, plate_confidence, result, plate_crop_url, front_image_url)
                         VALUES (%s, %s, %s, %s, %s, %s) RETURNING departure_id""",
                 (plate or None, vehicle_no, confidence, result, crop_url, front_url), returning=True)


def save_progress(url, trip_key, events, first_seq, pos):
    """W-14 경로 이벤트(first_seq 번째부터)와 현재 위치를 저장한다. events: route_watch.Watch.events 의 새로 생긴 항목들."""
    c = connect(url)
    try:
        cur = c.cursor()
        for k, e in enumerate(events):
            cur.execute("""INSERT INTO toll_route_events (trip_key, seq, kind, level, km, text, occurred_at)
                           VALUES (%s, %s, %s, %s, %s, %s, to_timestamp(%s)) ON CONFLICT (trip_key, seq) DO NOTHING""",
                        (trip_key, first_seq + k, e['kind'], e['level'], e.get('km'), e['text'], e['t']))
        if pos:
            cur.execute("""INSERT INTO toll_trip_positions (trip_key, km, lat, lng, route_status, dist_m, route_type, updated_at)
                           VALUES (%s, %s, %s, %s, %s, %s, %s, clock_timestamp())
                           ON CONFLICT (trip_key) DO UPDATE SET km = EXCLUDED.km, lat = EXCLUDED.lat, lng = EXCLUDED.lng,
                                route_status = EXCLUDED.route_status, dist_m = EXCLUDED.dist_m, route_type = EXCLUDED.route_type, updated_at = EXCLUDED.updated_at""",
                        (trip_key, pos['km'], pos['lat'], pos['lng'], pos['status'], pos['dist_m'], pos['route_type']))
        c.commit()
    except Exception:
        c.rollback()
        raise
    finally:
        c.close()


def save_reroute(url, trip_key, total, note, sections):
    """경로 이탈로 톨게이트를 지나지 않게 되면 예상 통행료를 다시 계산해 저장한다 (원래 expected_total 은 그대로 둔다)."""
    _exec(url, "UPDATE toll_trip_quotes SET reroute_total = %s, reroute_note = %s, skipped_sections = %s::jsonb WHERE trip_key = %s",
          (total, note, json.dumps(sections), trip_key))


ACTION_TEXT = {'EXIT_ADVISE': '진출 권고', 'HOLD_NOTICE': '검문소 조치 확인 요청', 'CAUTION_NOTICE': '적재 점검 안내', 'CHECKPOINT_NOTICE': '검문소 전방 안내', 'REROUTE_ADVISE': '대안 경로 안내'}


def issue_action(url, trip_key, action_type, message, basis_point=None, basis_check_id=None, issued_by='관제자'):
    """관제자가 운전자에게 지시/안내를 보낸다 -> action_id. 같은 근거(basis_check_id)로 같은 종류를 두 번 보내지 않는다."""
    if action_type not in ACTION_TEXT:
        raise ValueError('알 수 없는 조치 종류입니다.')
    rows = _rows(url, "SELECT vehicle_no FROM toll_trip_quotes WHERE trip_key = %s", (trip_key,))
    if not rows:
        raise ValueError('통행을 찾을 수 없습니다.')
    dup = _rows(url, "SELECT action_id FROM toll_control_actions WHERE trip_key = %s AND action_type = %s AND basis_point IS NOT DISTINCT FROM %s AND basis_check_id IS NOT DISTINCT FROM %s",
                (trip_key, action_type, basis_point, basis_check_id))
    if dup and action_type != 'CHECKPOINT_NOTICE':
        raise ValueError('이미 같은 지시를 보냈습니다.')
    return _exec(url, """INSERT INTO toll_control_actions (trip_key, vehicle_no, action_type, basis_point, basis_check_id, message, issued_by)
                         VALUES (%s, %s, %s, %s, %s, %s, %s) RETURNING action_id""",
                 (trip_key, rows[0]['vehicle_no'], action_type, basis_point, basis_check_id, message, issued_by), returning=True)


# ------------------------------- 조회 -------------------------------
LEVELS = ('NORMAL', 'CAUTION', 'CONFIRMED')
LEVEL_RANK = {k: i for i, k in enumerate(LEVELS)}
AXLE_LIMIT_KG = 10000


def level_of(total, limit, axles, violation):
    """정상 NORMAL / 주의 CAUTION(기준 초과, 허용 오차 이내) / 확정 CONFIRMED(허용 오차 초과). DB 뷰 v_toll_leg1_check_level 과 같은 규칙."""
    if violation:
        return 'CONFIRMED'
    if (total and limit and total > limit) or (axles and max(axles) > AXLE_LIMIT_KG):
        return 'CAUTION'
    return 'NORMAL'


def fiat(n):
    return f'{int(n):,}원'


def _build_recs(t):
    """판정 결과에서 관제자 권고를 만든다. issued 가 있으면 이미 지시를 보낸 것."""
    pts = [p for p in t['point_list'] if p['final']]
    acts = t['actions']
    done = lambda typ: next((a for a in acts if a['action_type'] == typ), None)
    recs = []
    conf = [p for p in pts if p['level'] == 'CONFIRMED']
    if conf:
        p = conf[-1]
        f = p['final']
        why = f.get('violation_reason') or '과적'
        w = f"{p['name']} 계측 {tons_s(f['total_weight_kg'])} / 기준 {tons_s(t['limit_kg'])}"
        if not f['is_passed']:
            recs.append(dict(type='HOLD_NOTICE', level='CONFIRMED', point=p['code'], check_id=f['check_id'], title='검문소 조치 확인 요청',
                             reason=f'{w} — {why}. 재측정에서도 확정되어 검문소가 운행 중지 조치를 했습니다.',
                             message=f"{p['name']}에서 {why}로 운행 중지 조치되었습니다. 검문소의 안내에 따르고, 적재량 조정 후 재출발 일정을 관제에 알려 주세요.",
                             issued=done('HOLD_NOTICE')))
        else:
            recs.append(dict(type='EXIT_ADVISE', level='CONFIRMED', point=p['code'], check_id=f['check_id'], title='진출 권고',
                             reason=f'{w} — {why}. 도로공사가 과태료를 통보했고 차량은 통과했습니다.',
                             message=f"{p['name']} 계측 결과 {why}로 확정되었습니다. 다음 진출 가능 지점(성남IC)에서 진출해 적재량을 조정해 주세요. (권고이며 강제는 아닙니다)",
                             issued=done('EXIT_ADVISE')))
    else:
        cau = [p for p in pts if p['level'] == 'CAUTION']
        if cau:
            p = cau[-1]
            f = p['final']
            recs.append(dict(type='CAUTION_NOTICE', level='CAUTION', point=p['code'], check_id=f['check_id'], title='적재 점검 안내 (선택)',
                             reason=f"{p['name']} 계측 {tons_s(f['total_weight_kg'])} / 기준 {tons_s(t['limit_kg'])} — 기준을 넘었지만 허용 오차(10%) 이내입니다.",
                             message=f"{p['name']} 계측 결과 기준을 약간 넘었습니다(허용 오차 이내). 적재 상태를 점검해 주세요.",
                             issued=done('CAUTION_NOTICE')))
    pos = t.get('position')
    if pos and pos['route_status'] == 'DEVIATING':
        dv = [e for e in t['route_events'] if e['kind'] == 'DEVIATE']
        if dv:
            e = dv[-1]
            recs.append(dict(type='REROUTE_ADVISE', level='DEVIATION', point='DEVIATE', check_id=e['seq'], title='대안 경로 안내',
                             reason=f"기본 경로(최단 경로)를 이탈해 주행 중입니다 ({e['km']}km 부근에서 이탈, 이탈 사유는 알 수 없음).",
                             message='기본 경로를 이탈했습니다. 기본 경로(성남IC 진출 → 곤지암 허브)로 복귀하는 대안 경로를 앱으로 안내합니다. 확인해 주세요. (권고이며 강제는 아닙니다)',
                             issued=next((a for a in acts if a['action_type'] == 'REROUTE_ADVISE' and a['basis_check_id'] == e['seq']), None)))
    return recs


def tons_s(kg):
    return '—' if kg is None else f'{kg / 1000:.1f}t'


def trips_data(url, since, until=None):
    """통행 1건당 1행: 차량 · 현재 위치 · 지점별 계측/판정 · 예상 통행료 · 경로 이벤트 · 관제 권고/조치.  W-12 · W-13 · W-14 가 같이 쓴다."""
    trips = _rows(url, f"""SELECT v.trip_id, v.vehicle_no, v.entered_at, v.plate_confidence, v.plate_crop_url,
                                  v.last_point, v.last_point_name, v.last_seq, v.last_km, v.status, v.fine_manwon, v.paid_won,
                                  v.vehicle_class, v.expected_total, v.base_total, v.has_estimate, v.updated_at,
                                  c.company_name, t.truck_type, vs.axle_count, {LIMIT_SQL} AS limit_kg
                             FROM v_toll_leg1_trip_current v
                             JOIN trucks t ON t.vehicle_no = v.vehicle_no
                             JOIN companies c ON c.company_id = t.company_id
                             LEFT JOIN vehicle_specs vs ON vs.vehicle_no = v.vehicle_no
                            WHERE v.entered_at >= COALESCE(%s::timestamptz, '-infinity'::timestamptz)
                              AND v.entered_at < COALESCE(%s::timestamptz, 'infinity'::timestamptz)
                            ORDER BY v.entered_at DESC LIMIT 500""", (since, until))
    if not trips:
        return []
    keys = [t['trip_id'] for t in trips]
    checks, quotes, poss, evs, acts, pays, deps = {}, {}, {}, {}, {}, {}, {}
    for r in _rows(url, f"""SELECT c.check_id, c.trip_key, c.total_weight_kg, c.is_violation, c.violation_reason, c.retry_count, c.is_passed,
                                   c.checked_at, c.fine_manwon, c.is_reoffense, p.point_code, p.seq, p.point_name, p.kind, p.km_from_start,
                                   ARRAY[{AXLE_COLS.replace('axle', 'c.axle')}] AS axles
                              FROM overload_checks c JOIN v_toll_leg1_points p ON p.station_id = c.station_id
                             WHERE c.trip_key = ANY(%s) ORDER BY c.check_id""", (keys,)):
        r['axles'] = [a for a in r['axles'] if a]
        checks.setdefault(r['trip_key'], []).append(r)
    for q in _rows(url, """SELECT trip_key, lines, night_excluded, fined_trips_1y, expected_minutes, departed_at, route_type, reroute_total, reroute_note, skipped_sections
                             FROM toll_trip_quotes WHERE trip_key = ANY(%s)""", (keys,)):
        quotes[q['trip_key']] = q
    for p in _rows(url, "SELECT * FROM toll_trip_positions WHERE trip_key = ANY(%s)", (keys,)):
        poss[p['trip_key']] = p
    for e in _rows(url, "SELECT trip_key, seq, kind, level, km, text, occurred_at FROM toll_route_events WHERE trip_key = ANY(%s) ORDER BY trip_key, seq", (keys,)):
        evs.setdefault(e['trip_key'], []).append(e)
    for a in _rows(url, "SELECT action_id, trip_key, action_type, basis_point, basis_check_id, message, issued_by, issued_at, delivery_status FROM toll_control_actions WHERE trip_key = ANY(%s) ORDER BY action_id", (keys,)):
        acts.setdefault(a['trip_key'], []).append(a)
    for p in _rows(url, """SELECT tp.trip_key, vp.point_code, tp.fare_won AS amount, tp.fare_discount_pct AS discount_pct, tp.passed_at AS paid_at
                             FROM toll_passages tp JOIN v_toll_leg1_points vp ON vp.tollgate_id = tp.tollgate_id
                            WHERE tp.trip_key = ANY(%s) AND tp.fare_won IS NOT NULL AND tp.passage_status = 'PASSED' ORDER BY tp.passage_id""", (keys,)):
        pays.setdefault(p['trip_key'], {})[p['point_code']] = p
    for d in _rows(url, "SELECT trip_key, departed_at, plate_confidence, plate_crop_url FROM departure_logs WHERE trip_key = ANY(%s)", (keys,)):
        deps[d['trip_key']] = d
    meta = _rows(url, "SELECT point_code, seq, point_name, kind, toll_role, km_from_start FROM v_toll_leg1_points ORDER BY seq")
    for t in trips:
        k = t['trip_id']
        t['checks'] = checks.get(k, [])
        t['quote'] = quotes.get(k)
        t['position'] = poss.get(k)
        t['route_events'] = evs.get(k, [])
        t['actions'] = acts.get(k, [])
        t['departure'] = deps.get(k)
        skipped = set((t['quote'] or {}).get('skipped_sections') or [])
        point_list = []
        for m in meta:
            cs = [c for c in t['checks'] if c['point_code'] == m['point_code']]
            first = next((c for c in cs if c['retry_count'] == 0), None)
            final = cs[-1] if cs else None
            pay = pays.get(k, {}).get(m['point_code'])
            sec_skipped = False
            if not cs and m['kind'] == 'TOLLGATE':
                sec = {'TG_NAMINCHEON': 'NAMINCHEON_TG', 'TG_CHEONGGYE': 'CHEONGGYE_TG'}.get(m['point_code'])
                sec_skipped = sec in skipped
            point_list.append(dict(code=m['point_code'], name=m['point_name'], kind=m['kind'], km=float(m['km_from_start']), first=first, final=final,
                                   retest=len(cs) > 1, pay=pay, skipped=sec_skipped,
                                   level=level_of(final['total_weight_kg'], t['limit_kg'], final['axles'], final['is_violation']) if final else None,
                                   first_level=level_of(first['total_weight_kg'], t['limit_kg'], first['axles'], first['is_violation']) if first else None))
        t['point_list'] = point_list
        lv = [p['level'] for p in point_list if p['level']]
        t['level'] = max(lv, key=lambda x: LEVEL_RANK[x]) if lv else 'NORMAL'
        t['recs'] = _build_recs(t)
        t['pending'] = sum(1 for r in t['recs'] if not r['issued'] and r['level'] == 'CONFIRMED')
        t['paid_won'] = int(t['paid_won'] or 0)
    _hub_link(url, trips)
    return trips


def _hub_link(url, trips):
    """26.10.29 허브 입차 연동: 통행마다 t['hub'] = {state, arrived_at, entered_at, decision, confidence, queue} 를 붙인다.
    state: ENROUTE(곤지암 도착 전) / ARRIVED(도착했고 허브 입차 전) / ENTERED(허브 입차 기록 있음).  queue = 도착했지만 아직 입차 안 한 차량의 입차 순번(도착 순서, 1부터).
    허브 입차 기록은 백엔드(HubVehicleService)가 hub_vehicle_logs 에 남기며, 테이블이 없거나 조회가 막히면 연동 없이(ENROUTE/ARRIVED 까지만) 보여 준다."""
    import datetime
    plates = sorted({(t['vehicle_no'] or '').replace(' ', '') for t in trips if t.get('vehicle_no')})
    logs = []
    if plates:
        try:
            logs = _rows(url, """SELECT log_id, created_at, decision, ocr_confidence, COALESCE(recognized_plate, photo_plate) AS plate
                                   FROM hub_vehicle_logs
                                  WHERE direction = 'ENTRY' AND REPLACE(COALESCE(recognized_plate, photo_plate), ' ', '') = ANY(%s)
                                  ORDER BY created_at""", (plates,))
        except Exception:
            logs = []
    waiting = []
    for t in trips:
        p = t.get('position') or {}
        arrived = p.get('updated_at') if p.get('route_status') == 'ARRIVED' else None
        h = dict(state='ENROUTE', arrived_at=arrived, entered_at=None, decision=None, confidence=None, queue=None)
        if arrived:
            h['state'] = 'ARRIVED'
            key = (t['vehicle_no'] or '').replace(' ', '')
            lim = arrived - datetime.timedelta(minutes=1)
            try:
                hit = next((l for l in logs if (l['plate'] or '').replace(' ', '') == key and l['created_at'] >= lim), None)
            except TypeError:                                         # 시간대 유무가 다르면 비교하지 않고 입차 전으로 본다
                hit = None
            if hit:
                h.update(state='ENTERED', entered_at=hit['created_at'], decision=hit['decision'], confidence=hit['ocr_confidence'])
            else:
                waiting.append((arrived, t['trip_id'], h))
        t['hub'] = h
    for i, (_, _, h) in enumerate(sorted(waiting, key=lambda x: (x[0], x[1])), 1):
        h['queue'] = i


def departure_issues(url, since, until=None):
    """출차 OCR 단계에서 막힌 건 (인식 실패 / 미등록 차량)"""
    return _rows(url, """SELECT departure_id, departed_at, recognized_plate_no, plate_confidence, result FROM departure_logs
                          WHERE result <> 'DEPARTED' AND departed_at >= COALESCE(%s::timestamptz, '-infinity'::timestamptz)
                            AND departed_at < COALESCE(%s::timestamptz, 'infinity'::timestamptz)
                          ORDER BY departed_at DESC LIMIT 20""", (since, until))
