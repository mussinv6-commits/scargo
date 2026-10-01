# -*- coding: utf-8 -*-
"""
gate_api.py
====================================================
gate_watch_service.py(폴더 상시 감시)를 HTTP로 트리거하는 방식으로 바꾼 FastAPI
버전. 2026-09-30 사용자 확인 사항:
- 폴더를 계속 자동으로 감시하는 대신, HTTP 요청(POST /scan)이 올 때마다 딱 한
  대(무작위로 고른 사진 한 장)만 인식+DB조회+게이트로그 저장을 하고 결과를
  돌려줌 - 발표할 때 버튼을 누르는 순간에 맞춰 "트럭 한 대가 게이트에 들어오는"
  장면을 보여주기 위함(gate_watch_service.py의 자동 폴링과는 트리거 방식만
  다르고, 인식 로직 자체는 동일한 코드를 재사용함). Vue 화면의 "게이트인" 버튼을
  누르면 이 /scan을 호출하는 구조를 그대로 쓰면 됨.
- 등록차량 매칭(번호판+차종 둘 다 일치해야 통과)은 scargo가 쓰는 PostgreSQL에
  FastAPI가 직접 접속해서 조회함(Spring Boot API를 거치지 않음 - 사용자 확인).
  ** 설계 변경(2026-09-30, scargo 엔티티 확인 후): 처음엔 이 매칭용으로 새
  테이블(gate_registered_vehicles)을 만들 계획이었는데, scargo 백엔드의
  Truck.java/schema.sql을 직접 확인해보니 scargo가 이미 실제로 쓰는
  "trucks" 테이블이 존재함(vehicle_no PK + truck_type 컬럼 - 정확히 "번호판
  +차종" 매칭에 필요한 그 컬럼들). 심지어 GateLog.java의 actualVehicleNo
  필드가 "매칭된 차량 번호판 (trucks FK)"라고 이미 주석까지 달려 있어서,
  백엔드 설계 자체가 이미 이 매칭 관계를 염두에 두고 있었음. 그래서 새 테이블을
  만들지 않고 기존 trucks 테이블을 그대로 조회함 - 중복 테이블을 만들 필요가
  없어졌고, gate_logs 저장 시에도 GateLogCreateRequest에 이미 있는
  actualVehicleNo 필드에 매칭된 번호판을 그대로 채워 넣음(스키마 변경 전혀
  없음). **
  단, trucks.company_id가 NOT NULL FK라서 더미 트럭을 넣으려면 companies
  테이블에 있는 실제 업체 하나가 필요함 - schema.sql에 이미 15개 업체가
  더미로 들어있으므로 그중 첫 번째(company_id 최소값)를 그대로 씀.
  gate_logs 저장은 기존에 이미 검증된 scargo 백엔드 API(POST
  /api/v1/gate-logs)를 그대로 씀 - 그 테이블의 실제 DB 컬럼명을 추측해서
  직접 SQL로 쓰는 위험을 피하기 위함(trucks 조회만 직접 SQL, gate_logs
  쓰기는 기존 API - 사용자가 확인한 "FastAPI가 PostgreSQL 직접 접속"은
  등록차량 조회 용도였음).
- 사용자가 "더미데이터는 없어"라고 확인함 - 그래서 서버 시작 시 trucks
  테이블이 비어 있으면 테스트 사진 중 실제로 존재하는 번호판 몇 개를
  등록차량으로 미리 넣어둠(DUMMY_VEHICLES 참고) - 이 번호판이 찍힌 사진을
  감시 폴더에 넣으면 "등록차량 일치 -> 차단기 오픈", 그 외 사진은 "미등록 ->
  차단기 안 열림"을 보여줄 수 있음. trucks에 이미 데이터가 있으면(count>0)
  절대 건드리지 않음(기존 실제 데이터를 보존).

*** gate_watch_service.py(자동 폴링), gate_live_demo.py(--limit/--delay 재생)는
그대로 남겨둠 - 셋 다 트리거 방식만 다르고 인식 로직(batch_test_images.py)은
전부 동일하게 재사용함. ***

- 2026-09-30 추가 (스키마 재설계 대응): 사용자가 게이트 마스터 테이블(gates)을
  추가한 schema.sql을 새로 실행하면서 gate_logs.gate_name/gate_type 컬럼이
  사라지고 gate_id(FK)로 바뀜 - scargo 백엔드(GateLog 엔티티/서비스/DTO)도
  이에 맞춰 gate_id 참조 구조로 같이 업데이트함(GateLogCreateRequest가 이제
  gateCode를 받음). 이 파일도 그에 맞춰 payload의 "gateName"/"gateType"을
  "gateId": gate_id 하나로 교체함(위 DEFAULT_GATE_CODE 참고).

실행 전 준비 (필수):
    1. 환경변수 설정 (PowerShell 예시)
       $env:GATE_API_ID = "admin"
       $env:GATE_API_PW = "admin1234"
       $env:PGPASSWORD = "<scargo Postgres 비밀번호>"
       (PGHOST/PGPORT/PGDATABASE/PGUSER는 기본값 localhost/5432/scargo/postgres -
       다르면 같은 이름의 환경변수로 덮어쓰기)
    2. PostgreSQL에 scargo 데이터베이스가 이미 schema.sql로 구성돼 있어야 함
       (trucks/companies 테이블이 이미 존재해야 함 - 새로 만들지 않음)
    3. scargo 백엔드(Spring Boot)가 http://localhost:8080 에서 구동 중이어야 함
       (게이트로그 저장은 여전히 이 API를 통해서 함)

실행:
    pip install fastapi uvicorn[standard] pg8000 aiofiles
    (aiofiles는 정적 이미지 서빙(/images) 기능에 필요함. pg8000은 2026-09-30
    변경 - 한국어 Windows에서 psycopg2가 내는 UnicodeDecodeError를 피하기 위해
    순수 파이썬 드라이버로 교체함. psycopg2-binary는 더 이상 필요 없음)
    uvicorn gate_api:app --host 0.0.0.0 --port 8001

사용 (발표 중 버튼/curl로 트리거):
    curl -X POST http://localhost:8001/scan
    -> 감시 폴더(기본: exe/스크립트 옆의 "게이트_수신함")에서 무작위로 사진 한 장을
       골라 인식하고, 등록차량(trucks) DB와 대조한 뒤, 게이트로그를 저장하고 결과를
       JSON으로 돌려줌. 폴더가 비어 있으면 204(처리할 사진 없음)를 돌려줌.
       Vue에서는 "게이트인" 버튼 onClick에 이 요청을 걸면 됨.
    응답의 imageUrl(예: /images/processed/xxx.jpg)을 http://localhost:8001 뒤에
    그대로 붙이면 실제로 스캔된 사진을 프론트에서 <img>로 바로 보여줄 수 있음
    (2026-09-30 추가 - /images 경로를 감시 폴더 전체에 대해 정적으로 서빙함).
"""
import json
import logging
import os
import random
import re
import shutil
import sys
import time
from pathlib import Path
from typing import Optional

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import cv2
import requests
import torch
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel


def _exe_dir():
    """plate_detector_gui.py / batch_test_images.py / gate_watch_service.py와
    동일한 이유의 함수(2026-09-30) - PyInstaller로 나중에 exe화할 경우를 대비해
    똑같이 맞춰둠. 지금은 uvicorn으로 스크립트로 실행하므로 __file__ 기준으로
    동작(동작 변화 없음)."""
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


APP_DIR = _exe_dir()
sys.path.insert(0, str(APP_DIR))

from batch_test_images import (  # noqa: E402  (경로 설정 뒤에 import 필요)
    CRNN_CHARS_PATH,
    CRNN_CONF_THRESHOLD,
    CRNN_MODEL_PATH,
    YOLO_CONF,
    YOLO_IOU,
    _is_plausible_plate_text,
    _is_valid_plate_format,
    clean_plate_text,
    enhance_low_conf_crop,
    ocr_plate,
    padded_crop,
    pick_best_model,
)
from plate_ocr_crnn_attn import CRNNRecognizer, guess_line_count

DEFAULT_WATCH_DIR = APP_DIR / "게이트_수신함"
DEFAULT_SCARGO_API_BASE = "http://localhost:8080"
# 2026-09-30 변경: gate_logs가 gate_name/gate_type을 직접 저장하던 구조에서,
# 게이트 마스터 테이블(gates)을 gate_id(FK)로 참조하는 구조로 schema.sql이
# 바뀜에 따라 scargo POST /api/v1/gate-logs가 이제 gateCode(게이트 마스터의
# gate_code)를 받도록 바뀜. DB가 자동 생성하는 숫자 gate_id 대신, schema.sql이
# 심어둔 4개 게이트 코드(Gate-ABC-01/Gate-I-01/Gate-H-01/Gate-DEFG-01) 중
# 하나를 그대로 씀 - 스키마가 재실행돼도(SERIAL이 바뀌어도) 코드는 고정이라 안전함.
DEFAULT_GATE_CODE = "Gate-ABC-01"
# 2026-10-01 추가: 이 서버(gate_api.py)가 사진을 서빙하는 주소 - gate_logs.front_image_url에
# 절대주소로 남겨서 다른 화면(계중대 등)에서도 <img src>로 바로 쓸 수 있게 함
GATE_PUBLIC_BASE = os.environ.get("GATE_PUBLIC_BASE", "http://localhost:8001")
DEFAULT_VEHICLE_TYPE = "TRUCK"  # gate_logs.vehicle_type에 쓰는 값 (trucks.truck_type과 매칭 비교에도 씀)
IMAGE_SUFFIXES = (".jpg", ".jpeg", ".png")

# 2026-09-30: "더미데이터는 없어" 확인 - 실제 테스트 사진 중 존재하는 번호판을
# 몇 개 등록차량(trucks)으로 미리 넣어둠. 이 번호판이 찍힌 사진(같은 파일명)을
# 감시 폴더에 넣으면 매칭 성공(차단기 오픈) 케이스를 보여줄 수 있고, 그 외
# 사진은 매칭 실패(미등록) 케이스를 보여줄 수 있음 - 데모에서 두 경우 다
# 자연스럽게 나오게 하려는 목적. trucks 테이블이 이미 비어있지 않으면 절대
# 건드리지 않음(서버 시작 시 최초 1회만, count==0일 때만 씨드).
DUMMY_VEHICLES = [
    ("부산98사2482", "TRUCK"),
    ("경기86자5261", "TRUCK"),
    ("006너9792", "TRUCK"),
    ("경북98사5843", "TRUCK"),
    ("인천99바8989", "TRUCK"),
]

logger = logging.getLogger("gate_api")
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)

app = FastAPI(title="Gate Recognition API")
app.add_middleware(
    CORSMiddleware,
    # scargo_vue_0922 프론트엔드(localhost:5173)에서 바로 fetch할 수 있게 허용.
    # 발표용 데모라 와일드카드 대신 알려진 개발 서버 주소만 명시함.
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# 2026-09-30: 실시간으로 인식한 "실제 사진"을 Vue 화면에서 바로 보여주기 위해
# 감시 폴더(게이트_수신함, 그 밑의 processed/failed 포함)를 그대로 정적 파일로
# 서빙함 - /scan 응답의 imageUrl이 이 경로를 가리킴(예: /images/processed/xxx.jpg).
DEFAULT_WATCH_DIR.mkdir(parents=True, exist_ok=True)
(DEFAULT_WATCH_DIR / "processed").mkdir(parents=True, exist_ok=True)
(DEFAULT_WATCH_DIR / "failed").mkdir(parents=True, exist_ok=True)
app.mount("/images", StaticFiles(directory=str(DEFAULT_WATCH_DIR)), name="images")

# 아래 전역 상태는 FastAPI startup 이벤트에서 한 번만 초기화됨(요청마다 모델을
# 새로 로드하면 몇 초씩 걸려 데모에 못 씀).
_state = {
    "yolo": None,
    "crnn": None,
    "ocr_reader": None,
    "imgsz": None,
    "session": None,
    "db_conn": None,
}


def get_credentials():
    user_id = os.environ.get("GATE_API_ID")
    user_pw = os.environ.get("GATE_API_PW")
    if not user_id or not user_pw:
        raise RuntimeError(
            "환경변수 GATE_API_ID / GATE_API_PW 가 설정 안 됨 - "
            '예(PowerShell): $env:GATE_API_ID = "admin"'
        )
    return user_id, user_pw


def login(session, api_base, user_id, user_pw):
    resp = session.post(
        f"{api_base}/api/accounts/login",
        json={"userId": user_id, "userPw": user_pw},
        timeout=5,
    )
    logger.info(f"[로그인 시도] status={resp.status_code}, 응답 내용={resp.text}")
    logger.info(f"[확보된 세션 쿠키 목록] {session.cookies.get_dict()}")

    if resp.status_code != 200:
        raise RuntimeError(f"scargo 로그인 실패 (status={resp.status_code}): {resp.text}")
    
    # requests.Session이 자동으로 쿠키를 저장하지 못한 경우 대비 수동 주입
    if not session.cookies.get_dict():
        set_cookie = resp.headers.get("Set-Cookie")
        if set_cookie:
            session.headers.update({"Cookie": set_cookie})
            logger.warning("[경고] 세션 쿠키가 자동으로 저장되지 않아 헤더에 수동 주입했습니다.")

    logger.info(f"scargo 로그인 성공: {user_id}")


def get_db_connection():
    """DB 연결 - 표준 libpq 스타일 환경변수(PGHOST/PGPORT/PGDATABASE/PGUSER/
    PGPASSWORD)를 그대로 씀. PGPASSWORD 외에는 흔한 로컬 개발 기본값을
    깔아둠(다르면 환경변수로 덮어쓰기).
    2026-09-30 변경: 원래 psycopg2를 썼는데, 한국어(CP949) Windows 로케일에서
    psycopg2의 C 확장(libpq)이 UnicodeDecodeError('utf-8' codec can't decode
    byte 0xb8...)를 내는 문제가 실제로 발생함(사용자 확인) - 콘솔 코드페이지를
    UTF-8로 바꾸거나(chcp 65001) PYTHONUTF8=1을 줘도 해결 안 됨(C 확장 내부
    동작이라 파이썬/콘솔 쪽 설정으로는 못 고침). 참고로 scargo 백엔드(Spring
    Boot/JDBC)는 완전히 동일한 계정/DB로 정상 접속되는 것으로 이미 확인했으므로
    PostgreSQL 서버나 계정 자체 문제는 아님. 그래서 C 확장이 전혀 없는 순수
    파이썬 드라이버 pg8000으로 교체해서 이 클래스의 버그를 원천적으로 피함
    (DBAPI2 호환이라 아래 cur.execute(...) 등 나머지 코드는 그대로 재사용됨)."""
    import pg8000.dbapi as pg8000

    return pg8000.connect(
        host=os.environ.get("PGHOST", "localhost"),
        port=int(os.environ.get("PGPORT", "5432")),
        database=os.environ.get("PGDATABASE", "scargo"),
        user=os.environ.get("PGUSER", "postgres"),
        password=os.environ.get("PGPASSWORD", "1234")
    )


def ensure_dummy_trucks(conn):
    """2026-09-30 설계 변경: scargo가 이미 실제로 쓰는 "trucks" 테이블(Truck.java
    엔티티, vehicle_no PK + truck_type 컬럼)을 그대로 매칭 대상으로 씀 - 새
    테이블을 만들지 않음(위 모듈 docstring 참고). "더미데이터는 없어" 확인에
    따라 trucks가 비어 있을 때만(count==0) 테스트 사진 중 실제로 존재하는
    번호판 몇 개를 씨드해둠. company_id는 NOT NULL FK라서 companies 테이블에
    이미 있는 실제 업체(schema.sql 더미 데이터) 중 하나를 그대로 씀."""
    cur = conn.cursor()
    try:
        cur.execute("SELECT COUNT(*) FROM trucks")
        (count,) = cur.fetchone()
        if count > 0:
            logger.info(f"trucks 테이블에 이미 데이터 {count}건 있음 - 씨드 건너뜀(기존 데이터 보존)")
            conn.commit()
            return

        cur.execute("SELECT company_id FROM companies ORDER BY company_id LIMIT 1")
        row = cur.fetchone()
        if row is None:
            logger.warning("companies 테이블이 비어 있어 트럭 씨드를 건너뜀 (trucks.company_id는 NOT NULL FK)")
            conn.commit()
            return
        company_id = row[0]

        logger.info(f"trucks 테이블이 비어 있음 - 더미 등록차량 {len(DUMMY_VEHICLES)}건 씨드 (company_id={company_id})")
        cur.executemany(
            "INSERT INTO trucks (vehicle_no, company_id, truck_type) "
            "VALUES (%s, %s, %s) ON CONFLICT (vehicle_no) DO NOTHING",
            [(plate, company_id, vtype) for plate, vtype in DUMMY_VEHICLES],
        )
        conn.commit()
    finally:
        cur.close()


def lookup_vehicle(conn, plate_no: str, truck_type: str = None):
    """번호판(vehicle_no)만으로 매칭. scargo의 실제 trucks 테이블을 조회함
    (Truck.java 엔티티와 동일 테이블 - 새 테이블을 만들지 않음, 위 모듈
    docstring 참고).
    2026-09-30 변경: 원래는 "번호판+차종 둘 다 일치해야 매칭"이었는데, 사용자가
    실제 trucks 데이터를 직접 조회해서 확인해보니 truck_type 컬럼 값이
    "트랙터"/"카고트럭"/"윙바디"/"냉동탑차" 같은 실제 세부 차종이었음. 반면
    이 코드가 비교하던 값은 DEFAULT_VEHICLE_TYPE = "TRUCK"이라는 고정 문자열
    뿐이고(AI가 사진에서 세부 차종을 분류하는 기능 자체가 없음), 그래서 번호판이
    아무리 정확히 일치해도 차종 비교에서 항상 실패해 등록차량도 전부 미등록
    처리되는 버그였음. 차종 비교를 완전히 제거하고 번호판만으로 매칭하도록
    수정함(truck_type 인자는 호출부 호환을 위해 남겨두되 더 이상 안 씀)."""
    if not plate_no:
        return None
    cur = conn.cursor()
    try:
        cur.execute(
            "SELECT vehicle_no, truck_type, company_id, status, planned_route FROM trucks "
            "WHERE vehicle_no = %s",
            (plate_no,),
        )
        row = cur.fetchone()
    finally:
        cur.close()
    if row is None:
        return None
    return {
        "vehicleNo": row[0],
        "truckType": row[1],
        "companyId": row[2],
        "status": row[3],
        "plannedRoute": row[4],
    }


def lookup_gate_id(conn, gate_code: str):
    """활성 gates 레코드의 실제 gate_id를 gate_code로 조회한다."""
    cur = conn.cursor()
    try:
        cur.execute(
            "SELECT gate_id FROM gates WHERE gate_code = %s AND is_active = TRUE",
            (gate_code,),
        )
        row = cur.fetchone()
    finally:
        cur.close()
    return row[0] if row else None


def recognize_one(image_path: Path):
    """gate_watch_service.py의 recognize_one과 완전히 동일 - 검출/인식 경로를
    이중 관리하지 않기 위해 그대로 재사용."""
    img = cv2.imread(str(image_path))
    if img is None:
        return None

    model = _state["yolo"]
    crnn = _state["crnn"]
    ocr_reader = _state["ocr_reader"]
    imgsz = _state["imgsz"]

    result = model.predict(source=img, conf=YOLO_CONF, iou=YOLO_IOU, imgsz=imgsz, verbose=False)[0]
    boxes_xyxy = result.boxes.xyxy.cpu().numpy()
    boxes_conf = result.boxes.conf.cpu().numpy()
    if len(boxes_xyxy) == 0:
        return {"pred": "", "ocr_conf": None, "engine": "", "det_conf": None,
                "line_count": None, "crnn_raw_text": "", "crnn_raw_conf": None}

    best_i = int(boxes_conf.argmax())
    x1, y1, x2, y2 = [float(v) for v in boxes_xyxy[best_i]]
    det_conf = float(boxes_conf[best_i])
    h_img, w_img = img.shape[:2]
    x1, y1 = max(0.0, x1), max(0.0, y1)
    x2, y2 = min(float(w_img), x2), min(float(h_img), y2)

    low_conf_box = det_conf < 0.7
    pad_ratio = 0.30 if low_conf_box else 0.18
    pad_bottom_ratio = 0.65 if low_conf_box else pad_ratio
    box_ratio = (x2 - x1) / (y2 - y1) if (y2 - y1) > 0 else None
    crop = padded_crop(img, x1, y1, x2, y2, pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio)
    if low_conf_box:
        crop = enhance_low_conf_crop(crop)

    line_count = guess_line_count(crop, box_ratio=box_ratio) if crnn is not None else None

    text, ocr_conf, engine, crnn_raw_text, crnn_raw_conf = ocr_plate(
        crnn, ocr_reader, crop, CRNN_CONF_THRESHOLD, box_ratio=box_ratio,
        pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio,
    )
    if text:
        text = clean_plate_text(text)
    if text and engine != "crnn" and not _is_valid_plate_format(text):
        text = ""
    if text and engine != "crnn":
        if len(text) == 8 and not text.startswith("006"):
            text = ""
        elif len(text) == 7:
            text = ""
    if text and not _is_plausible_plate_text(text):
        text = ""

    pred = text.replace(" ", "") if text else ""
    return {"pred": pred, "ocr_conf": ocr_conf, "engine": engine, "det_conf": det_conf,
            "line_count": line_count, "crnn_raw_text": crnn_raw_text or "",
            "crnn_raw_conf": crnn_raw_conf}


def _move_safely(path: Path, dest_dir: Path):
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest = dest_dir / path.name
    if dest.exists():
        dest = dest_dir / f"{path.stem}_{int(time.time() * 1000)}{path.suffix}"
    shutil.move(str(path), str(dest))
    return dest


def _pick_random_stable_file(watch_dir: Path) -> Optional[Path]:
    """2026-09-30 사용자 확인: 폴더에 여러 장이 있어도 무작위로 한 장만 골라서
    "트럭이 한 대씩 게이트에 들어오는" 느낌을 냄. 카메라가 아직 쓰고 있는
    파일은 파일 크기 안정성 체크로 건너뜀(gate_watch_service.py와 동일 로직)."""
    candidates = [
        p for p in watch_dir.iterdir()
        if p.is_file() and p.suffix.lower() in IMAGE_SUFFIXES
    ]
    random.shuffle(candidates)
    for path in candidates:
        try:
            size1 = path.stat().st_size
            time.sleep(0.3)
            size2 = path.stat().st_size
        except FileNotFoundError:
            continue
        if size1 == size2 and size2 != 0:
            return path
    return None


def _recycle_processed(watch_dir: Path) -> int:
    """2026-10-01 추가: 감시 폴더가 비면 processed 에 있던 사진을 다시 감시 폴더로 되돌림.
    시연 중에 사진이 다 떨어져 "처리할 사진이 없습니다"가 뜨는 것을 막기 위함.
    - _move_safely 가 붙인 "_1790...(13자리 타임스탬프)" 중복본은 원본 이름으로 되돌리고,
      같은 이름이 이미 있으면 그 중복본은 processed 에 그대로 둔다.
    - 환경변수 GATE_RECYCLE=0 이면 이 기능을 끈다."""
    if os.environ.get("GATE_RECYCLE", "1") == "0":
        return 0
    processed_dir = watch_dir / "processed"
    if not processed_dir.exists():
        return 0
    moved = 0
    for p in sorted(processed_dir.iterdir()):
        if not (p.is_file() and p.suffix.lower() in IMAGE_SUFFIXES):
            continue
        stem = re.sub(r"_\d{13}$", "", p.stem)
        dest = watch_dir / f"{stem}{p.suffix}"
        if dest.exists():
            continue
        try:
            shutil.move(str(p), str(dest))
            moved += 1
        except OSError:
            logger.exception(f"[{p.name}] 감시 폴더로 되돌리기 실패")
    if moved:
        logger.info(f"감시 폴더가 비어서 processed 의 사진 {moved}장을 다시 넣음")
    return moved


class ScanResult(BaseModel):
    fileName: str
    recognizedPlate: Optional[str] = None
    engine: Optional[str] = None
    detConfidence: Optional[float] = None
    ocrConfidence: Optional[float] = None
    lineCount: Optional[int] = None
    matchResult: str  # "AUTHORIZED" | "DENIED" | "NOT_RECOGNIZED"
    matchedVehicle: Optional[dict] = None
    gateOpen: bool
    gateLogId: Optional[int] = None
    imageUrl: Optional[str] = None  # 2026-09-30: 실제 스캔된 사진 (/images/processed/... - 프론트에서 그대로 <img src>)


@app.on_event("startup")
def startup():
    logger.info("모델 로딩 중...")
    from ultralytics import YOLO
    import easyocr

    model_path, imgsz = pick_best_model()
    _state["yolo"] = YOLO(model_path)
    _state["imgsz"] = imgsz

    if CRNN_MODEL_PATH.exists() and CRNN_CHARS_PATH.exists():
        crnn = CRNNRecognizer()
        crnn.load(str(CRNN_MODEL_PATH), str(CRNN_CHARS_PATH))
        _state["crnn"] = crnn
        logger.info(f"CRNN 로드 완료: {CRNN_MODEL_PATH.name}")
    else:
        logger.warning("CRNN 가중치를 못 찾음 - EasyOCR만 사용")

    use_gpu = torch.cuda.is_available()
    _state["ocr_reader"] = easyocr.Reader(["ko", "en"], gpu=use_gpu)
    logger.info(f"EasyOCR GPU 사용: {use_gpu}")

    user_id, user_pw = get_credentials()
    session = requests.Session()
    login(session, DEFAULT_SCARGO_API_BASE, user_id, user_pw)
    _state["session"] = session

    conn = get_db_connection()
    ensure_dummy_trucks(conn)
    _state["db_conn"] = conn

    DEFAULT_WATCH_DIR.mkdir(parents=True, exist_ok=True)
    logger.info(f"감시 폴더: {DEFAULT_WATCH_DIR}")
    logger.info("준비 완료 - POST /scan 으로 트리거하세요")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/scan", response_model=ScanResult)
def scan():
    watch_dir = DEFAULT_WATCH_DIR
    processed_dir = watch_dir / "processed"
    failed_dir = watch_dir / "failed"

    path = _pick_random_stable_file(watch_dir)
    if path is None and _recycle_processed(watch_dir) > 0:
        path = _pick_random_stable_file(watch_dir)
    if path is None:
        raise HTTPException(status_code=204, detail="처리할 사진이 감시 폴더에 없음")

    try:
        result = recognize_one(path)
    except Exception:
        logger.exception(f"[{path.name}] 인식 중 예외 발생")
        _move_safely(path, failed_dir)
        raise HTTPException(status_code=500, detail=f"인식 중 오류: {path.name}")

    if result is None:
        logger.warning(f"[{path.name}] 이미지 파일을 못 읽음")
        _move_safely(path, failed_dir)
        raise HTTPException(status_code=500, detail=f"이미지 파일을 못 읽음: {path.name}")

    pred = result["pred"]
    recognition_status = "SUCCESS" if pred else "FAILED"

    matched_vehicle = None
    if pred:
        matched_vehicle = lookup_vehicle(_state["db_conn"], pred, DEFAULT_VEHICLE_TYPE)

    if not pred:
        match_result = "NOT_RECOGNIZED"
    elif matched_vehicle is not None:
        match_result = "AUTHORIZED"
    else:
        match_result = "DENIED"
    gate_open = match_result == "AUTHORIZED"

    # 2026-10-01 변경: 게이트로그를 저장하기 "전에" 사진을 processed로 옮겨서, 저장되는 기록에
    # 실제 사진 주소(frontImageUrl)를 같이 남김 - 계중대 계량 화면 등 다른 화면에서도 게이트에서
    # 찍힌 사진을 다시 볼 수 있게 하기 위함. (파일명이 충돌하면 _move_safely가 타임스탬프를
    # 붙이므로 반드시 이동 후 실제 파일명(final_path.name) 기준으로 URL을 만듦)
    final_path = _move_safely(path, processed_dir)
    image_url = f"/images/processed/{final_path.name}"

    plate_confidence = round(result["ocr_conf"] * 100, 2) if result["ocr_conf"] is not None else None
    # 등록 차량은 planned_route.destination_gate를 사용하고, 미등록 차량은 기존 ABC야드 출입구를 사용
    gate_code = DEFAULT_GATE_CODE
    if matched_vehicle is not None:
        planned_route = matched_vehicle.get("plannedRoute")
        # pg8000 반환 형태에 따라 JSON 문자열이면 dict로 변환
        if isinstance(planned_route, str):
            try:
                planned_route = json.loads(planned_route)
            except json.JSONDecodeError:
                planned_route = None
        if isinstance(planned_route, dict):
            destination_gate = planned_route.get("destination_gate")
            if destination_gate:
                # "Gate-DEFG-01 (DEFG야드 출입구)" -> "Gate-DEFG-01"
                gate_code = destination_gate.split(" ", 1)[0].strip()

    gate_id = lookup_gate_id(_state["db_conn"], gate_code)
    if gate_id is None:
        logger.error(f"게이트 ID 조회 실패: gateCode={gate_code}")
        raise HTTPException(status_code=500, detail=f"활성 게이트를 찾을 수 없음: {gate_code}")

    payload = {
        "gateId": gate_id,
        "recognizedPlateNo": pred or None,
        # 2026-09-30: trucks 테이블과 매칭된 경우에만 채움 - GateLog.java에
        # 이미 있는 실제 컬럼(actual_vehicle_no, "매칭된 차량 번호판 (trucks FK)"
        # 주석)에 그대로 넣음. 스키마/DTO 변경 전혀 없음.
        "actualVehicleNo": matched_vehicle["vehicleNo"] if matched_vehicle else None,
        "plateConfidence": plate_confidence,
        "recognitionStatus": recognition_status,
        "vehicleType": DEFAULT_VEHICLE_TYPE,
        "frontImageUrl": f"{GATE_PUBLIC_BASE}{image_url}",  # 2026-10-01 추가
        "ocrRawData": json.dumps({
            "engine": result["engine"],
            "detConfidence": result["det_conf"],
            "lineCount": result["line_count"],
            "crnnRawText": result["crnn_raw_text"],
            "crnnRawConfidence": result["crnn_raw_conf"],
            # 2026-09-30: 등록차량 매칭 결과 - gate_logs에 matchResult/gateOpen
            # 전용 컬럼은 없어서(actualVehicleNo만 있음) 프론트에서 바로 쓸 수
            # 있게 자유형식 JSON 칸에도 같이 실어보냄(위 모듈 docstring 참고).
            "matchResult": match_result,
            "matchedVehicle": matched_vehicle,
            "gateOpen": gate_open,
        }, ensure_ascii=False),
    }

    gate_log_id = None
    try:
        logger.info(f"  -> [게이트로그 전송 시도] 현재 사용 중인 쿠키: {_state['session'].cookies.get_dict()}")
        logger.info(f"  -> [게이트로그 전송] gateCode={gate_code}, gateId={gate_id}")
        
        resp = _state["session"].post(
            f"{DEFAULT_SCARGO_API_BASE}/api/v1/gate-logs", json=payload, timeout=5,
        )
        if resp.status_code == 201:
            gate_log_id = resp.json().get("gateLogId")
            logger.info(f"  -> DB 저장 완료 (gate_log_id={gate_log_id})")
        else:
            logger.error(f"게이트로그 저장 실패 (status={resp.status_code}): {resp.text}")
            logger.error(f"     (거부된 쿠키 상태: {_state['session'].cookies.get_dict()})")
    except requests.exceptions.RequestException:
        logger.exception("게이트로그 저장 중 네트워크 오류")


    logger.info(f"[{path.name}] 인식={pred or '(판독불가)'} 매칭={match_result} 차단기={'오픈' if gate_open else '닫힘'}")

    return ScanResult(
        fileName=path.name,
        recognizedPlate=pred or None,
        engine=result["engine"] or None,
        detConfidence=result["det_conf"],
        ocrConfidence=result["ocr_conf"],
        lineCount=result["line_count"],
        matchResult=match_result,
        matchedVehicle=matched_vehicle,
        gateOpen=gate_open,
        gateLogId=gate_log_id,
        imageUrl=image_url,
    )