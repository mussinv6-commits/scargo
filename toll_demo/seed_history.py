"""구간 관제 과거 운행 기록(누적 기록) 생성 — 발표 화면에 기록이 쌓여 있는 모습을 보여 주기 위한 샘플 데이터.

  python seed_history.py --db "postgresql://postgres:비밀번호@localhost:5432/scargo" [--days 14] [--count 36] [--seed 7]

- 실제 시연과 같은 로직(toll_trip · route_watch · toll_db)으로 통행을 끝까지 돌려 저장한 뒤, 시각만 과거로 옮긴다.
  그래서 판정 · 통행료(심야할인 포함) · 경로 이벤트 · 도착 기록이 시연 때와 똑같이 남는다.
- 분포: 정상 약 80% · 주의 약 15%(우회 경로 이탈) · 확정 약 5%(통행료 구간 적발 1 · 검문소 정차 1).
- 먼저 seed_data.bat 로 차량 제원을 채워 두어야 한다. 지우려면 구간관제_기록삭제.sql.
"""
import argparse, datetime, random, sys
import psycopg2, psycopg2.extras
import toll_trip as T
import route_watch
import toll_db

RANK = {'NORMAL': 0, 'CAUTION': 1, 'CONFIRMED': 2}


def trip_level(trip):
    lv = [e.get('level', 'NORMAL') for e in trip['events']]
    return max(lv, key=lambda x: RANK[x]) if lv else 'NORMAL'


def sync_trip(url, trip):          # toll_web.sync_trip 과 같은 처리 (화면 서버 없이 쓰려고 복사)
    w = trip['watch']
    km = w.last['km'] if w.last else 0.0
    while trip['status'] == 'RUNNING' and trip['step'] < T.N:
        p = T.POINTS[trip['step']]
        if p['key'] in w.skipped_keys:
            T.skip_point(trip, p['key']); continue
        if p['km'] > km + 1e-6:
            break
        ev = T.advance(trip)
        if ev is None:
            break
        toll_db.save_event(url, trip, ev)
    if w.skipped_keys and trip.get('rerouted') != tuple(w.skipped_keys):
        rr = T.reroute_quote(trip, w.skipped_keys)
        toll_db.save_reroute(url, trip['trip_key'], rr['total'], rr['note'], rr['skipped_sections'])
        trip['rerouted'] = tuple(w.skipped_keys)
    w.risk = trip_level(trip)
    if trip['status'] == 'HALTED' and not w.stopped:
        last = trip['events'][-1]
        w.stop(f"{last['name']}에서 검문소 조치로 운행 중지 — 정차 (도로공사/검문소 통보)")
    if trip.get('trip_key'):
        saved = trip.get('saved_events', 0)
        new = w.events[saved:]
        pos = None
        if w.last:
            st = 'STOPPED' if w.stopped else 'ARRIVED' if w.at_end() else 'DEVIATING' if w.status == 'DEVIATING' else 'NORMAL'
            pos = dict(km=w.last['km'], lat=w.last['lat'], lng=w.last['lng'], status=st, dist_m=w.last['dist_m'], route_type=w.route_type)
        toll_db.save_progress(url, trip['trip_key'], new, saved + 1, pos)
        trip['saved_events'] = saved + len(new)


def shift_times(url, trip, dep_id, depart, t0, t1):
    """저장된 시각을 [출발 시각 ~ 출발+소요시간] 으로 펼친다. t0/t1: 이 통행을 만드는 동안의 실제 시작/끝 (UTC)."""
    span = max((t1 - t0).total_seconds(), 0.001)
    k = min(trip['quote']['expected_minutes'] * 60.0, 3 * 3600) / span
    dep = depart.astimezone(datetime.timezone.utc)
    key = trip['trip_key']

    def e(col):                                   # 새 시각 = 출발 + (원래 시각 - 시작) x 배율
        return f"%s::timestamptz + ({col} - %s::timestamptz) * %s"
    c = psycopg2.connect(url); cur = c.cursor()
    P = (dep, t0, k)
    cur.execute(f"UPDATE departure_logs SET departed_at = {e('departed_at')} WHERE departure_id = %s", P + (dep_id,))
    cur.execute(f"UPDATE overload_checks SET checked_at = {e('checked_at')} WHERE trip_key = %s", P + (key,))
    cur.execute(f"UPDATE toll_passages SET detected_at = {e('detected_at')}, passed_at = {e('passed_at')}, created_at = {e('created_at')} WHERE trip_key = %s",
                P + P + P + (key,))
    cur.execute(f"UPDATE toll_trip_positions SET updated_at = {e('updated_at')} WHERE trip_key = %s", P + (key,))
    cur.execute(f"UPDATE toll_route_events SET occurred_at = {e('occurred_at')} WHERE trip_key = %s", P + (key,))
    cur.execute(f"UPDATE toll_trip_quotes SET created_at = {e('created_at')} WHERE trip_key = %s", P + (key,))
    c.commit(); c.close()


def pick_departure(rnd, day, hour_weights):
    h = rnd.choices(range(24), weights=hour_weights)[0]
    return datetime.datetime.combine(day, datetime.time(h, rnd.randint(0, 59), rnd.randint(0, 59))).astimezone()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--db', required=True); ap.add_argument('--days', type=int, default=14)
    ap.add_argument('--count', type=int, default=36); ap.add_argument('--seed', type=int, default=7)
    a = ap.parse_args()
    rnd = random.Random(a.seed)
    c = psycopg2.connect(a.db); cur = c.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    cur.execute("""SELECT t.vehicle_no, vs.usage_purpose, vs.empty_weight_kg, vs.gross_weight_kg, vs.max_payload_kg, vs.tire_count, vs.axle_count
                     FROM trucks t JOIN vehicle_specs vs ON vs.vehicle_no = t.vehicle_no WHERE vs.gross_weight_kg > 0 AND vs.axle_count >= 2""")
    vehicles = [dict(r) for r in cur.fetchall()]; c.close()
    if not vehicles:
        sys.exit('제원이 있는 차량이 없습니다. 먼저 seed_data.bat 를 실행하세요.')
    ok, why = toll_db.ready(a.db)
    if not ok:
        sys.exit('DB 에 구간 관제 구조가 없습니다 (팀DB_추가분.sql 실행 필요): ' + why)
    n = a.count
    plan = ['A'] * round(n * 0.80) + ['F'] * round(n * 0.14) + ['B', 'C']
    plan = (plan + ['A'] * n)[:n]
    rnd.shuffle(plan)
    detour_a = set(rnd.sample([i for i, s in enumerate(plan) if s == 'A'], min(2, plan.count('A'))))
    today = datetime.date.today()
    days = [today - datetime.timedelta(days=d) for d in range(a.days, 0, -1)]       # 어제부터 거슬러 days 일
    hw = [3, 2, 2, 2, 3, 6, 10, 14, 14, 12, 10, 9, 9, 10, 12, 12, 10, 8, 6, 5, 4, 4, 3, 3]   # 시간대별 출차 가중치 (새벽 일부 포함 -> 심야할인 사례)
    slots = sorted(pick_departure(rnd, rnd.choice(days), hw) for _ in range(n))     # 시간순으로 만들어야 최근 1년 과태료 횟수가 맞게 쌓인다
    made = 0
    last = {}
    for i, (sc, dep) in enumerate(zip(plan, slots)):
        route = 'DETOUR' if sc == 'F' or i in detour_a else 'BASIC'
        cand = [v for v in vehicles if T.eligible(sc, v)[0]
                and all(abs((dep - d).total_seconds()) > 4 * 3600 for d in last.get(v['vehicle_no'], []))]   # 같은 차량이 운행 중에 또 출발하지 않게
        if not cand:
            continue
        v = rnd.choice(cand)
        plate = v['vehicle_no']
        last.setdefault(plate, []).append(dep)
        fined = toll_db.fined_trips_1y(a.db, plate)
        trip = T.new_trip(format(rnd.getrandbits(40), 'x'), sc, plate, v, dep.replace(tzinfo=None), fined, route)
        t0 = datetime.datetime.now(datetime.timezone.utc)
        conf = round(rnd.uniform(93.0, 99.9), 2)
        dep_id = toll_db.save_departure(a.db, plate, conf, 'DEPARTED', plate, None)
        trip['departure_id'] = dep_id
        w = route_watch.Watch(plate, route, 'NORMAL'); trip['watch'] = w
        for _ in range(20):
            w.step(1)
            if w.last and w.last['km'] >= T.POINTS[0]['km']:
                break
        sync_trip(a.db, trip)
        for _ in range(2000):
            if w.stopped or w.at_end():
                break
            w.step(1); sync_trip(a.db, trip)
        t1 = datetime.datetime.now(datetime.timezone.utc)
        shift_times(a.db, trip, dep_id, dep, t0, t1)
        made += 1
        print(f"{dep:%m-%d %H:%M}  {plate}  {T.SCENARIOS[sc]['name']}  {route}  -> {'정차' if w.stopped else '도착'}")
    print(f'완료: {made}건 ({a.days}일치)')


if __name__ == '__main__':
    main()
