"""
SCARGO 관제 시연 서버 (FastAPI) — 톨게이트·검문소 계측 현황(W-12) · 차량 관제(W-13) · 관제 지도(W-14)

[구조] SCARGO 는 관제 + 컨트롤 타워다. 톨게이트·검문소는 도로공사 등 외부 기관 시설이고, 번호판 OCR 은 인천항 출차에서 한 번만 한다.
       계측 결과는 도로공사 연계(시연은 toll_trip.py 의 Mock)로 받아 차량 번호로 매칭해 DB 에 저장·표시한다.
       확정 판정이 나오면 진출 권고를 만들고, 관제자가 [운전자에게 지시 발송] 을 누르면 기록(toll_control_actions)이 남는다.
       차량 위치는 시뮬레이션이며 W-14 지도가 움직이면 위치가 지점에 닿을 때 계측 결과가 W-12/W-13 에 나타난다.
DB 에는 최종합본 DB(7·8·9·10장)가 먼저 적용되어 있어야 한다.

실행:  python toll_web.py            (환경변수는 run_toll.bat 참고)
설정(환경변수):
  GATE_PHOTO_DIR   차량 전면 사진 폴더 (기본: 이 파일 옆의 photos)
  GATE_DB_URL      postgresql://사용자:비밀번호@DB서버:5432/scargo     (없으면 인식만 하고 차량 조회는 '조회 불가')
  GATE_WEB_PORT    포트 (기본 8001)
  GATE_WEB_TOKEN   접속 비밀번호 (지정하면 주소 끝에 ?token=비밀번호). 다른 페이지에 iframe 으로 넣을 때도 같은 주소(?token=..&embed=1)를 쓴다
  TOLL_MIN_CONF    성공으로 보는 최소 신뢰도 % (기본 80)
  GATE_YOLO / GATE_OCR / GATE_DTR_DIR / GATE_OCR_OV   gate_pipeline.py 와 같은 모델 경로 설정 (YOLO 는 .pt)
"""
import os
import re
import sys
import time
import hmac
import random
import socket
import asyncio
import argparse
import base64
import threading
from contextlib import asynccontextmanager
from pathlib import Path

import cv2
import numpy as np
import uvicorn
from fastapi import FastAPI, Request, Query
from fastapi.responses import HTMLResponse, Response, JSONResponse, FileResponse

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
if os.environ.get('GATE_PIPELINE_DIR'):
    sys.path.insert(0, os.environ['GATE_PIPELINE_DIR'])
sys.path.insert(0, os.getcwd())

PHOTO_DIR = Path(os.environ.get('GATE_PHOTO_DIR', str(HERE / 'photos')))
DB_URL = os.environ.get('GATE_DB_URL', '')
TOKEN = os.environ.get('GATE_WEB_TOKEN', '')
EXTS = {'.jpg', '.jpeg', '.png', '.bmp', '.webp'}
CROP_DIR = HERE / 'data' / 'crops'
import datetime as _dt
view_since = {'t': _dt.datetime.now(_dt.timezone.utc)}   # 화면에 보여줄 기록의 시작 시각. 서버를 켠 시각부터 (이전 시연 기록은 DB 에 남지만 화면에는 안 나옴). '초기화' 버튼은 이 시각을 지금으로 바꾼다
REGIONS = "서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주"

LOOKUP_SQL = """
SELECT t.vehicle_no, t.trailer_no, t.is_semi_trailer, t.truck_type, t.max_load_ton, t.status, t.entry_approval, t.vehicle_category,
       c.company_name, c.address AS company_address, c.industry_type, c.representative_name,
       vs.registration_category, vs.usage_purpose, vs.empty_weight_kg, vs.gross_weight_kg, vs.max_payload_kg,
       vs.tire_count, vs.axle_count, vs.chassis_height_m
  FROM trucks t
  JOIN companies c ON c.company_id = t.company_id
  LEFT JOIN vehicle_specs vs ON vs.vehicle_no = t.vehicle_no
 WHERE t.vehicle_no = ANY(%s)
 ORDER BY array_position(%s::text[], t.vehicle_no::text)
 LIMIT 1
"""
LEG_SQL = """
SELECT l.assignment_id, l.leg_type, l.status, fc.center_name AS from_name, tc.center_name AS to_name, l.departed_at
  FROM shipment_legs l
  JOIN logistics_centers fc ON fc.center_id = l.from_center_id
  JOIN logistics_centers tc ON tc.center_id = l.to_center_id
 WHERE l.vehicle_no = %s AND l.status IN ('ASSIGNED', 'IN_TRANSIT')
 ORDER BY l.created_at DESC LIMIT 1
"""


# ------------------------------- 사진 (섞은 주머니: 한 바퀴 다 보여준 뒤에 다시 섞음) -------------------------------
class Photos:
    def __init__(self, folder):
        self.folder = folder
        self.lock = threading.Lock()
        self.files, self.bag, self.last = [], [], None
        self.ids = {}                                  # 사진 id -> 경로 (화면에서 /api/photo/{id} 로 요청)
        self.scan()

    def scan(self):
        self.files = sorted(p for p in self.folder.rglob('*') if p.is_file() and p.suffix.lower() in EXTS) if self.folder.is_dir() else []

    def next(self):
        with self.lock:
            if not self.files:
                return None
            if not self.bag:
                self.bag = list(self.files)
                random.shuffle(self.bag)
                if len(self.bag) > 1 and self.bag[-1] == self.last:      # 한 바퀴 끝에서 같은 사진이 연달아 나오지 않게
                    self.bag[0], self.bag[-1] = self.bag[-1], self.bag[0]
            p = self.bag.pop()
            self.last = p
            return p, len(self.bag)

    def register(self, path):
        pid = format(abs(hash(str(path))) % (16 ** 10), 'x')
        self.ids[pid] = path
        return pid


photos = Photos(PHOTO_DIR)
model_state = dict(state='loading', error='')                # loading / ready / error


def read_image(path):
    try:
        return cv2.imdecode(np.fromfile(str(path), dtype=np.uint8), cv2.IMREAD_COLOR)     # 한글 경로 대응
    except Exception:
        return None


def jpeg(img, max_side=None, quality=85):
    if max_side:
        h, w = img.shape[:2]
        sc = min(1.0, max_side / max(h, w))
        if sc < 1.0:
            img = cv2.resize(img, None, fx=sc, fy=sc, interpolation=cv2.INTER_AREA)
    ok, buf = cv2.imencode('.jpg', img, [cv2.IMWRITE_JPEG_QUALITY, quality])
    return buf.tobytes() if ok else b''


# ------------------------------- DB (읽기 전용) -------------------------------
def plate_candidates(plate):
    """OCR 번호 -> 조회할 번호들. 지역명 접두어가 있으면 뗀 형태도 함께 조회."""
    p = re.sub(r'\s+', '', plate or '')
    c = [p]
    m = re.match(rf'^(?:{REGIONS})(.+)$', p)
    if m:
        c.append(m.group(1))
    return c


def db_connect():
    import psycopg2
    c = psycopg2.connect(DB_URL, connect_timeout=3)
    c.autocommit = True
    return c


def db_rows(sql, args):
    import psycopg2.extras
    c = db_connect()
    try:
        cur = c.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cur.execute(sql, args)
        return [dict(r) for r in cur.fetchall()]
    finally:
        c.close()


def jsonable(row):
    out = {}
    for k, v in row.items():
        if hasattr(v, 'isoformat'):
            v = v.isoformat()
        elif v.__class__.__name__ == 'Decimal':
            v = float(v)
        out[k] = v
    return out


def lookup_vehicle(plate):
    """-> dict(db='ok'|'off'|'error', registered=bool|None, vehicle=dict|None, leg=dict|None, error=str)"""
    if not DB_URL:
        return dict(db='off', registered=None, vehicle=None, leg=None, error='GATE_DB_URL 이 설정되지 않아 차량을 조회할 수 없습니다')
    try:
        cands = plate_candidates(plate)
        rows = db_rows(LOOKUP_SQL, (cands, cands))
    except Exception as e:
        return dict(db='error', registered=None, vehicle=None, leg=None, error=f'{type(e).__name__}: {e}')
    if not rows:
        return dict(db='ok', registered=False, vehicle=None, leg=None, error='')
    v = jsonable(rows[0])
    leg, leg_error = None, ''
    try:
        lr = db_rows(LEG_SQL, (v['vehicle_no'],))
        leg = jsonable(lr[0]) if lr else None
    except Exception as e:                                   # 운송 구간 표가 아직 없는 DB 에서도 차량 정보는 보여준다
        leg_error = f'{type(e).__name__}'
    return dict(db='ok', registered=True, vehicle=v, leg=leg, error='', leg_error=leg_error)


def db_status():
    if not DB_URL:
        return 'off', ''
    try:
        c = db_connect()
        c.close()
        return 'ok', ''
    except Exception as e:
        return 'error', f'{type(e).__name__}: {e}'


# ------------------------------- 출차 OCR + 관제 시연 (기본 경로 1구간: 인천항 출차 → 검문소 18 → 남인천TG → 청계TG → 성남IC 진출) -------------------------------
# 번호판 OCR 은 인천항 출차에서 한 번만 한다. 이후 지점 계측 결과는 도로공사 연계(시연은 toll_trip.py 의 Mock)로 받아 차량 번호로 매칭한다.
trips = {}                                                    # trip_key -> 시연 진행 상태 (메모리). 기록은 지점마다 DB 에 저장
trips_lock = threading.RLock()
MAX_PICK_TRIES = 15
POINT_KM = None


def parse_depart(dep):
    """'now'/빈 값 -> 지금, 'HH:MM' -> 오늘 그 시각 (서버 PC 의 현지 시각). 잘못된 값이면 지금."""
    import datetime
    now = datetime.datetime.now()
    try:
        if dep and dep != 'now':
            h, m = (int(x) for x in str(dep).split(':')[:2])
            return now.replace(hour=h, minute=m, second=0, microsecond=0)
    except Exception:
        pass
    return now


def trip_level(trip):
    """지금까지 계측된 지점들의 최종 판정 중 가장 무거운 것 (NORMAL / CAUTION / CONFIRMED)"""
    rank = {'NORMAL': 0, 'CAUTION': 1, 'CONFIRMED': 2}
    lv = [e.get('level', 'NORMAL') for e in trip['events']]
    return max(lv, key=lambda x: rank[x]) if lv else 'NORMAL'


def sync_trip(trip):
    """시뮬레이션 위치(watch)에 맞춰 지점을 처리하고 DB 에 저장한다.
       - 위치가 지점 km 에 닿으면 그 지점의 계측 결과(Mock)를 받아 저장
       - 경로 이탈로 지나지 않는 톨게이트는 건너뛰고 예상 통행료를 다시 계산
       - 새 경로 이벤트와 현재 위치 저장, 정차(운행 중지) 처리"""
    import toll_trip as T
    import toll_db
    w = trip['watch']
    km = w.last['km'] if w.last else 0.0
    while trip['status'] == 'RUNNING' and trip['step'] < T.N:
        p = T.POINTS[trip['step']]
        if p['key'] in w.skipped_keys:
            T.skip_point(trip, p['key'])
            continue
        if p['km'] > km + 1e-6:
            break
        ev = T.advance(trip)
        if ev is None:
            break
        toll_db.save_event(DB_URL, trip, ev)
    if w.skipped_keys and trip.get('rerouted') != tuple(w.skipped_keys):
        rr = T.reroute_quote(trip, w.skipped_keys)
        toll_db.save_reroute(DB_URL, trip['trip_key'], rr['total'], rr['note'], rr['skipped_sections'])
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
        toll_db.save_progress(DB_URL, trip['trip_key'], new, saved + 1, pos)
        trip['saved_events'] = saved + len(new)


def start_trip(scenario, dep=None, route='BASIC'):
    import toll_recognize
    import toll_trip
    import route_watch
    if scenario not in toll_trip.SCENARIOS:
        return dict(error='알 수 없는 시나리오입니다.'), 400
    if route not in ('BASIC', 'DETOUR'):
        route = 'BASIC'
    if not DB_URL:
        return dict(error='구간 시연에는 DB 연결(GATE_DB_URL)이 필요합니다. 차량 제원(총중량)을 DB 에서 읽습니다.'), 400
    import toll_db
    ok_db, why_db = toll_db.ready(DB_URL)
    if not ok_db:
        return dict(error='DB 에 시연용 구조가 없습니다 (최종합본 DB 7·8·9·10장 실행 필요): ' + why_db), 500
    skipped = []
    issue_saved = False
    for _ in range(MAX_PICK_TRIES):
        pick = photos.next()
        if pick is None:
            return dict(error=f'사진이 없습니다. 폴더를 확인하세요: {PHOTO_DIR}'), 404
        path, _remaining = pick
        img = read_image(path)
        if img is None:
            skipped.append((path.name, '사진을 열 수 없음'))
            continue
        rec = toll_recognize.recognize(img)
        crop = rec.pop('crop')
        if rec['status'] != 'SUCCESS':
            skipped.append((path.name, '출차 OCR 인식 실패'))
            if not issue_saved:
                issue_saved = True
                try:
                    toll_db.save_departure(DB_URL, rec.get('plate'), rec.get('confidence'), 'OCR_FAILED')
                except Exception as e:
                    print('[DB] 출차 인식 실패 기록 저장 실패:', e)
            continue
        look = lookup_vehicle(rec['plate'])
        if look['db'] == 'error':
            return dict(error='DB 조회에 실패했습니다: ' + look['error']), 500
        v = look['vehicle']
        if not look['registered']:
            skipped.append((path.name, '미등록 차량'))
            if not issue_saved:
                issue_saved = True
                try:
                    toll_db.save_departure(DB_URL, rec.get('plate'), rec.get('confidence'), 'UNREGISTERED')
                except Exception as e:
                    print('[DB] 미등록 기록 저장 실패:', e)
            continue
        with trips_lock:
            if any(t['status'] == 'RUNNING' and t['plate'] == v['vehicle_no'] for t in trips.values()):
                skipped.append((path.name, '이미 운행 중인 차량'))
                continue
        if not v.get('gross_weight_kg') and v.get('empty_weight_kg') and v.get('max_payload_kg'):
            v['gross_weight_kg'] = int(v['empty_weight_kg']) + int(v['max_payload_kg'])      # 총중량이 비어 있으면 공차+최대적재량으로 대신함
        if not v.get('gross_weight_kg'):
            skipped.append((f"{path.name} -> DB 번호 {v.get('vehicle_no')}", '제원(총중량) 없음'))
            continue
        ok_sc, why = toll_trip.eligible(scenario, v)
        if not ok_sc:
            skipped.append((f"{path.name} -> {v.get('vehicle_no')}", why))
            continue
        tid = format(random.getrandbits(40), 'x')
        try:
            fined = toll_db.fined_trips_1y(DB_URL, v['vehicle_no'])         # 최근 1년 과태료 통보 통행 수 (2회 이상이면 심야할인 제외)
        except Exception:
            fined = 0
        trip = toll_trip.new_trip(tid, scenario, v['vehicle_no'], v, parse_depart(dep), fined, route)
        trip['recognition'] = rec
        trip['vehicle_info'] = v
        CROP_DIR.mkdir(parents=True, exist_ok=True)
        if crop is not None and getattr(crop, 'size', 0):
            cv2.imwrite(str(CROP_DIR / f'{tid}.jpg'), crop)
            trip['crop_url'] = f'/api/crop/{tid}.jpg'
        trip['watch'] = route_watch.Watch(v['vehicle_no'], route, 'NORMAL')
        try:
            trip['departure_id'] = toll_db.save_departure(DB_URL, rec.get('plate'), rec.get('confidence'), 'DEPARTED', v['vehicle_no'], trip.get('crop_url'))
            w = trip['watch']
            first_km = toll_trip.POINTS[0]['km']
            for _i in range(20):                                           # 출차 후 첫 계측 지점(검문소 18)까지 이동
                w.step(1)
                if w.last and w.last['km'] >= first_km:
                    break
            sync_trip(trip)
        except Exception as e:
            return dict(error=f'DB 저장에 실패했습니다: {type(e).__name__}: {e}'), 500
        with trips_lock:
            trips[trip['trip_key']] = trip
            while len(trips) > 30:
                trips.pop(next(iter(trips)))
        return dict(trip_id=trip['trip_key'], plate=trip['plate'], scenario=scenario, route=route), 200
    return dict(error='시연에 쓸 수 있는 사진을 찾지 못했습니다 (인식 실패/미등록/제원 없음). 건너뛴 사진: ' +
                      ', '.join(f'{n}({r})' for n, r in skipped[:6])), 404


def find_trip(key):
    return trips.get(int(key))


def finished(trip):
    w = trip['watch']
    return w.stopped or w.at_end()


def move_trip(trip, n=1):
    """W-14 재생: 위치를 n 칸 옮기고 지점 처리 + 저장"""
    if finished(trip):
        return
    trip['watch'].step(n)
    _sync_safe(trip)


def next_point(trip):
    """시연 '다음': 다음 지점(없으면 도착)까지 이동한다."""
    import toll_trip as T
    if finished(trip):
        return
    w = trip['watch']
    target = T.POINTS[trip['step']]['km'] if trip['status'] == 'RUNNING' and trip['step'] < T.N else 1e9
    for _ in range(400):
        w.step(1)
        if w.stopped or w.at_end() or (w.last and w.last['km'] >= target):
            break
    _sync_safe(trip)


def _sync_safe(trip):
    try:
        sync_trip(trip)
    except Exception as e:                                    # 저장에 실패해도 시뮬레이션은 계속, 화면에는 경고만
        trip['db_error'] = f'{type(e).__name__}: {e}'
        print('[DB] 저장 실패:', trip['db_error'])


# ------------------------------- 웹 -------------------------------
# ---- 서버 쪽 시뮬레이션 시계: 재생 중이면 운행 중인 모든 차량이 같이 움직인다 (브라우저 페이지를 옮겨도 멈추지 않는다)
sim = dict(playing=False, tick=1.2, pause_on_dev=True, notice=None, seen=set())   # 1.2초/칸: 3대 시연이 약 2분. pause_on_dev: 경로 이탈이 처음 감지되면 재생을 멈추고 안내를 띄운다


def sim_tick():
    with trips_lock:
        live = [t for t in trips.values() if not finished(t)]
        for t in live:
            move_trip(t, 1)
        if sim['pause_on_dev']:
            for t in live:
                k = t['trip_key']
                if t['watch'].status == 'DEVIATING' and k not in sim['seen']:
                    sim['seen'].add(k)
                    sim['playing'] = False
                    sim['notice'] = dict(trip_id=k, plate=t['plate'], text=f"{t['plate']} 경로 이탈 감지 — 기본 경로에서 벗어났습니다. 통행료가 다시 계산되고 대안 경로 안내 권고가 만들어집니다. (설명 후 '재개' 를 누르세요)")
                    break
        if not [t for t in trips.values() if not finished(t)]:
            sim['playing'] = False


async def sim_loop():
    while True:
        await asyncio.sleep(sim['tick'])
        if sim['playing']:
            try:
                await asyncio.to_thread(sim_tick)
            except Exception as e:
                print('[시뮬레이션 오류]', e)


def sim_state():
    return dict(playing=sim['playing'], tick_ms=int(sim['tick'] * 1000), pause_on_dev=sim['pause_on_dev'], notice=sim['notice'])


# ---- 자동 운행 (26.10.29): 시연 버튼 없이 차량이 간격을 두고 자동으로 출차 · 진행한다
#  · 구간 관제 화면이 열려 있는 동안만(화면이 20초 안에 서버를 불렀을 때) 새 차량을 출차시킨다 → 아무도 안 볼 때 DB 기록이 쌓이지 않는다
#  · 출차 시각은 택배 이동 · 물류센터 관제와 같은 '공용 하루 시계'. 화면(iframe)이 /api/clock 으로 알려 주고, 없으면 PC 시각
#  · 3대 중 3번째마다 우회(이탈) 차량 1대. 이탈이 감지돼도 멈추지 않고 안내만 표시한다 (pause_on_dev 끔)
#  · 끄려면 환경변수 TOLL_AUTO=0 (또는 POST /api/auto?on=0)
AUTO = dict(on=os.environ.get('TOLL_AUTO', '1') != '0', target=3, gap=(6.0, 14.0), next=0.0, n=0, err=None, seen=0.0, clock=None)


def auto_dep():
    """출차 시각 문자열: 공용 시계가 있으면 그 시각 'HH:MM', 없으면 'now'"""
    c = AUTO['clock']
    if not c:
        return 'now'
    try:
        tod = (float(c['tod']) + (time.time() - c['at']) * float(c['rate'])) % 24
        return '%02d:%02d' % (int(tod), int((tod % 1) * 60))
    except Exception:
        return 'now'


async def auto_loop():
    while True:
        await asyncio.sleep(2)
        try:
            if not AUTO['on'] or not DB_URL or model_state['state'] != 'ready':
                continue
            if time.time() - AUTO['seen'] > 20:             # 화면이 열려 있지 않으면 새 차량을 내보내지 않는다
                continue
            sim['playing'] = True                           # 일시정지 버튼이 없다: 항상 재생
            sim['pause_on_dev'] = False
            sim['notice'] = None
            if time.time() < AUTO['next']:
                continue
            with trips_lock:
                live = sum(1 for t in trips.values() if not finished(t))
            if live >= AUTO['target']:
                continue
            AUTO['n'] += 1
            detour = AUTO['n'] % 3 == 0
            data, code = await asyncio.to_thread(start_trip, 'F' if detour else 'A', auto_dep(), 'DETOUR' if detour else 'BASIC')
            if code == 200:
                AUTO['err'] = None
                AUTO['next'] = time.time() + random.uniform(*AUTO['gap'])
            else:
                AUTO['err'] = data.get('error') if isinstance(data, dict) else str(data)
                AUTO['next'] = time.time() + 20             # 실패하면 잠시 쉬었다가 다시 (사진 없음 · DB 오류 등)
                print('[자동 운행] 출차 실패:', AUTO['err'])
        except Exception as e:
            AUTO['err'] = f'{type(e).__name__}: {e}'
            AUTO['next'] = time.time() + 20
            print('[자동 운행 오류]', AUTO['err'])


@asynccontextmanager
async def lifespan(_app):
    asyncio.create_task(sim_loop())
    asyncio.create_task(auto_loop())
    async def warm():                                          # 모델을 미리 불러 첫 클릭이 느리지 않게
        try:
            import toll_recognize
            await asyncio.to_thread(toll_recognize.load)
            model_state['state'] = 'ready'
        except Exception as e:
            model_state.update(state='error', error=f'{type(e).__name__}: {e}')
            print('[모델 오류]', model_state['error'])
    asyncio.create_task(warm())
    yield


app = FastAPI(lifespan=lifespan)


def allowed(request: Request):
    if not TOKEN:
        return True
    t = request.query_params.get('token') or request.headers.get('x-toll-token') or request.cookies.get('toll_token') or ''
    return hmac.compare_digest(t.encode(), TOKEN.encode())


@app.middleware('http')
async def auth(request: Request, call_next):
    if not allowed(request):
        return Response('접속 비밀번호가 필요합니다. 주소 끝에 ?token=비밀번호 를 붙이세요.', status_code=401, media_type='text/plain; charset=utf-8')
    resp = await call_next(request)
    if TOKEN and request.query_params.get('token'):
        resp.set_cookie('toll_token', TOKEN, httponly=True, samesite='lax')
    return resp


@app.get('/', response_class=HTMLResponse)
async def index():
    return (HERE / 'map.html').read_text(encoding='utf-8')            # 첫 화면은 관제 지도


@app.get('/gate', response_class=HTMLResponse)
async def gate_page():
    return (HERE / 'gate.html').read_text(encoding='utf-8')            # 계측 이력


@app.get('/checkpoint', response_class=HTMLResponse)
async def checkpoint_page():
    return (HERE / 'checkpoint.html').read_text(encoding='utf-8')


@app.get('/map', response_class=HTMLResponse)
async def map_page():
    return (HERE / 'map.html').read_text(encoding='utf-8')


@app.get('/leaflet.js')
async def leaflet_js():
    return Response((HERE / 'leaflet.js').read_text(encoding='utf-8'), media_type='application/javascript; charset=utf-8')


@app.get('/leaflet.css')
async def leaflet_css():
    return Response((HERE / 'leaflet.css').read_text(encoding='utf-8'), media_type='text/css; charset=utf-8')


@app.get('/common.js')
async def common_js():
    return Response((HERE / 'common.js').read_text(encoding='utf-8'), media_type='application/javascript; charset=utf-8')


@app.get('/api/crop/{name}')
async def api_crop(name: str):
    p = CROP_DIR / Path(name).name
    if not p.exists():
        return Response(status_code=404)
    return FileResponse(p, media_type='image/jpeg')


def range_bounds(frm, to):
    """조회 기간 -> (since, until). frm/to 는 YYYY-MM-DD (서버 시각 기준 날짜). 둘 다 없으면 오늘.
    오늘이 시작일이면 서버를 켠 시각/초기화 시각 이후 기록만 본다."""
    day0 = _dt.datetime.now().astimezone().replace(hour=0, minute=0, second=0, microsecond=0)

    def parse(x):
        try:
            return _dt.datetime.strptime(x, '%Y-%m-%d').astimezone() if x else None
        except ValueError:
            return None
    f, t = parse(frm), parse(to)
    if frm == 'all':
        f = None
    elif f is None:
        f = day0
    since = f
    until = (t + _dt.timedelta(days=1)) if t else None
    if since is not None and since >= day0 and view_since['t']:
        since = max(since, view_since['t'])
    return since, until


@app.get('/api/db/overview')
async def api_db_overview(frm: str = Query('', alias='from'), to: str = ''):
    """W-12 · W-13 · W-14 가 같이 쓰는 조회: 통행 목록(차량·위치·지점별 판정·예상 통행료·권고·조치) + 출차 확인 필요 건"""
    import toll_db
    since, until = range_bounds(frm, to)
    try:
        tr = await asyncio.to_thread(toll_db.trips_data, DB_URL, since, until)
        iss = await asyncio.to_thread(toll_db.departure_issues, DB_URL, since, until)
    except Exception as e:
        return JSONResponse(dict(error=f'{type(e).__name__}: {e}'), status_code=500)
    live = {k for k, t in trips.items() if not finished(t)}
    # 서버 재시작 전에 출발해 도착·정차 기록이 없는 차량은 시연 서버가 움직일 수 없으므로 목록에서 뺀다 (DB 기록은 그대로 남음)
    tr = [t for t in tr if t['trip_id'] in trips or (t.get('position') or {}).get('route_status') in ('ARRIVED', 'STOPPED')]
    for t in tr:
        t['live'] = t['trip_id'] in live                      # 시연 서버가 움직일 수 있는 차량인지 (서버 재시작 전 차량은 False)
    return JSONResponse(_jsonify(dict(trips=tr, issues=iss, sim=sim_state())))


@app.post('/api/db/reset')
async def api_db_reset():
    import datetime
    view_since['t'] = datetime.datetime.now(datetime.timezone.utc)
    with trips_lock:
        trips.clear()
    sim['playing'] = False
    sim['notice'] = None
    sim['seen'].clear()
    return dict(ok=True)


def next_one(key):
    with trips_lock:
        trip = find_trip(key)
        if trip is None:
            return dict(error='이 서버를 다시 시작하기 전의 차량이라 시연으로 진행할 수 없습니다.'), 404
        if finished(trip):
            return dict(error='이미 도착했거나 정차한 차량입니다.'), 409
        next_point(trip)
        return dict(ok=True, db_error=trip.get('db_error')), 200


def next_all():
    n = 0
    with trips_lock:
        for t in list(trips.values()):
            if not finished(t):
                next_point(t)
                n += 1
    return dict(advanced=n)


@app.post('/api/db/trip/{key}/next')
async def api_db_trip_next(key: int):
    data, code = await asyncio.to_thread(next_one, key)
    return JSONResponse(data, status_code=code)


@app.post('/api/db/next_all')
async def api_db_next_all():
    return await asyncio.to_thread(next_all)


# ---- W-14 관제 지도: 차량별 위치 시뮬레이션 (판정은 route_watch.py). 위치가 지점에 닿으면 그 지점의 계측 결과가 W-12/W-13 에도 나타난다
def watch_view(trip):
    st = trip['watch'].state()
    st.update(trip_id=trip['trip_key'], plate=trip['plate'], level=trip_level(trip), db_error=trip.get('db_error'))
    return st


@app.get('/api/map/data')
async def api_map_data():
    import route_watch
    d = dict(route_watch.data())
    d.pop('_cum', None)
    return d


@app.post('/api/clock')
async def api_clock(request: Request):
    """구간 관제 화면이 공용 하루 시계(택배 이동 · 물류센터 관제와 같은 시각)를 알려 준다: {tod: 시(0~24 소수), rate: 초당 시간}"""
    AUTO['seen'] = time.time()
    try:
        b = await request.json()
        AUTO['clock'] = dict(tod=float(b['tod']), rate=float(b.get('rate', 1 / 3600)), at=time.time())
    except Exception:
        pass
    if not AUTO['on']:
        st = 'off'
    elif not DB_URL:
        st = 'no_db'
    elif model_state['state'] == 'error':
        st = 'model_error'
    elif model_state['state'] != 'ready':
        st = 'model_loading'
    else:
        st = 'ok'
    with trips_lock:
        live = sum(1 for t in trips.values() if not finished(t))
    return dict(ok=True, on=AUTO['on'], state=st, live=live, dep=auto_dep(), err=AUTO['err'] or (model_state.get('error') if st == 'model_error' else None))


@app.get('/api/auto')
async def api_auto_get():
    return dict(on=AUTO['on'], target=AUTO['target'], err=AUTO['err'], dep=auto_dep())


@app.post('/api/auto')
async def api_auto_set(on: int = 1):
    AUTO['on'] = bool(on)
    return dict(on=AUTO['on'])


@app.get('/api/sim')
async def api_sim():
    AUTO['seen'] = time.time()
    return sim_state()


@app.post('/api/sim/play')
async def api_sim_play():
    sim['playing'] = True
    sim['notice'] = None
    return sim_state()


@app.post('/api/sim/pause')
async def api_sim_pause():
    sim['playing'] = False
    return sim_state()


@app.post('/api/sim/pause_on_deviate')
async def api_sim_pod(on: int = 1):
    sim['pause_on_dev'] = bool(on)
    return sim_state()


@app.post('/api/sim/speed')
async def api_sim_speed(ms: int = 450):
    sim['tick'] = max(100, min(int(ms), 3000)) / 1000
    return sim_state()


@app.get('/api/watch/all')
async def api_watch_all():
    """지도용: 운행 중(시뮬레이션 서버가 아는) 모든 차량의 위치·상태·알림·이벤트"""
    AUTO['seen'] = time.time()                       # 화면이 열려 있다는 신호 (자동 운행은 화면이 열려 있을 때만 새 차량 출차)
    with trips_lock:
        return dict(vehicles=[watch_view(t) for t in trips.values()], sim=sim_state())


@app.get('/api/watch/{key}')
async def api_watch_state(key: int):
    with trips_lock:
        trip = find_trip(key)
        if trip is None:
            return JSONResponse(dict(error='시연 서버가 움직일 수 있는 차량이 아닙니다 (서버를 다시 시작하기 전의 차량이거나 초기화됨).'), status_code=404)
        return watch_view(trip)


@app.post('/api/watch/{key}/step')
async def api_watch_step(key: int, n: int = 1):
    def run():
        with trips_lock:
            trip = find_trip(key)
            if trip is None:
                return dict(error='움직일 수 있는 차량이 아닙니다.'), 404
            move_trip(trip, max(1, min(int(n), 50)))
            return watch_view(trip), 200
    data, code = await asyncio.to_thread(run)
    return JSONResponse(data, status_code=code)


@app.post('/api/watch/{key}/skip')
async def api_watch_skip(key: int):
    def run():
        with trips_lock:
            trip = find_trip(key)
            if trip is None:
                return dict(error='움직일 수 있는 차량이 아닙니다.'), 404
            if not finished(trip):
                trip['watch'].goto_before_detour()
                _sync_safe(trip)
            return watch_view(trip), 200
    data, code = await asyncio.to_thread(run)
    return JSONResponse(data, status_code=code)


@app.post('/api/trip/{key}/action')
async def api_trip_action(key: int, request: Request):
    """관제자가 운전자에게 지시(안내)를 보낸다. 권고 문구는 서버가 DB 판정으로 다시 만들어 쓴다 (화면에서 보낸 문구는 쓰지 않음)."""
    import toll_db
    try:
        body = await request.json()
    except Exception:
        body = {}
    typ = str(body.get('type', ''))

    def run():
        t = next((x for x in toll_db.trips_data(DB_URL, None) if x['trip_id'] == key), None)
        if t is None:
            return dict(error='통행을 찾을 수 없습니다.'), 404
        if typ == 'CHECKPOINT_NOTICE':
            with trips_lock:
                trip = find_trip(key)
                al = next((a for a in (trip['watch'].state()['alerts'] if trip else []) if a['no'] == body.get('no')), None)
            if al is None:
                return dict(error='지금 안내할 검문소 경고가 없습니다.'), 409
            msg = f"{al['name']} 전방 {al['dist_km']:.1f}km입니다. 경로를 이탈한 상태로 과적 검문소에 접근하고 있습니다. 적재 상태를 확인하고 기본 경로 복귀를 검토하세요."
            aid = toll_db.issue_action(DB_URL, key, typ, msg, f"CP{al['no']}", None)
            return dict(ok=True, action_id=aid), 200
        rec = next((r for r in reversed(t['recs']) if r['type'] == typ), None)
        if rec is None:
            return dict(error='이 차량에는 해당 권고가 없습니다.'), 409
        aid = toll_db.issue_action(DB_URL, key, typ, rec['message'], rec['point'], rec['check_id'])
        return dict(ok=True, action_id=aid), 200
    try:
        data, code = await asyncio.to_thread(run)
    except ValueError as e:
        return JSONResponse(dict(error=str(e)), status_code=409)
    except Exception as e:
        return JSONResponse(dict(error=f'{type(e).__name__}: {e}'), status_code=500)
    return JSONResponse(data, status_code=code)


@app.get('/api/photofile/{name}')
async def api_photofile(name: str):
    p = PHOTO_DIR / Path(name).name
    if not p.exists() or p.suffix.lower() not in EXTS:
        return Response(status_code=404)
    return FileResponse(p)


@app.get('/api/status')
async def status():
    dbs, dberr = await asyncio.to_thread(db_status)
    return dict(model=model_state['state'], model_error=model_state['error'], db=dbs, db_error=dberr,
                photos=len(photos.files), photo_dir=str(PHOTO_DIR), min_conf=float(os.environ.get('TOLL_MIN_CONF', '80')))


def _jsonify(o):
    import toll_db
    if isinstance(o, dict):
        return {k: _jsonify(v) for k, v in o.items()}
    if isinstance(o, (list, tuple)):
        return [_jsonify(v) for v in o]
    if hasattr(o, 'isoformat'):
        return o.isoformat()
    if o.__class__.__name__ == 'Decimal':
        return float(o)
    return o


@app.post('/api/trip/start')
async def api_trip_start(request: Request):
    """인천항 출차: 사진 → 번호판 OCR(여기서 한 번만) → 등록 차량이면 통행 시작"""
    if model_state['state'] == 'error':
        return JSONResponse(dict(error='모델을 불러오지 못했습니다: ' + model_state['error']), status_code=500)
    if model_state['state'] == 'loading':
        return JSONResponse(dict(error='모델을 불러오는 중입니다. 잠시 후 다시 눌러 주세요.'), status_code=503)
    try:
        body = await request.json()
    except Exception:
        body = {}
    try:
        data, code = await asyncio.to_thread(start_trip, str(body.get('scenario', 'A')), body.get('dep'), str(body.get('route', 'BASIC')))
    except Exception as e:
        return JSONResponse(dict(error=f'{type(e).__name__}: {e}'), status_code=500)
    return JSONResponse(data, status_code=code)


@app.get('/api/photo/{pid}')
async def api_photo(pid: str):
    p = photos.ids.get(pid)
    img = read_image(p) if p else None
    if img is None:
        return Response(status_code=404)
    return Response(jpeg(img, 1280, 85), media_type='image/jpeg', headers={'Cache-Control': 'no-store'})


@app.get('/api/vehicles/{plate}')
async def api_vehicle(plate: str):
    return await asyncio.to_thread(lookup_vehicle, plate)


def lan_ips():
    ips = set()
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ips.add(info[4][0])
    except OSError:
        pass
    return sorted(i for i in ips if not i.startswith('127.'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--host', default='0.0.0.0')
    ap.add_argument('--port', type=int, default=int(os.environ.get('GATE_WEB_PORT', '8001')))
    a = ap.parse_args()
    print(f"사진 폴더: {PHOTO_DIR}  ({len(photos.files)}장)")
    if not photos.files:
        print('[주의] 사진이 없습니다. GATE_PHOTO_DIR 로 사진 폴더를 지정하세요.')
    if not DB_URL:
        print('[알림] GATE_DB_URL 이 없어 인식만 하고 차량 조회는 하지 않습니다.')
    print(f"\n웹 화면: http://localhost:{a.port}" + (f"/?token={TOKEN}" if TOKEN else ''))
    for ip in lan_ips():
        print(f"다른 노트북에서: http://{ip}:{a.port}" + (f"/?token={TOKEN}" if TOKEN else ''))
    if not TOKEN:
        print('[주의] GATE_WEB_TOKEN 이 없어 같은 네트워크의 누구나 볼 수 있습니다.')
    uvicorn.run(app, host=a.host, port=a.port, log_level='warning')


if __name__ == '__main__':
    main()
