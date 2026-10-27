"""
경로 이탈 감시 시연 (화면·DB 와 무관한 순수 계산).  모든 기준값은 아래 상수만 고치면 바뀐다.

[시연용 규칙]
  이탈:  기본 경로선에서 DEVIATE_M(100m) 이상 벗어난 상태가 DEVIATE_SAMPLES(2)번 연속 관측되면 '경로 이탈'
  복귀:  이탈 중 경로선 RETURN_M(50m) 이내로 들어오면 '복귀' (이탈보다 좁게 잡아 경계에서 경고가 깜빡이지 않게 함)
  검문소: 이탈 중인 차량이 알림 대상 검문소(role=ALERT) 전방 WARN_KM(5km) 안에 들어오면 경고, 통과하면 해제
          (기본 경로를 정상 주행 중에는 검문소 경고를 내지 않는다)
  통행료: 이탈 구간이 지나는 톨게이트는 '미통과'로 표시 (예상 통행료가 달라짐)
시연 이동: 기본 경로 -> (이탈 구간: A 경로의 안산 구간) -> 기본 경로 복귀 -> 도착. 관측 간격 STEP_KM.
"""
import json
import math
import time
from pathlib import Path

DEVIATE_M = 100          # 이탈 거리(m)
DEVIATE_SAMPLES = 2      # 연속 관측 횟수
RETURN_M = 50            # 복귀 거리(m)
WARN_KM = 5.0            # 검문소 전방 경고 거리(km)
PASS_M = 500             # 검문소에 이 거리 안까지 접근하면 '통과 중'으로 보고, 다시 멀어지면 통과 처리 (관측 간격의 절반보다 크게)
STEP_KM = 0.8            # 시연 관측 간격(km)
DATA_FILE = Path(__file__).resolve().parent / 'map_data.json'

_data = None


def data():
    global _data
    if _data is None:
        _data = json.loads(DATA_FILE.read_text(encoding='utf-8'))
        r = _data['route']
        _data['_cum'] = _cum(r)
    return _data


def hav_m(a, b):
    p = math.radians
    dl, dn = p(b[0] - a[0]), p(b[1] - a[1])
    x = math.sin(dl / 2) ** 2 + math.cos(p(a[0])) * math.cos(p(b[0])) * math.sin(dn / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(x))


def _cum(pts):
    c = [0.0]
    for i in range(1, len(pts)):
        c.append(c[-1] + hav_m(pts[i - 1], pts[i]))
    return c


def locate(p):
    """점 p=(lat,lng) -> (경로선까지 거리 m, 경로선 위 진행 km). 경로선은 기본 경로."""
    d = data()
    pts, cum = d['route'], d['_cum']
    k = math.cos(math.radians(p[0]))
    best = (1e12, 0.0)
    for i in range(len(pts) - 1):
        ax, ay = (pts[i][1] - p[1]) * k * 111320, (pts[i][0] - p[0]) * 110540
        bx, by = (pts[i + 1][1] - p[1]) * k * 111320, (pts[i + 1][0] - p[0]) * 110540
        dx, dy = bx - ax, by - ay
        L = dx * dx + dy * dy
        t = 0 if L == 0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / L))
        dist = math.hypot(ax + t * dx, ay + t * dy)
        if dist < best[0]:
            best = (dist, (cum[i] + t * (cum[i + 1] - cum[i])) / 1000)
    return best


def _resample(pts, step_m):
    """폴리라인을 일정 간격(step_m)의 점으로 다시 뽑는다."""
    out, carry = [pts[0]], 0.0
    for i in range(1, len(pts)):
        a, b = pts[i - 1], pts[i]
        seg = hav_m(a, b)
        pos = step_m - carry
        while pos <= seg:
            t = pos / seg
            out.append([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t])
            pos += step_m
        carry = seg - (pos - step_m)
    if out[-1] != pts[-1]:
        out.append(pts[-1])
    return out


def sim_path(route_type='DETOUR'):
    """시연 이동 경로. BASIC: 기본 경로 그대로. DETOUR: 기본 경로(출발 ~ 이탈 지점) + 이탈 구간 + 기본 경로(복귀 지점 ~ 도착)"""
    d = data()
    cum = d['_cum']
    route, detour = d['route'], d['detour']
    if route_type == 'BASIC':
        return _resample(route, STEP_KM * 1000)
    li = min(range(len(route)), key=lambda i: abs(cum[i] / 1000 - d['detour_leave_km']))
    ri = min(range(len(route)), key=lambda i: abs(cum[i] / 1000 - d['detour_rejoin_km']))
    full = route[:li + 1] + detour + route[ri:]
    return _resample(full, STEP_KM * 1000)


class Watch:
    def __init__(self, plate='시연 차량', route_type='DETOUR', risk='NORMAL'):
        self.plate = plate
        self.route_type = route_type
        self.risk = risk                # 이 차량의 계측 판정 (NORMAL/CAUTION/CONFIRMED). 검문소 전방 경고 문구가 달라진다
        self.reset()

    def reset(self):
        self.path = sim_path(self.route_type)
        self.stopped = False            # 검문소 조치 등으로 정차
        self.i = -1
        self.status = 'IDLE'            # IDLE / NORMAL / DEVIATING
        self.off = 0                    # 연속 이탈 관측 수
        self.events = []
        self.dev_start_km = None
        self.dev_travel_km = 0.0
        self.alerts = {}                # 검문소 no -> dict(state WARN/NEAR/PASSED, dist_km, min_m)
        self.skipped = []               # 이탈 중 지나친(미통과) 톨게이트 이름
        self.skipped_keys = []          # 같은 톨게이트의 지점 코드 (TG_NAMINCHEON ...)
        self.last = None

    # ---- 한 칸 진행
    def step(self, n=1):
        for _ in range(n):
            if self.stopped or self.i + 1 >= len(self.path):
                break
            self._advance()
        return self.state()

    def stop(self, text=None):
        """정차 (검문소 조치 등). 더 이상 움직이지 않는다."""
        if not self.stopped:
            self.stopped = True
            if text:
                self._log('HALT', text, 'warn')

    def at_end(self):
        return self.i + 1 >= len(self.path)

    def goto_before_detour(self, margin=2):
        """이탈이 시작되기 margin 칸 전까지 한 번에 이동 (처음부터 걸어가지 않고 이탈 시연으로 바로 가기)"""
        first_off = next((i for i, p in enumerate(self.path) if locate(p)[0] >= DEVIATE_M), len(self.path))
        while self.i + 1 < first_off - margin:
            self._advance()
        return self.state()

    def _log(self, kind, text, level='info'):
        self.events.append(dict(t=time.time(), kind=kind, text=text, level=level, km=round(self.last['km'], 1) if self.last else None))

    def _advance(self):
        d = data()
        self.i += 1
        p = self.path[self.i]
        dist, km = locate(p)
        if self.status == 'DEVIATING' and self.i > 0:
            self.dev_travel_km += hav_m(self.path[self.i - 1], p) / 1000
        self.last = dict(lat=p[0], lng=p[1], dist_m=round(dist), km=round(km, 1), index=self.i, total=len(self.path))
        if self.status == 'IDLE':
            self.status = 'NORMAL'
            self._log('START', f'{self.plate} 인천항 출발')
        if dist >= DEVIATE_M:
            self.off += 1
        elif dist <= RETURN_M:
            self.off = 0
        if self.status == 'NORMAL' and self.off >= DEVIATE_SAMPLES:
            self.status = 'DEVIATING'
            self.dev_start_km = self._leave_km()
            self.dev_travel_km = 0.0
            tg = [t for t in d['tolls'] if d['detour_leave_km'] <= t['km'] <= d['detour_rejoin_km']]
            self.skipped = [t['name'] for t in tg]
            self.skipped_keys = [t['key'] for t in tg]
            self._log('DEVIATE', f'경로 이탈 감지: 기본 경로 {self.dev_start_km}km 부근에서 벗어남 (현재 {round(dist)}m, {DEVIATE_SAMPLES}회 연속 관측). 우회 경로 주행 중 · 이탈 사유는 알 수 없음', 'warn')
            if tg:
                self._log('TOLL', f"{' · '.join(self.skipped)} 를 지나지 않는 경로라 해당 통행료는 발생하지 않습니다 (예상 통행료 다시 계산)", 'info')
        elif self.status == 'DEVIATING' and dist <= RETURN_M:
            self.status = 'NORMAL'
            self.off = 0
            self._log('RETURN', f'기본 경로 복귀 (경로선 {RETURN_M}m 이내). 우회 주행 약 {self.dev_travel_km:.1f}km', 'ok')
            self.alerts = {k: v for k, v in self.alerts.items() if v['state'] == 'PASSED'}
        if self.status == 'DEVIATING':
            self._update_alerts(p)
        if self.i + 1 >= len(self.path):
            self._log('ARRIVE', '곤지암 허브 도착', 'ok')

    def _leave_km(self):
        d = data()
        return d['detour_leave_km']

    def _update_alerts(self, p):
        d = data()
        for c in d['checkpoints']:
            if c['role'] != 'ALERT':
                continue
            dist_m = hav_m(p, (c['lat'], c['lng']))
            a = self.alerts.get(c['no'])
            if a and a['state'] == 'PASSED':
                continue
            if a is None and dist_m <= WARN_KM * 1000:
                a = self.alerts[c['no']] = dict(state='WARN', min_m=dist_m)
                if self.risk == 'NORMAL':
                    self._log('CHECKPOINT_WARN', f"{c['name']} 전방 {dist_m / 1000:.1f}km — 참고: 이 차량은 계측 결과 정상입니다 (경로 이탈 중)", 'info')
                else:
                    self._log('CHECKPOINT_WARN', f"{c['name']} 전방 {dist_m / 1000:.1f}km — 과적 {'확정' if self.risk == 'CONFIRMED' else '주의'} 판정 이력이 있는 차량이 경로 이탈 중 접근합니다", 'warn')
            if a is None:
                continue
            a['dist_km'] = round(dist_m / 1000, 2)
            a['min_m'] = min(a['min_m'], dist_m)
            if dist_m <= PASS_M:
                a['state'] = 'NEAR'
            elif a['state'] == 'NEAR' and dist_m > a['min_m'] + 150:
                a['state'] = 'PASSED'
                self._log('CHECKPOINT_PASS', f"{c['name']} 통과 — 경고 해제", 'info')
            elif a['state'] == 'WARN' and dist_m > WARN_KM * 1000 + 500:   # 접근하지 않고 멀어짐
                del self.alerts[c['no']]

    def state(self):
        d = data()
        act = []
        for c in d['checkpoints']:
            a = self.alerts.get(c['no'])
            if a and a['state'] != 'PASSED' and self.status == 'DEVIATING':
                act.append(dict(no=c['no'], name=c['name'], dist_km=a.get('dist_km'), state=a['state'], risk=self.risk))
        return dict(plate=self.plate, status='STOPPED' if self.stopped else self.status, vehicle=self.last, alerts=act, events=self.events[-60:], n_events=len(self.events),
                    skipped=self.skipped, skipped_keys=self.skipped_keys, route_type=self.route_type, risk=self.risk,
                    done=self.i + 1 >= len(self.path), rules=dict(deviate_m=DEVIATE_M, deviate_samples=DEVIATE_SAMPLES, return_m=RETURN_M, warn_km=WARN_KM))
