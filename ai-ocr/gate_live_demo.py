# -*- coding: utf-8 -*-
"""
gate_live_demo.py
====================================================
CargoScan(YOLO11 + CRNN + EasyOCR) 인식 결과를 scargo 백엔드(Spring Boot,
PostgreSQL)의 게이트 통과 이력 API(/api/v1/gate-logs)에 실시간으로 전송하는
발표용 데모 스크립트.

- 등록 차량: 백엔드 조회를 통해 해당 차량의 지정 게이트 ID를 자동으로 매칭
- 미등록 차량: 1번~4번 게이트 중 랜덤으로 gateId 지정
"""
import argparse
import json
import os
import random

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import sys
import time
from pathlib import Path

import cv2
import requests
import torch

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

from batch_test_images import (  # noqa: E402
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
    """scargo 백엔드에 세션 로그인 후 쿠키 저장 상태를 확인합니다."""
    resp = session.post(
        f"{api_base}/api/accounts/login",
        json={"userId": user_id, "userPw": user_pw},
        timeout=5,
    )
    print(f"[로그인 시도] status={resp.status_code}, 응답 내용={resp.text}")
    print(f"[확보된 세션 쿠키 목록] {session.cookies.get_dict()}")

    if resp.status_code != 200:
        raise RuntimeError(
            f"로그인 실패 (status={resp.status_code}): {resp.text}\n"
            f"-> scargo 백엔드가 http://localhost:8080 에서 실행 중인지, 계정 정보가 정확한지 확인하세요."
        )
    
    # 만약 쿠키가 담기지 않았다면 수동으로 응답 헤더의 Set-Cookie를 추출해 세션에 강제 주입하는 안전장치
    if not session.cookies.get_dict():
        print("[경고] requests.Session이 자동으로 쿠키를 저장하지 못했습니다. 수동 헤더 주입을 시도합니다.")
        set_cookie = resp.headers.get("Set-Cookie")
        if set_cookie:
            # JSESSIONID 등 쿠키 추출 파싱
            session.headers.update({"Cookie": set_cookie})

    print(f"[로그인 성공] {user_id}")


def check_vehicle_registration(session: requests.Session, api_base: str, plate_no: str) -> dict:
    """
    백엔드에 번호판이 등록된 차량인지 조회합니다.
    """
    try:
        resp = session.get(f"{api_base}/api/v1/vehicles/search", params={"plateNo": plate_no}, timeout=3)
        if resp.status_code == 200:
            data = resp.json()
            if data: 
                return {"isRegistered": True, "gateId": data.get("assignedGateId", 1)}
    except Exception:
        pass
    
    return {"isRegistered": False, "gateId": None}


def post_gate_log(session: requests.Session, api_base: str, payload: dict) -> None:
    """
    세션 쿠키를 포함하여 게이트 로그를 전송합니다.
    """
    # 전송 시 현재 세션에 유지 중인 쿠키 상태 출력
    print(f"  -> [전송 시도] 현재 쿠키 상태: {session.cookies.get_dict()}")
    
    resp = session.post(f"{api_base}/api/v1/gate-logs", json=payload, timeout=5)
    if resp.status_code == 201:
        body = resp.json()
        print(
            f"  -> DB 저장 완료 (gate_log_id={body.get('gateLogId')}, "
            f"gateId={payload['gateId']}, status={payload['recognitionStatus']})"
        )
    else:
        print(f"  -> 전송 실패 (status={resp.status_code}): {resp.text}")
        print(f"     (거부된 쿠키 상태: {session.cookies.get_dict()})")


def recognize_one(model, crnn, ocr_reader, image_path: Path):
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
    parser.add_argument("--images", default="test/images", help="인식할 사진 폴더")
    parser.add_argument("--limit", type=int, default=8, help="처리할 사진 최대 장수")
    parser.add_argument("--delay", type=float, default=2.0, help="사진 사이 대기 시간(초)")
    parser.add_argument("--gate-name", default="인천항 통합 게이트", help="gate_logs.gate_name 값")
    parser.add_argument("--gate-type", default="IN", choices=["IN", "OUT"], help="gate_logs.gate_type 값")
    parser.add_argument("--vehicle-type", default="TRUCK", help="gate_logs.vehicle_type 값")
    parser.add_argument("--api-base", default=DEFAULT_API_BASE, help="scargo 백엔드 주소")
    parser.add_argument("--login-id", default=DEFAULT_LOGIN_ID, help="로그인 ID")
    parser.add_argument("--login-pw", default=DEFAULT_LOGIN_PW, help="로그인 PW")
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
    print(f"[EasyOCR] GPU 사용: {use_gpu} 로딩 중...")
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
        gt = extract_ground_truth(path.stem)
        pred, ocr_conf, engine, det_conf, line_count, crnn_raw_text, crnn_raw_conf = recognize_one(
            model, crnn, ocr_reader, path
        )

        recognition_status = "SUCCESS" if pred else "FAILED"
        plate_confidence = round(ocr_conf * 100, 2) if ocr_conf is not None else None

        target_gate_id = 1
        if pred:
            vehicle_info = check_vehicle_registration(session, args.api_base, pred)
            if vehicle_info["isRegistered"]:
                target_gate_id = vehicle_info["gateId"] or 1
                print(f"  [등록 차량 매칭] 번호판={pred} -> 지정 게이트 ID: {target_gate_id}")
            else:
                target_gate_id = random.randint(1, 4)
                print(f"  [미등록 차량] 번호판={pred} -> 랜덤 게이트 ID: {target_gate_id} 할당")
        else:
            target_gate_id = random.randint(1, 4)

        gt_note = f" (정답={gt})" if gt else ""
        print(f"[{i}/{len(files)}] {path.name}{gt_note}")
        print(f"  인식결과={pred or '(판독불가)'}  엔진={engine or '-'}  검출확신도={det_conf}")

        payload = {
            "gateId": int(target_gate_id),
            "gateName": f"인천항 {target_gate_id}번 게이트",
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