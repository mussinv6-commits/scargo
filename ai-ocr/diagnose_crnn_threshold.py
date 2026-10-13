# -*- coding: utf-8 -*-
"""
diagnose_crnn_threshold.py
====================================================
batch_test_images.py와 완전히 동일한 검출/크롭 로직으로 test+valid 767장을 돌리되,
"CRNN의 confidence가 threshold(0.5)보다 낮아서 EasyOCR로 폴백한 경우, 만약 그때도
CRNN 답을 그냥 썼다면 맞았을까 틀렸을까"를 알아보기 위한 진단 전용 스크립트.

배경: batch_test_result.csv를 엔진별로 분석해보니 - 오답 65건 중 45건(69%), 판독불가
52건 중 52건(100%)이 최종적으로 CRNN이 아니라 EasyOCR(+alt)이 선택된 경우였음.
CRNN이 EasyOCR보다 훨씬 정확한 엔진(문서상 beam+TTA 80.5% vs EasyOCR 54~60%)인데,
confidence 임계값(0.5) 때문에 CRNN 답이 있어도 버려지고 훨씬 약한 EasyOCR로
넘어간 게 오답/판독불가의 대부분을 차지하는 건 아닌지 확인하기 위함.

이 스크립트는 프로덕션 코드(plate_detector_gui.py, plate_ocr_crnn_attn.py)를 전혀
안 바꾸고, 각 이미지마다 "CRNN raw 답변+confidence"와 "지금 실제로 쓰이는 최종
답변"을 한 번에 같이 기록만 함 - 그래서 이 CSV 하나로 여러 threshold 값을 나중에
한번에 비교해볼 수 있음(모델을 여러 번 돌릴 필요 없음).

실행:
    python diagnose_crnn_threshold.py

결과:
    diagnose_crnn_threshold_result.csv 에 파일별 상세 기록
    (정답, CRNN답, CRNN확신도, 현재프로덕션답, 현재프로덕션엔진, 현재결과)
"""
import os

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import csv
from pathlib import Path

import cv2
import numpy as np
import torch

from batch_test_images import (
    APP_DIR,
    CRNN_MODEL_PATH,
    CRNN_CHARS_PATH,
    CRNN_CONF_THRESHOLD,
    YOLO_CONF,
    YOLO_IOU,
    IMAGE_DIRS,
    clean_plate_text,
    _is_plausible_plate_text,
    extract_ground_truth,
    pick_best_model,
    padded_crop,
    enhance_low_conf_crop,
    ocr_plate,
)


def main():
    from ultralytics import YOLO
    import easyocr
    from plate_ocr_crnn_attn import CRNNRecognizer

    model_path, imgsz = pick_best_model()
    model = YOLO(model_path)

    if not (CRNN_MODEL_PATH.exists() and CRNN_CHARS_PATH.exists()):
        raise SystemExit("CRNN 가중치를 못 찾음 - 이 진단은 CRNN이 있어야 의미가 있음")
    crnn = CRNNRecognizer()
    crnn.load(str(CRNN_MODEL_PATH), str(CRNN_CHARS_PATH))
    print(f"[CRNN] 로드 완료: {CRNN_MODEL_PATH.name}")

    use_gpu = torch.cuda.is_available()
    print(f"[EasyOCR] GPU 사용: {use_gpu} - 로딩 중(시간 좀 걸림)...")
    ocr_reader = easyocr.Reader(["ko", "en"], gpu=use_gpu)

    rows = []
    n_done = 0

    for split_dir in IMAGE_DIRS:
        split_name = split_dir.parent.name
        if not split_dir.exists():
            print(f"[건너뜀] {split_dir} 없음")
            continue
        files = sorted(split_dir.glob("*"))

        for path in files:
            if path.suffix.lower() not in (".jpg", ".jpeg", ".png"):
                continue
            gt = extract_ground_truth(path.stem)
            if gt is None:
                continue

            img = cv2.imread(str(path))
            if img is None:
                rows.append([split_name, path.name, gt, "", "", "", "", "이미지 로드 실패"])
                continue

            result = model.predict(source=img, conf=YOLO_CONF, iou=YOLO_IOU, imgsz=imgsz, verbose=False)[0]
            boxes_xyxy = result.boxes.xyxy.cpu().numpy()
            boxes_conf = result.boxes.conf.cpu().numpy()
            if len(boxes_xyxy) == 0:
                rows.append([split_name, path.name, gt, "", "", "", "", "검출 실패"])
                n_done += 1
                continue

            best_i = int(boxes_conf.argmax())
            x1, y1, x2, y2 = [float(v) for v in boxes_xyxy[best_i]]
            det_conf = float(boxes_conf[best_i])
            h_img, w_img = img.shape[:2]
            x1, y1 = max(0.0, x1), max(0.0, y1)
            x2, y2 = min(float(w_img), x2), min(float(h_img), y2)

            low_conf_box = det_conf < 0.7
            pad_ratio = 0.30 if low_conf_box else 0.18
            pad_bottom_ratio = 0.65 if low_conf_box else pad_ratio
            crop = padded_crop(img, x1, y1, x2, y2, pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio)
            if low_conf_box:
                crop = enhance_low_conf_crop(crop)

            # 1) CRNN 단독 답 - confidence 무관하게 항상 기록 (threshold는 여기서 아예 안 봄)
            crnn_text_raw, crnn_conf = crnn.recognize(crop, tta=True, decode="beam")
            crnn_text = clean_plate_text(crnn_text_raw) if crnn_text_raw else ""
            if crnn_text and not _is_plausible_plate_text(crnn_text):
                crnn_text = ""
            crnn_pred = crnn_text.replace(" ", "") if crnn_text else ""

            # 2) 지금 실제 프로덕션 로직(v3.14, threshold=0.5) 최종 답 - batch_test_images.py와 100% 동일
            prod_text, prod_conf, prod_engine = ocr_plate(crnn, ocr_reader, crop, CRNN_CONF_THRESHOLD)
            if prod_text:
                prod_text = clean_plate_text(prod_text)
            if prod_text and not _is_plausible_plate_text(prod_text):
                prod_text = ""
            prod_pred = prod_text.replace(" ", "") if prod_text else ""

            prod_status = "판독불가" if not prod_pred else ("정답" if prod_pred == gt else "오답")

            rows.append([
                split_name, path.name, gt,
                crnn_pred, f"{crnn_conf:.3f}" if crnn_conf is not None else "",
                prod_pred, prod_engine, prod_status,
            ])
            n_done += 1
            print(f"[{split_name}] {n_done:4d}  정답={gt:12s}  CRNN단독={crnn_pred or '(없음)':12s}"
                  f"(conf={crnn_conf if crnn_conf is not None else 0:.2f})  "
                  f"프로덕션={prod_pred or '(없음)':12s}[{prod_engine}]  [{path.name}]")

    out_path = APP_DIR / "diagnose_crnn_threshold_result.csv"
    with open(out_path, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow([
            "구분", "파일명", "정답",
            "CRNN단독답", "CRNN단독확신도",
            "프로덕션답", "프로덕션엔진", "프로덕션결과",
        ])
        w.writerows(rows)

    print(f"\n총 {n_done}장 처리 완료. 결과: {out_path.name}")
    print("이 CSV를 가지고 CRNN confidence threshold를 여러 값으로 가정했을 때 정확도가")
    print("어떻게 바뀌는지는 따로(모델 재실행 없이) 분석함.")


if __name__ == "__main__":
    main()
