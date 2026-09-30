# -*- coding: utf-8 -*-
"""
gate_live_demo.py
====================================================
CargoScan(YOLO11 + CRNN + EasyOCR) 인식 결과를 scargo 백엔드(Spring Boot,
PostgreSQL)의 게이트 통과 이력 API(/api/v1/gate-logs)에 실시간으로 전송하는
발표용 데모 스크립트.

batch_test_images.py 와 완전히 동일한 검출/인식 로직(모델 선택, 패딩, CRNN+
EasyOCR 폴백, 번호판 문법 필터)을 그대로 재사용함 - 별도로 다시 구현하지 않고
batch_test_images.py 에서 그대로 import 해서 씀(정확도가 이미 실측 검증된
로직을 이중 관리하지 않기 위함).

실행 전 준비:
    1. PostgreSQL에 scargo 데이터베이스가 존재해야 함 (pgAdmin에서 생성)
    2. scargo 백엔드(Spring Boot)가 http://localhost:8080 에서 구동 중이어야 함
       (콘솔에 ">>> [AdminInitializer] admin 계정 최초 생성 완료" 등이 찍히면 정상)
    3. scargo_vue_0922 프론트엔드(npm run dev, localhost:5173)의
       "🚦 인식 데모" 화면을 열어두면 이 스크립트가 보내는 기록이 실시간으로 보임

실행 예시:
    python gate_live_demo.py
    python gate_live_demo.py --images valid/images --limit 10 --delay 2.5
    python gate_live_demo.py --gate-name "인천항 2번 게이트" --gate-type OUT

발표(영상 녹화) 시나리오:
    화면을 두 개 띄워놓고(왼쪽: 이 스크립트를 실행하는 터미널, 오른쪽: 브라우저의
    "🚦 인식 데모" 화면) 이 스크립트를 실행하면, 사진 한 장을 처리할 때마다
    --delay 초 간격으로 터미널에 인식 결과가 출력되고 동시에 브라우저 쪽 실시간
    피드에도 새 줄이 나타남 -> 그 화면을 그대로 녹화하면 됨.
"""
import argparse
import json
import os

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import sys
import time
from pathlib import Path

import cv2
import requests
import torch

APP_DIR = Path(__file__).resolve().parent
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
    extract_ground_truth,
    ocr_plate,
    padded_crop,
    pick_best_model,
)
from plate_ocr_crnn_attn import CRNNRecognizer, guess_line_count

DEFAULT_API_BASE = "http://localhost:8080"
DEFAULT_LOGIN_ID = "admin"
DEFAULT_LOGIN_PW = "admin1234"


def login(session: requests.Session, api_base: str, user_id: str, user_pw: str) -> None:
    """scargo 백엔드에 세션 로그인 - 이후 POST /api/v1/gate-logs 에 필요한
    ADMIN 권한 쿠키(JSESSIONID)를 session 객체에 저장함."""
    resp = session.post(
        f"{api_base}/api/accounts/login",
        json={"userId": user_id, "userPw": user_pw},
        timeout=5,
    )
    if resp.status_code != 200:
        raise RuntimeError(
            f"로그인 실패 (status={resp.status_code}): {resp.text}\n"
            f"-> scargo 백엔드가 http://localhost:8080 에서 실행 중인지, "
            f"계정 '{user_id}'가 존재하는지 확인하세요."
        )
    print(f"[로그인 성공] {user_id}")


def post_gate_log(session: requests.Session, api_base: str, payload: dict) -> None:
    resp = session.post(f"{api_base}/api/v1/gate-logs", json=payload, timeout=5)
    if resp.status_code == 201:
        body = resp.json()
        print(
            f"  -> DB 저장 완료 (gate_log_id={body.get('gateLogId')}, "
            f"status={payload['recognitionStatus']})"
        )
    else:
        print(f"  -> 전송 실패 (status={resp.status_code}): {resp.text}")


def recognize_one(model, crnn, ocr_reader, image_path: Path):
    """batch_test_images.py의 main() 루프 안 로직과 동일 (검출 + CRNN/EasyOCR
    폴백 + 번호판 문법 필터). 반환: (pred_text, ocr_conf, engine, det_conf,
    line_count, crnn_raw_text, crnn_raw_conf) - pred_text가 빈 문자열이면 판독불가."""
    img = cv2.imread(str(image_path))
    if img is None:
        return "", None, "", None, None, "", None

    result = model.predict(source=img, conf=YOLO_CONF, iou=YOLO_IOU, imgsz=IMGSZ, verbose=False)[0]
    boxes_xyxy = result.boxes.xyxy.cpu().numpy()
    boxes_conf = result.boxes.conf.cpu().numpy()
    if len(boxes_xyxy) == 0:
        return "", None, "", None, None, "", None

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
    if text and not _is_plausible_plate_text(text):
        text = ""

    pred = text.replace(" ", "") if text else ""
    return pred, ocr_conf, engine, det_conf, line_count, crnn_raw_text or "", crnn_raw_conf


def main():
    global IMGSZ

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--images", default="test/images", help="인식할 사진 폴더 (기본: test/images)")
    parser.add_argument("--limit", type=int, default=8, help="처리할 사진 최대 장수 (기본: 8)")
    parser.add_argument("--delay", type=float, default=2.0, help="사진 사이 대기 시간(초) - 발표 시연용 (기본: 2.0)")
    parser.add_argument("--gate-name", default="인천항 1번 게이트", help="gate_logs.gate_name 값")
    parser.add_argument("--gate-type", default="IN", choices=["IN", "OUT"], help="gate_logs.gate_type 값")
    parser.add_argument("--vehicle-type", default="TRUCK", help="gate_logs.vehicle_type 값")
    parser.add_argument("--api-base", default=DEFAULT_API_BASE, help="scargo 백엔드 주소 (기본: http://localhost:8080)")
    parser.add_argument("--login-id", default=DEFAULT_LOGIN_ID, help="로그인 계정 ID (기본: admin)")
    parser.add_argument("--login-pw", default=DEFAULT_LOGIN_PW, help="로그인 비밀번호 (기본: admin1234)")
    args = parser.parse_args()

    session = requests.Session()
    login(session, args.api_base, args.login_id, args.login_pw)

    from ultralytics import YOLO
    import easyocr

    model_path, imgsz = pick_best_model()
    IMGSZ = imgsz
    model = YOLO(model_path)

    crnn = None
    if CRNN_MODEL_PATH.exists() and CRNN_CHARS_PATH.exists():
        crnn = CRNNRecognizer()
        crnn.load(str(CRNN_MODEL_PATH), str(CRNN_CHARS_PATH))
        print(f"[CRNN] 로드 완료: {CRNN_MODEL_PATH.name}")
    else:
        print("[CRNN] 가중치를 못 찾음 - EasyOCR만 사용")

    use_gpu = torch.cuda.is_available()
    print(f"[EasyOCR] GPU 사용: {use_gpu} - 로딩 중(시간 좀 걸림)...")
    ocr_reader = easyocr.Reader(["ko", "en"], gpu=use_gpu)

    image_dir = (APP_DIR / args.images).resolve()
    files = [
        p for p in sorted(image_dir.glob("*"))
        if p.suffix.lower() in (".jpg", ".jpeg", ".png")
    ][: args.limit]
    if not files:
        raise SystemExit(f"{image_dir} 에서 처리할 사진을 찾지 못함")

    print(f"\n[시작] {len(files)}장을 {args.delay}초 간격으로 처리 -> {args.api_base}/api/v1/gate-logs\n")

    for i, path in enumerate(files, 1):
        gt = extract_ground_truth(path.stem)  # 참고용 (실제 서비스라면 없는 정보, 시연 로그에만 표시)
        pred, ocr_conf, engine, det_conf, line_count, crnn_raw_text, crnn_raw_conf = recognize_one(
            model, crnn, ocr_reader, path
        )

        recognition_status = "SUCCESS" if pred else "FAILED"
        plate_confidence = round(ocr_conf * 100, 2) if ocr_conf is not None else None

        gt_note = f" (정답={gt})" if gt else ""
        print(f"[{i}/{len(files)}] {path.name}{gt_note}")
        print(f"  인식결과={pred or '(판독불가)'}  엔진={engine or '-'}  검출확신도={det_conf}")

        payload = {
            "gateName": args.gate_name,
            "gateType": args.gate_type,
            "recognizedPlateNo": pred or None,
            "plateConfidence": plate_confidence,
            "recognitionStatus": recognition_status,
            "vehicleType": args.vehicle_type,
            "ocrRawData": json.dumps(
                {
                    "engine": engine,
                    "detConfidence": det_conf,
                    "lineCount": line_count,
                    "crnnRawText": crnn_raw_text,
                    "crnnRawConfidence": crnn_raw_conf,
                    "groundTruthForDemo": gt,
                },
                ensure_ascii=False,
            ),
        }
        post_gate_log(session, args.api_base, payload)

        if i < len(files):
            time.sleep(args.delay)

    print("\n[완료] 프론트엔드의 '🚦 인식 데모' 화면에서 실시간 피드를 확인하세요.")


if __name__ == "__main__":
    main()
