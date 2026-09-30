# -*- coding: utf-8 -*-
"""
gate_watch_service.py
====================================================
gate_live_demo.py(발표용 데모 스크립트)를 실제 게이트 운영에 쓸 수 있는 상시
서비스로 발전시킨 정식버전.

데모와 다른 점 (2026-09-29, 사용자 확인 사항 반영):
- 정해진 테스트 사진 몇 장을 --limit/--delay로 재생하는 게 아니라, 지정된 폴더를
  계속 감시(폴링)하다가 새 사진이 들어올 때마다 자동으로 인식+전송함(실제 게이트
  카메라/스캐너가 이 폴더에 사진을 떨어뜨려준다고 가정).
- (2026-09-30 사용자 확인) 폴더에 사진이 여러 장 쌓여 있어도 한 폴링마다 전부
  처리하지 않고 무작위로 딱 한 장만 골라 처리함 - "게이트인에서 트럭이 한 대씩
  들어오는" 모습을 자연스럽게 보여주기 위함(데모/실 운영 공통).
- 로그인 계정/비밀번호를 코드에 평문으로 두지 않고 환경변수(GATE_API_ID,
  GATE_API_PW)에서만 읽음 - 설정 안 하면 평문 기본값으로 조용히 넘어가지 않고
  바로 에러로 멈춤.
- 처리한 사진은 성공/실패 여부에 따라 하위 폴더(processed/ 또는 failed/)로
  옮겨서 같은 사진을 중복 처리하지 않고, 나중에 진짜 문제 있었던 사진만 따로
  확인할 수 있게 함.
- print 대신 logging 모듈로 타임스탬프 찍힌 로그를 콘솔+파일
  (gate_watch_service.log)에 같이 남김 - 상시 서비스라 나중에 "언제 뭐가
  있었는지" 되짚어볼 수 있어야 함.
- 백엔드가 아직 안 떠 있거나(연결 실패) 세션이 끊겨도(401/403) 서비스가 죽지
  않고 재시도함 - 데모 스크립트는 로그인 실패하면 바로 죽었지만, 상시 서비스는
  그러면 안 됨.
- 검출/인식 로직 자체(모델 선택, 패딩, CRNN+EasyOCR 폴백, 번호판 문법 필터,
  2026-09-29 추가된 EasyOCR "지역명 놓침" 필터 포함)는 batch_test_images.py에서
  그대로 import - gate_live_demo.py와 완전히 동일한 로직을 재사용함(정확도가
  이미 실측 검증된 경로를 이중 관리하지 않기 위함).

*** gate_live_demo.py는 그대로 남겨둠 - 발표 녹화용(정해진 사진을 --delay
간격으로 재생)으로는 계속 그걸 쓰면 됨. 이 서비스는 그 용도가 아님. ***

실행 전 준비 (필수):
    1. 환경변수 설정 (PowerShell 예시)
       $env:GATE_API_ID = "admin"
       $env:GATE_API_PW = "admin1234"
       (설정 안 하면 실행 시 바로 에러 메시지와 함께 종료됨 - 코드에 평문 계정을
       안 남기기 위함)
    2. PostgreSQL에 scargo 데이터베이스가 존재해야 함
    3. scargo 백엔드(Spring Boot)가 http://localhost:8080 에서 구동 중이어야 함
       (이 서비스는 백엔드가 아직 안 떠 있어도 죽지 않고 재시도하면서 기다림)

실행:
    python gate_watch_service.py
    python gate_watch_service.py --watch-dir "C:\\게이트_수신함" --gate-name "인천항 1번 게이트"
    (Ctrl+C로 정상 종료 - 지금까지 처리한 건수 요약을 출력하고 끝남)

감시 폴더 안에 사진이 들어오면:
    watch-dir/새사진.jpg
    -> 인식 성공/판독불가 둘 다: watch-dir/processed/새사진.jpg 로 이동 + DB 저장
       (판독불가여도 "게이트를 통과했다"는 기록 자체는 남아야 하므로 저장함)
    -> 처리 중 예외 발생(이미지 파일 손상 등): watch-dir/failed/새사진.jpg 로 이동
       (DB 저장 안 함 - 나중에 이 폴더만 확인하면 진짜 문제 있었던 사진만 모아서 보임)
"""
import argparse
import json
import logging
import os
import random
import shutil
import sys
import time
from pathlib import Path

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import cv2
import requests
import torch

def _exe_dir():
    """plate_detector_gui.py의 _exe_dir()와 동일(2026-09-29 추가, batch_test_images.py
    에도 동일하게 적용함) - PyInstaller onedir로 빌드된 exe에서 sys.executable은
    exe가 실제로 있는 최상위 폴더를 가리키지만 __file__은 그 안의 _internal
    폴더를 가리켜서 한 단계 더 들어가 버림. 감시 폴더(게이트_수신함), 로그
    파일(gate_watch_service.log)을 exe 옆(사용자가 실제로 보는 위치)에 정확히
    두기 위해 이 함수 기준으로 경로를 잡음. 일반 스크립트로 실행할 때는 기존과
    동일하게 동작함(__file__ 기준)."""
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

DEFAULT_API_BASE = "http://localhost:8080"
# 2026-09-29: exe로 빌드했을 때 배포 폴더(dist\gate_watch_service\) 옆에 바로
# 보이도록 APP_DIR.parent가 아니라 APP_DIR 기준으로 둠 - 스크립트로 실행할 때도
# (frozen=False) _exe_dir()가 __file__ 기준을 쓰므로 project.v4i.yolov8 폴더
# 바로 아래에 생김.
DEFAULT_WATCH_DIR = APP_DIR / "게이트_수신함"
DEFAULT_POLL_INTERVAL = 2.0  # 초 - 폴더를 몇 초마다 다시 훑어볼지
LOGIN_RETRY_INTERVAL = 5.0   # 초 - 백엔드가 아직 안 떠 있을 때 재로그인 재시도 간격
IMAGE_SUFFIXES = (".jpg", ".jpeg", ".png")

logger = logging.getLogger("gate_watch_service")


def setup_logging(log_path: Path):
    logger.setLevel(logging.INFO)
    fmt = logging.Formatter("%(asctime)s [%(levelname)s] %(message)s", datefmt="%Y-%m-%d %H:%M:%S")
    stream_handler = logging.StreamHandler(sys.stdout)
    stream_handler.setFormatter(fmt)
    file_handler = logging.FileHandler(log_path, encoding="utf-8")
    file_handler.setFormatter(fmt)
    logger.addHandler(stream_handler)
    logger.addHandler(file_handler)


def get_credentials():
    """2026-09-29 사용자 확인: 계정/비밀번호는 환경변수로만 받음 - 코드에 평문
    기본값을 두고 조용히 그걸로 넘어가지 않고, 안 정해져 있으면 바로 에러로 멈춤."""
    user_id = os.environ.get("GATE_API_ID")
    user_pw = os.environ.get("GATE_API_PW")
    if not user_id or not user_pw:
        raise SystemExit(
            "환경변수 GATE_API_ID / GATE_API_PW 가 설정 안 됨 - 정식버전은 계정을 "
            "코드에 평문으로 두지 않습니다. 예(PowerShell):\n"
            '  $env:GATE_API_ID = "admin"\n'
            '  $env:GATE_API_PW = "admin1234"\n'
            "설정 후 다시 실행하세요."
        )
    return user_id, user_pw


def login(session, api_base, user_id, user_pw, retry=True):
    """로그인. retry=True(기본, 서비스 시작 시)면 백엔드가 아직 안 떠 있어서
    연결 자체가 안 될 때 죽지 않고 LOGIN_RETRY_INTERVAL마다 계속 재시도함(상시
    서비스는 백엔드보다 먼저 켜져도 괜찮아야 함). retry=False(세션 만료로
    재로그인할 때)는 무한루프를 막기 위해 한 번만 시도하고 실패하면 예외를 던짐.
    계정/비번 자체가 틀린 경우(연결은 되는데 200이 아닌 응답)는 재시도해도 안 될
    거라 어느 경우든 바로 에러로 멈춤."""
    while True:
        try:
            resp = session.post(
                f"{api_base}/api/accounts/login",
                json={"userId": user_id, "userPw": user_pw},
                timeout=5,
            )
        except requests.exceptions.RequestException as e:
            if not retry:
                raise
            logger.warning(f"백엔드({api_base})에 연결 못함 - {e!r} - "
                            f"{LOGIN_RETRY_INTERVAL}초 후 재시도")
            time.sleep(LOGIN_RETRY_INTERVAL)
            continue

        if resp.status_code == 200:
            logger.info(f"로그인 성공: {user_id}")
            return
        raise SystemExit(
            f"로그인 실패 (status={resp.status_code}): {resp.text}\n"
            f"-> 계정 '{user_id}'/비밀번호를 확인하세요 (GATE_API_ID/GATE_API_PW)."
        )


def post_gate_log(session, api_base, payload, user_id, user_pw):
    """DB 저장. 세션 만료(401/403)로 보이면 한 번 재로그인 후 딱 한 번만
    재시도함(무한루프 방지) - 그 외 오류는 로그만 남기고 넘어감(사진 자체는
    호출부에서 processed/로 옮겨지므로 같은 사진이 재시도 루프에 다시 걸리진
    않음 - 실패 기록은 로그 파일로 확인)."""
    resp = session.post(f"{api_base}/api/v1/gate-logs", json=payload, timeout=5)
    if resp.status_code == 201:
        body = resp.json()
        logger.info(f"  DB 저장 완료 (gate_log_id={body.get('gateLogId')}, "
                    f"status={payload['recognitionStatus']})")
        return True
    if resp.status_code in (401, 403):
        logger.warning("  세션 만료로 보임 - 재로그인 후 1회 재시도")
        try:
            login(session, api_base, user_id, user_pw, retry=False)
        except Exception:
            logger.exception("  재로그인 실패")
            return False
        resp = session.post(f"{api_base}/api/v1/gate-logs", json=payload, timeout=5)
        if resp.status_code == 201:
            body = resp.json()
            logger.info(f"  DB 저장 완료(재시도) (gate_log_id={body.get('gateLogId')})")
            return True
    logger.error(f"  DB 전송 실패 (status={resp.status_code}): {resp.text}")
    return False


def recognize_one(model, crnn, ocr_reader, imgsz, image_path: Path):
    """batch_test_images.py / gate_live_demo.py의 검출+인식 로직과 동일
    (모델 선택, 패딩, CRNN+EasyOCR 폴백, 번호판 문법 필터 - 2026-09-29 추가된
    EasyOCR "지역명 놓침" 필터 포함). 이미지 자체를 못 읽으면 None을 반환함
    (호출부에서 failed/로 분류하는 신호로 씀 - 검출/판독 실패는 빈 문자열
    결과를 정상적으로 반환하는 것과 구분됨)."""
    img = cv2.imread(str(image_path))
    if img is None:
        return None

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
    # 2026-09-29 (plate_detector_gui.py와 동일) - EasyOCR이 지역명을 놓쳐서
    # 우연히 "지역명 없는" 형식과 맞아떨어지는 오탐지 차단.
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


def _pick_random_stable_file(watch_dir: Path):
    """2026-09-30 사용자 확인: 폴더에 여러 장이 쌓여 있어도 한 번에 다 처리하지
    않고 무작위로 한 장만 골라 처리함 - "게이트인에서 트럭이 한 대씩 들어오는"
    모습을 자연스럽게 보여주기 위함. 카메라가 아직 쓰고 있는 파일은 크기
    안정성 체크로 건너뜀(기존 로직과 동일 - 후보들을 무작위 순서로 섞은 뒤
    그중 안정적인 첫 파일을 반환)."""
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
            continue  # 그 사이 다른 프로세스가 옮겼을 수 있음
        if size1 == size2 and size2 != 0:
            return path
    return None


def _move_safely(path: Path, dest_dir: Path):
    """대상 폴더에 같은 이름이 이미 있으면(예: 같은 파일명으로 재촬영) 덮어쓰지
    않고 타임스탬프를 붙여서 둘 다 남김."""
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest = dest_dir / path.name
    if dest.exists():
        dest = dest_dir / f"{path.stem}_{int(time.time() * 1000)}{path.suffix}"
    try:
        shutil.move(str(path), str(dest))
    except Exception:
        logger.exception(f"{path.name} 이동 실패 (다음 폴링에서 다시 시도될 수 있음)")


def process_one_file(path, model, crnn, ocr_reader, imgsz, session, api_base,
                      user_id, user_pw, gate_code, vehicle_type,
                      processed_dir, failed_dir, stats):
    try:
        result = recognize_one(model, crnn, ocr_reader, imgsz, path)
    except Exception:
        logger.exception(f"[{path.name}] 인식 중 예외 발생 - failed/로 이동")
        _move_safely(path, failed_dir)
        stats["failed"] += 1
        return

    if result is None:
        logger.warning(f"[{path.name}] 이미지 파일을 못 읽음(손상 가능) - failed/로 이동")
        _move_safely(path, failed_dir)
        stats["failed"] += 1
        return

    pred = result["pred"]
    recognition_status = "SUCCESS" if pred else "FAILED"
    plate_confidence = round(result["ocr_conf"] * 100, 2) if result["ocr_conf"] is not None else None

    logger.info(f"[{path.name}] 인식결과={pred or '(판독불가)'} 엔진={result['engine'] or '-'} "
                f"검출확신도={result['det_conf']}")

    payload = {
        # 2026-09-30 변경: gate_logs가 게이트 마스터 테이블(gates)을
        # gate_id(FK)로 참조하는 구조로 바뀌면서, scargo가 이제
        # gateName/gateType 대신 gateCode(게이트 마스터의 gate_code)를 받음.
        "gateCode": gate_code,
        "recognizedPlateNo": pred or None,
        "plateConfidence": plate_confidence,
        "recognitionStatus": recognition_status,
        "vehicleType": vehicle_type,
        "ocrRawData": json.dumps({
            "engine": result["engine"],
            "detConfidence": result["det_conf"],
            "lineCount": result["line_count"],
            "crnnRawText": result["crnn_raw_text"],
            "crnnRawConfidence": result["crnn_raw_conf"],
        }, ensure_ascii=False),
    }
    saved = post_gate_log(session, api_base, payload, user_id, user_pw)
    # DB 저장 성공 여부와 무관하게 사진은 processed/로 옮김(같은 사진이 감시
    # 루프에 계속 남아 반복 재처리되는 걸 막기 위함) - 저장 실패는 로그 파일로만
    # 확인 가능(stats["db_failed"]로 종료 시 요약에도 남음).
    _move_safely(path, processed_dir)
    if pred:
        stats["success"] += 1
    else:
        stats["unread"] += 1
    if not saved:
        stats["db_failed"] += 1


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--watch-dir", default=str(DEFAULT_WATCH_DIR),
                         help=f"감시할 폴더(카메라/스캐너가 사진을 넣는 곳) - 기본: {DEFAULT_WATCH_DIR}")
    parser.add_argument("--poll-interval", type=float, default=DEFAULT_POLL_INTERVAL,
                         help="폴더를 다시 훑어보는 간격(초)")
    # 2026-09-30 변경: gates 마스터 테이블 도입으로 게이트명/구분을 직접 넘기는
    # 대신 schema.sql에 seed된 게이트 코드로 지정함(Gate-ABC-01/Gate-I-01/
    # Gate-H-01/Gate-DEFG-01 중 하나).
    parser.add_argument("--gate-code", default="Gate-ABC-01")
    parser.add_argument("--vehicle-type", default="TRUCK")
    parser.add_argument("--api-base", default=DEFAULT_API_BASE)
    args = parser.parse_args()

    watch_dir = Path(args.watch_dir).resolve()
    processed_dir = watch_dir / "processed"
    failed_dir = watch_dir / "failed"
    watch_dir.mkdir(parents=True, exist_ok=True)
    processed_dir.mkdir(parents=True, exist_ok=True)
    failed_dir.mkdir(parents=True, exist_ok=True)

    setup_logging(APP_DIR / "gate_watch_service.log")

    user_id, user_pw = get_credentials()

    logger.info(f"감시 폴더: {watch_dir}")
    logger.info(f"백엔드: {args.api_base}")

    session = requests.Session()
    login(session, args.api_base, user_id, user_pw)

    from ultralytics import YOLO
    import easyocr

    model_path, imgsz = pick_best_model()
    model = YOLO(model_path)

    crnn = None
    if CRNN_MODEL_PATH.exists() and CRNN_CHARS_PATH.exists():
        crnn = CRNNRecognizer()
        crnn.load(str(CRNN_MODEL_PATH), str(CRNN_CHARS_PATH))
        logger.info(f"CRNN 로드 완료: {CRNN_MODEL_PATH.name}")
    else:
        logger.warning("CRNN 가중치를 못 찾음 - EasyOCR만 사용")

    use_gpu = torch.cuda.is_available()
    logger.info(f"EasyOCR GPU 사용: {use_gpu} - 로딩 중...")
    ocr_reader = easyocr.Reader(["ko", "en"], gpu=use_gpu)

    stats = {"success": 0, "unread": 0, "failed": 0, "db_failed": 0}
    logger.info("감시 시작 - Ctrl+C로 종료")

    try:
        while True:
            # 2026-09-30 변경: 폴더에 여러 장이 쌓여 있어도 한 폴링마다 전부
            # 처리하지 않고, 무작위로 딱 한 장만 골라 처리함(파일 크기 안정성
            # 체크는 _pick_random_stable_file 안에서 그대로 수행) - "트럭이
            # 한 대씩 게이트에 들어오는" 모습을 보여주기 위한 사용자 요청.
            path = _pick_random_stable_file(watch_dir)
            if path is not None:
                process_one_file(
                    path, model, crnn, ocr_reader, imgsz, session, args.api_base,
                    user_id, user_pw, args.gate_code, args.vehicle_type,
                    processed_dir, failed_dir, stats,
                )
            time.sleep(args.poll_interval)
    except KeyboardInterrupt:
        logger.info("Ctrl+C로 종료 요청 받음")
    finally:
        logger.info(
            f"종료 - 처리 요약: 인식성공 {stats['success']} / 판독불가 {stats['unread']} / "
            f"이미지오류 {stats['failed']} / DB저장실패 {stats['db_failed']}"
        )


if __name__ == "__main__":
    main()
