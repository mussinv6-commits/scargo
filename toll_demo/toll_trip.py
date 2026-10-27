"""
톨게이트·검문소 1구간(인천 월미로 -> 곤지암 허브, 기본 경로 79.8km) 시연 로직 (화면·DB 와 무관한 순수 계산).
모든 규칙값은 이 파일 위쪽 상수만 고치면 바뀐다.

[구조] 톨게이트·검문소는 도로공사 등 외부 기관 시설이다. SCARGO 는 그 계측 결과(연계 API, 시연에서는 이 파일의 시나리오가 만드는 Mock 이벤트)를
       받아 판정을 표시·기록하고 관제한다.

[시연용 규칙 - 실제 법규를 그대로 옮긴 것이 아님]
  지점 3곳:  인천 과적검문소 -> 남인천TG(개방식) -> 청계TG(개방식) -> (성남IC 진출. 요금소 없음, 성남TG 는 이용하지 않음)
  판정:      측정 총중량 > 기준 총중량 x (1 + 허용오차)  또는  어느 한 축의 하중 > 축하중 기준 x (1 + 허용오차)  이면 과적
  톨게이트:  초과 확정이면 통과하고 과태료만 도로공사가 통보 (재측정 없음)
  검문소:    초과 확정이면 재측정 1회. 재측정에서 기준 이내/오차 범위면 통과, 다시 확정이면 검문소가 운행 중지 + 과태료 통보 (재측정값 기준)
  과태료:    한 운행에서 '최초 1회'만 통보. 이후 다시 확정되면 '재확정'으로 표시만 하고 추가 없음
  판정 3단계: 정상(기준 이내) / 주의(기준 초과 ~ 허용 오차 10% 이내, 또는 축하중 10~11t) / 확정(허용 오차 초과). 확정이면 SCARGO 가 진출 권고를 만들고 관제자가 지시를 보낸다
  차종:      측정한 축수로 분류 - 4축 이상 5종, 3축 4종, 2축 이하 3종 (CLASS_BY_AXLES)
  요금:      남인천TG, 청계TG 모두 개방식(통과 즉시 결제). 차종별 요금표(RATES, 한국도로공사 공시 조회값 2026.10.22)
             폐쇄식 구간(통행권 발권 -> 정산)이 생기면 SECTIONS 에 entry 지점을 적으면 된다. 요금표에 추정값(ESTIMATE)이 있으면 화면에 '추정'으로 표시.
  심야할인:  화물차 심야할인. 요금 구간마다 따로 판정한다.
             개방식: 통과 시각이 23~05시면 50%
             폐쇄식: 두 지점 통과 시각 사이 중 21~06시 비율 70% 이상 50%, 20% 이상 70% 미만 30%
             지점 통과 시각 = 출발 시각 + 소요시간 x (지점 km / 전체 km),  소요시간 = 거리 / 평균속도(60km/h 임시값 = 80분)
             1년 이내 과태료 부과 통행 2회 이상인 차량은 할인 제외 (DB 의 v_toll_night_exclusion)
  벌금액:    도로법 제117조 과태료표를 참고한 시연용 값. 현행 여부 미확인.
"""
import datetime
import random
import time

LEGAL_GROSS_KG = 40000           # 운행제한 총중량 (도로법 시행령 기준 40톤). 제원 총중량이 이보다 크면 40톤을 기준으로 씀
TOLERANCE = 0.10                 # 톨게이트의 측정 오차 허용 (기준의 10% 이내 초과는 과적으로 보지 않고 통과)
CHECKPOINT_TOLERANCE = 0.10      # 검사소의 측정 오차 허용 (톨게이트와 같게 10%). 0 으로 하면 검사소는 기준을 넘는 즉시 과적
AXLE_LIMIT_KG = 10000            # 축하중 제한 (한 축 10톤)
FINE_TIERS = [(5000, 50), (15000, 80), (None, 150)]    # 총중량: (기준 초과량 kg 미만, 벌금 만원). 마지막은 그 이상
AXLE_FINE_TIERS = [(2000, 50), (4000, 80), (None, 150)]  # 축하중: 같은 방식 (2톤 미만 / 4톤 미만 / 그 이상)
STEER_SHARE = 0.8                # 첫 번째 축(조향축)은 다른 축보다 하중이 가벼운 것으로 가정 (정상 분배 비율)
ROUTE_KM = 79.8                  # 1구간 기본 경로 거리 (지도 경로선 79.78km)
AVG_KMH = 60                     # 임시 평균 속도(km/h). 예상 소요시간 = 거리 / 속도 = 80분. 1구간 실제 소요시간을 구하면 EXPECTED_MINUTES 로 대신함
EXPECTED_MINUTES = None          # 숫자를 넣으면 평균속도 계산 대신 이 분(分)을 쓴다 (8장 R1 duration_minutes 와 같은 값)
NIGHT_CLOSED = (21, 6)           # 폐쇄식 할인시간대 21:00 ~ 06:00
NIGHT_OPEN = (23, 5)             # 개방식 할인시간대 23:00 ~ 05:00 (통과 시각 기준 50%)
NIGHT_TIERS = [(0.70, 50), (0.20, 30)]   # 폐쇄식: (할인시간대 이용 비율 이상, 감면 %). 70% 이상 50 / 20% 이상 70% 미만 30 / 20% 미만 0
NIGHT_EXCLUDE_FINES = 2          # 1년 이내 과태료 부과 통행이 이 횟수 이상이면 심야할인 제외
FARE_UNIT = 10                   # 할인 후 요금 반올림 단위(원). 도로공사 실제 단위 확인 전 임시값 (확인되면 100 또는 10)
CLASS_BY_AXLES = lambda axles: 5 if int(axles or 0) >= 4 else 4 if int(axles or 0) == 3 else 3   # 측정 축수로 3/4/5종 분류
CLASS_NAME = {1: '1종', 2: '2종', 3: '3종', 4: '4종', 5: '5종'}

# 차종별 요금표(할인 전). 실제 DB 에서는 toll_fare_rates 테이블이 같은 값을 가진다 (toll_db.ready 가 DB 값으로 덮어씀).
#   OFFICIAL: 한국도로공사 통행요금 조회 공시값(2026.10.22)  /  ESTIMATE: 추정값 (지금은 없음)
_NAM = {1: 900, 2: 900, 3: 900, 4: 1000, 5: 1000}
_CHG = {1: 1000, 2: 1000, 3: 1000, 4: 1100, 5: 1200}
RATES = {**{('NAMINCHEON_TG', c): dict(amount=a, basis='OFFICIAL') for c, a in _NAM.items()},
         **{('CHEONGGYE_TG', c): dict(amount=a, basis='OFFICIAL') for c, a in _CHG.items()}}
SECTION_NAME = {'NAMINCHEON_TG': '남인천TG (개방식)', 'CHEONGGYE_TG': '청계TG (개방식)'}
SECTION_SHORT = {'NAMINCHEON_TG': '남인천', 'CHEONGGYE_TG': '청계'}

# 요금 구간: system OPEN(지점 1곳에서 결제) / CLOSED(entry 지점에서 발권, exit 지점에서 정산)
SECTIONS = [
    dict(code='NAMINCHEON_TG', system='OPEN', exit='TG_NAMINCHEON'),
    dict(code='CHEONGGYE_TG', system='OPEN', exit='TG_CHEONGGYE'),
]

POINTS = [
    dict(key='CK18', name='인천 과적검문소', kind='CHECKPOINT', km=2.5),                                                   # 검문소 38 은 같은 자리(37m)라 알림 대상에서 뺌
    dict(key='TG_NAMINCHEON', name='남인천TG', kind='TOLLGATE', role='OPEN', section='NAMINCHEON_TG', km=18.6),     # 제2경인 본선, 경로선에서 약 105m
    dict(key='TG_CHEONGGYE', name='청계TG', kind='TOLLGATE', role='OPEN', section='CHEONGGYE_TG', km=47.5),         # 수도권제1순환. 좌표·km 는 경로선 기준 추정
]
N = len(POINTS)

# 지점별 '측정값 / 기준 총중량' 대본. 여기에 지점마다 +-1% 의 작은 흔들림이 더해진다 (같은 번호판+시나리오면 항상 같은 값)
SCENARIOS = {
    'A': dict(name='정상 통과', ratios=[0.86, 0.88, 0.87],
              desc='적재량이 기준 이내. 3곳 모두 통과하고 남인천·청계 요금을 결제한다.'),
    'B': dict(name='톨게이트 적발', ratios=[0.95, 1.16, 1.07],
              desc='남인천TG 에서 총중량 과적 판정 → 통과시키고 벌금만 부과. 청계TG 는 측정값이 허용 오차 이내라 통과.'),
    'C': dict(name='검문소 적발', ratios=[1.19, 1.18, 1.18],
              desc='인천 과적검문소 에서 총중량 과적 판정 → 재측정에서도 과적 → 운행중지 + 벌금. 톨게이트에는 가지 못해 통행료 없음.'),
    'D': dict(name='축하중 적발', ratios=[0.90] * 3, axle_ratios=[1.05, 1.07, 1.22], heavy_axle=1,
              desc='총중량은 기준 이내지만 한 축에 하중이 몰림. 청계TG 에서 축하중 10톤 초과 판정 → 통과시키고 벌금만 부과.'),
    'E': dict(name='재측정 통과', ratios=[1.13, 0.91, 0.92], retest_ratios={0: 1.07},
              desc='인천 과적검문소 에서 1차 측정이 과적이지만 재측정에서 기준 이내로 나와 통과. 벌금 없음.'),
}
SCENARIOS['F'] = dict(name='주의 통과', ratios=[1.05, 0.88, 0.87],
                      desc='인천 과적검문소 에서 기준을 약간 넘지만(허용 오차 10% 이내) 주의 판정으로 통과. 벌금 없음. 이후 지점은 정상.')
RETEST_DRIFT = 0.995             # 재측정 값을 따로 정하지 않은 지점은 1차 측정의 99.5% (과적이면 재측정에서도 과적으로 유지)


def limit_kg(gross_weight_kg):
    return min(int(gross_weight_kg), LEGAL_GROSS_KG)


def _tier(over_kg, tiers):
    for bound, manwon in tiers:
        if bound is None or over_kg < bound:
            return manwon
    return tiers[-1][1]


def fine_for(over_kg, axle_over_kg=0):
    """총중량 초과량과 축하중 초과량 중 더 무거운 쪽 단계의 벌금(만원). 초과가 없는 쪽(<=0)은 계산에서 뺌."""
    cands = []
    if over_kg > 0:
        cands.append(_tier(over_kg, FINE_TIERS))
    if axle_over_kg > 0:
        cands.append(_tier(axle_over_kg, AXLE_FINE_TIERS))
    return max(cands) if cands else 0


def axle_loads(gross_kg, n_axles, heavy_idx=None, heavy_kg=None):
    """총중량을 축별 하중으로 나눈다. 첫 축은 가볍게(STEER_SHARE), 나머지는 같게. heavy_idx 가 있으면 그 축을 heavy_kg 로 고정하고 나머지에 분배."""
    n = max(int(n_axles or 2), 1)
    w = [STEER_SHARE] + [1.0] * (n - 1)
    if heavy_idx is not None and 0 <= heavy_idx < n and n > 1:
        rest = [i for i in range(n) if i != heavy_idx]
        rest_total = max(gross_kg - heavy_kg, 0)
        sw = sum(w[i] for i in rest)
        out = [0] * n
        for i in rest:
            out[i] = rest_total * w[i] / sw
        out[heavy_idx] = heavy_kg
        return [int(round(x / 10.0) * 10) for x in out]
    sw = sum(w)
    return [int(round(gross_kg * x / sw / 10.0) * 10) for x in w]


def eligible(scenario, vehicle):
    """이 시나리오 대본을 이 차량에 적용해도 이야기가 맞는지 (예: 정상 차량의 축하중이 처음부터 넘지 않게).  -> (bool, 사유)"""
    gross = vehicle.get('gross_weight_kg')
    axles = int(vehicle.get('axle_count') or 0)
    if not gross or axles < 2:
        return False, '제원(총중량·차축 수) 부족'
    lim = limit_kg(gross)
    if scenario == 'D':
        if axles < 3 or lim < 22000:
            return False, '축하중 시나리오에는 3축 이상, 기준 총중량 22톤 이상 차량이 필요'
        others = [w for i, w in enumerate(axle_loads(lim * 0.9, axles, 1, int(AXLE_LIMIT_KG * 1.07))) if i != 1]
        if max(others) > 9500 or min(others) < 2000:
            return False, '나머지 축의 하중이 비현실적이라 축하중 시나리오에 맞지 않음'
        return True, ''
    if lim / axles > 9000:
        return False, '축당 평균 하중이 커서 총중량 시나리오와 맞지 않음'
    return True, ''


def vehicle_class(axle_count):
    return CLASS_BY_AXLES(axle_count)


def expected_minutes():
    return int(EXPECTED_MINUTES) if EXPECTED_MINUTES else round(ROUTE_KM / AVG_KMH * 60)


def pass_time(depart, km):
    """지점 통과 시각 = 출발 + 소요시간 x (지점 km / 전체 km)."""
    return depart + datetime.timedelta(minutes=expected_minutes() * km / ROUTE_KM)


def _overlap_seconds(a, b, start_h, end_h):
    """[a, b] 중 매일 start_h ~ end_h(다음 날) 시간대에 겹치는 초."""
    tot = 0.0
    d = (a - datetime.timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
    while d <= b:
        ws = d.replace(hour=start_h)
        we = (d + datetime.timedelta(days=1)).replace(hour=end_h) if end_h < start_h else d.replace(hour=end_h)
        lo, hi = max(a, ws), min(b, we)
        if hi > lo:
            tot += (hi - lo).total_seconds()
        d += datetime.timedelta(days=1)
    return tot


def night_ratio_closed(t_in, t_out):
    """폐쇄식: 두 통과 시각 사이 중 21~06시 비율 (0~1)."""
    span = (t_out - t_in).total_seconds()
    if span <= 0:
        return 0.0
    return _overlap_seconds(t_in, t_out, *NIGHT_CLOSED) / span


def night_ratio_open(t):
    """개방식: 통과 시각이 23~05시 안이면 1, 아니면 0."""
    return 1.0 if _overlap_seconds(t, t + datetime.timedelta(seconds=1), *NIGHT_OPEN) > 0 else 0.0


def discount_pct_for(system, ratio):
    if system == 'OPEN':
        return 50 if ratio >= 1.0 else 0
    for bound, pct in NIGHT_TIERS:
        if ratio >= bound - 1e-9:
            return pct
    return 0


def _round_unit(x):
    return int(round(x / FARE_UNIT)) * FARE_UNIT


def section_fare(section, vclass, discount_pct):
    """구간 요금 1건 -> dict(amount 할인 후, base 기준, discount_pct, basis). 요금표에 없는 차종은 5종 값으로 대신함."""
    r = RATES.get((section, vclass)) or RATES[(section, 5)]
    base = int(r['amount'])
    return dict(section=section, section_name=SECTION_NAME.get(section, section), base=base, discount_pct=discount_pct,
                amount=_round_unit(base * (1 - discount_pct / 100.0)), basis=r['basis'], vehicle_class=vclass)


def make_quote(vclass, depart, fined_trips_1y=0):
    """출발 직전에 보여주는 예상 통행료 (요금 구간별 심야할인을 따로 판정). depart 는 datetime, fined_trips_1y 는 최근 1년 과태료 부과 통행 수."""
    pk = {p['key']: p for p in POINTS}
    excluded = fined_trips_1y >= NIGHT_EXCLUDE_FINES
    lines = []
    for sc in SECTIONS:
        system = sc['system']
        t2 = pass_time(depart, pk[sc['exit']]['km'])
        t1 = pass_time(depart, pk[sc['entry']]['km']) if system == 'CLOSED' else t2
        ratio = night_ratio_open(t1) if system == 'OPEN' else night_ratio_closed(t1, t2)
        pct = 0 if excluded else discount_pct_for(system, ratio)
        ln = section_fare(sc['code'], vclass, pct)
        ln.update(system=system, night_ratio=round(ratio, 3), window_start=t1.isoformat(timespec='minutes'), window_end=t2.isoformat(timespec='minutes'))
        lines.append(ln)
    base_total, total = sum(l['base'] for l in lines), sum(l['amount'] for l in lines)
    est = any(l['basis'] == 'ESTIMATE' for l in lines)
    pcts = {l['discount_pct'] for l in lines}
    if excluded:
        disc = f'심야할인 제외: 1년 이내 과태료 {fined_trips_1y}회'
    elif pcts == {0}:
        disc = '할인 없음'
    elif len(pcts) == 1:
        disc = f'심야할인 {pcts.pop()}%'
    else:
        disc = '심야할인 ' + ' · '.join(f"{SECTION_SHORT[l['section']]} {l['discount_pct']}%" for l in lines)
    return dict(vehicle_class=vclass, class_name=CLASS_NAME.get(vclass, f'{vclass}종'), departed_at=depart.isoformat(timespec='minutes'),
                expected_minutes=expected_minutes(), base_total=base_total, expected_total=total, has_estimate=est,
                night_excluded=excluded, fined_trips_1y=fined_trips_1y, lines=lines,
                text=f'예상 통행료 {total:,}원 (기준 {base_total:,}원, {disc})' + (' · 산정 요금 포함' if est else ''))


def new_trip(trip_id, scenario, plate, vehicle, depart=None, fined_trips_1y=0, route_type='BASIC'):
    """depart: 출발 시각(datetime, 서버 PC 의 현지 시각 기준). 없으면 지금."""
    sc = SCENARIOS[scenario]
    depart = depart or datetime.datetime.now()
    lim = limit_kg(vehicle['gross_weight_kg'])
    vclass = vehicle_class(vehicle.get('axle_count'))      # 첫 측정(인천 과적검문소)의 축수 = 제원 축수와 같게 시연
    ratios, axle_ratios = [], []
    retest_ratios = {}
    for i, base in enumerate(sc['ratios']):
        r = random.Random(f'{plate}|{scenario}|{i}')
        ratios.append(base + r.uniform(-0.01, 0.01))
        if sc.get('axle_ratios'):
            axle_ratios.append(sc['axle_ratios'][i] + random.Random(f'{plate}|{scenario}|a{i}').uniform(-0.01, 0.01))
        if i in sc.get('retest_ratios', {}):
            retest_ratios[i] = sc['retest_ratios'][i] + random.Random(f'{plate}|{scenario}|r{i}').uniform(-0.01, 0.01)
    return dict(id=trip_id, scenario=scenario, scenario_name=sc['name'], plate=plate, vehicle=vehicle,
                limit_kg=lim, threshold_kg=round(lim * (1 + TOLERANCE)), ratios=ratios, axle_ratios=axle_ratios, retest_ratios=retest_ratios,
                heavy_axle=sc.get('heavy_axle'), axle_count=int(vehicle.get('axle_count') or 2),
                axle_limit_kg=AXLE_LIMIT_KG, axle_threshold_kg=round(AXLE_LIMIT_KG * (1 + TOLERANCE)), step=0, events=[],
                status='RUNNING', fined=False, fine_manwon=0, halted_at=None, settlement=None, created=time.time(),
                vclass=vclass, quote=make_quote(vclass, depart, fined_trips_1y), payments=[], paid_won=0, ticket=False, route_type=route_type, skipped=[])


def _measure(trip, ratio, axle_ratio, tol):
    """측정 1회분: 총중량·축별 하중·과적 여부. 축별 합계 = 총중량이 되도록 맞춘다 (DB 의 합계 검사 때문)."""
    measured = int(round(trip['limit_kg'] * ratio / 10.0) * 10)
    if axle_ratio is not None:
        axles = axle_loads(measured, trip['axle_count'], trip['heavy_axle'], int(round(AXLE_LIMIT_KG * axle_ratio / 10.0) * 10))
    else:
        axles = axle_loads(measured, trip['axle_count'])
    diff = measured - sum(axles)
    adj = len(axles) - 1 if trip['heavy_axle'] != len(axles) - 1 else 0
    axles[adj] += diff
    gross_thr = round(trip['limit_kg'] * (1 + tol))
    axle_thr = round(AXLE_LIMIT_KG * (1 + tol))
    max_axle = max(axles)
    gross_over, axle_over = measured > gross_thr, max_axle > axle_thr
    reason = '총중량+축하중' if gross_over and axle_over else '총중량' if gross_over else '축하중' if axle_over else ''
    over = gross_over or axle_over
    level = 'CONFIRMED' if over else 'CAUTION' if (measured > trip['limit_kg'] or max_axle > AXLE_LIMIT_KG) else 'NORMAL'     # 정상 / 주의(기준 초과, 허용 오차 이내) / 확정
    return dict(measured_kg=measured, over_kg=measured - trip['limit_kg'], ratio_pct=round(measured / trip['limit_kg'] * 100, 1),
                axle_kg=axles, max_axle_kg=max_axle, axle_over_kg=max_axle - AXLE_LIMIT_KG, threshold_kg=gross_thr,
                axle_threshold_kg=axle_thr, over=over, level=level, gross_over=gross_over, axle_over=axle_over, over_reason=reason)


def finish_if_done(trip):
    if trip['status'] == 'RUNNING' and trip['step'] >= N:
        trip['status'] = 'COMPLETED'
        names = ' + '.join(SECTION_SHORT.get(x['section'], x['section']) + 'TG' for x in trip['payments'])
        trip['settlement'] = dict(state='AMOUNT', amount_won=trip['paid_won'],
                                  text=f"계측 완료 · 통보된 통행료 {trip['paid_won']:,}원 ({names or '통과 톨게이트 없음'}) · 성남IC 진출 예정" + (' · 산정 요금 포함' if trip['quote']['has_estimate'] else ''))


def skip_point(trip, key):
    """경로 이탈로 이 지점을 지나지 않는다 (계측·요금 없음). 다음 처리할 지점일 때만 건너뛴다. -> 건너뛰었으면 True"""
    if trip['status'] != 'RUNNING' or trip['step'] >= N or POINTS[trip['step']]['key'] != key:
        return False
    trip.setdefault('skipped', []).append(key)
    trip['step'] += 1
    finish_if_done(trip)
    return True


def reroute_quote(trip, keys):
    """이탈로 지나지 않는 지점(keys)의 요금 구간을 빼고 예상 통행료를 다시 계산 -> dict(total, skipped_sections, note)"""
    secs = [p['section'] for p in POINTS if p['key'] in keys and p.get('section')]
    lines = [l for l in trip['quote']['lines'] if l['section'] not in secs]
    names = ' · '.join(SECTION_NAME.get(x, x).split(' ')[0] for x in secs)
    return dict(total=sum(l['amount'] for l in lines), skipped_sections=secs, note=(names + ' 미통과') if names else '')


def advance(trip):
    """다음 지점 1곳을 처리해서 이벤트를 돌려준다. 끝났으면 None."""
    if trip['status'] != 'RUNNING' or trip['step'] >= N:
        return None
    i = trip['step']
    p = POINTS[i]
    tol = TOLERANCE if p['kind'] == 'TOLLGATE' else CHECKPOINT_TOLERANCE
    ar = trip['axle_ratios'][i] if trip['axle_ratios'] else None
    first = _measure(trip, trip['ratios'][i], ar, tol)
    final, retest = first, None
    if first['over'] and p['kind'] == 'CHECKPOINT':                 # 검문소: 과적이면 재측정 1회
        rr = trip['retest_ratios'].get(i, trip['ratios'][i] * RETEST_DRIFT)
        retest = _measure(trip, rr, (ar * RETEST_DRIFT) if ar is not None else None, tol)
        final = retest
    is_over = final['over']
    reason = final['over_reason']
    fine = 0
    if not is_over:
        action, label = ('PASS_RETEST', '1차 초과 → 재측정에서 기준 이내/오차 범위 → 통과') if retest else ('PASS', '통과')
    else:
        fine_now = not trip['fined']
        if fine_now:
            fine = fine_for(final['over_kg'] if final['gross_over'] else 0, final['axle_over_kg'] if final['axle_over'] else 0)
        if p['kind'] == 'TOLLGATE':
            action, label = ('PASS_FINE', f'{reason} 초과 확정 - 통과, 과태료 {fine}만원 도로공사 통보') if fine_now else ('PASS_REOFFENSE', f'{reason} 초과 재확정 - 이미 과태료 통보됨, 추가 없음')
        else:
            action, label = ('HALT_FINE', f'{reason} 초과 확정(재측정 후에도 초과) - 검문소 조치: 운행 중지, 과태료 {fine}만원 통보') if fine_now else ('HALT_REOFFENSE', f'{reason} 초과 재확정(재측정 후에도 초과) - 검문소 조치: 운행 중지 (추가 과태료 없음)')
    if fine:
        trip['fined'] = True
        trip['fine_manwon'] += fine
    ev = dict(index=i, key=p['key'], name=p['name'], kind=p['kind'], role=p.get('role'), km=p['km'], limit_kg=trip['limit_kg'], axle_limit_kg=AXLE_LIMIT_KG,
              verdict='OVER' if is_over else 'OK', level=final['level'], first_level=first['level'], action=action, label=label, fine_manwon=fine, time=time.time(),
              retest=retest, first_over=first['over'],
              ocr=dict(plate=trip['plate'], confidence=(trip.get('recognition') or {}).get('confidence')))
    ev.update({k: first[k] for k in ('measured_kg', 'over_kg', 'ratio_pct', 'axle_kg', 'max_axle_kg', 'axle_over_kg', 'axle_threshold_kg', 'threshold_kg')})
    ev['over_reason'] = first['over_reason']
    ev['final_reason'] = final['over_reason']
    # 통과한 톨게이트의 요금: 개방식은 즉시 결제, 폐쇄식은 발권 지점에서 통행권만 받고 정산 지점에서 결제
    ev['payment'], ev['ticket'] = None, False
    if p['kind'] == 'TOLLGATE' and not action.startswith('HALT'):
        if p['role'] == 'CLOSED_ENTRY':
            trip['ticket'], ev['ticket'] = True, True
        elif p.get('section'):
            ev['payment'] = dict(next(l for l in trip['quote']['lines'] if l['section'] == p['section']))    # 출발 전에 계산한 구간별 요금(심야할인 반영)
            ev['payment']['system'] = 'OPEN' if p['role'] == 'OPEN' else 'CLOSED'
            trip['payments'].append(ev['payment'])
            trip['paid_won'] += ev['payment']['amount']
    trip['events'].append(ev)
    trip['step'] = i + 1
    if action.startswith('HALT'):
        trip['status'], trip['halted_at'] = 'HALTED', i
        hold = '검문소 조치: 운행 중지 - 지금까지 통보된 통행료 ' + f"{trip['paid_won']:,}원" if trip['paid_won'] else '검문소 조치: 운행 중지 - 톨게이트 도착 전이라 통행료 발생 없음'
        trip['settlement'] = dict(state='HOLD', amount_won=trip['paid_won'] or None, text=hold)
    else:
        finish_if_done(trip)
    return ev


def public(trip):
    d = {k: trip[k] for k in ('id', 'scenario', 'scenario_name', 'plate', 'limit_kg', 'threshold_kg', 'step', 'events',
                               'status', 'fined', 'fine_manwon', 'halted_at', 'settlement', 'axle_count', 'axle_limit_kg', 'axle_threshold_kg',
                               'quote', 'payments', 'paid_won', 'route_type', 'skipped')}
    d['vehicle_class'] = trip['vclass']
    d['class_name'] = CLASS_NAME.get(trip['vclass'], f"{trip['vclass']}종")
    return d
