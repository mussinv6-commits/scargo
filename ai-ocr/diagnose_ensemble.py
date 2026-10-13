# -*- coding: utf-8 -*-
"""
diagnose_ensemble.py
====================================================
BiLSTM 단독 모델(ocr_model/best_recognizer.pth)과 현재 프로덕션이 쓰는
어텐션 모델(ocr_model_attn/best_recognizer_attn.pth)을, 똑같은 YOLO 검출+크롭
결과에 대해 나란히 돌려서 "두 모델의 오답이 서로 다른 사진에서 나는지(독립적)
아니면 같은 사진에서 같이 틀리는지(상관됨)"를 먼저 확인하기 위한 진단 전용
스크립트. 프로덕션 코드(batch_test_images.py, plate_ocr_crnn_attn.py)는
전혀 안 바꿈 - 순수 조회용.

왜 이게 먼저 필요한가:
2026-09-29 CHANGELOG에 기록된 대로, "CRNN 확신도 통과 시에도 EasyOCR을 상시
대조해서 다수결로 뒤집는" 앙상블 시도가 실측 후 실패로 되돌려진 적이 있음
(EasyOCR 본선/alt가 같은 리더기라 "독립된 두 표"가 아니라 같은 약점을
공유해서 다수결이 오히려 정답을 오답으로 뒤집었음). BiLSTM단독과 어텐션은
서로 다른 모델(가중치도 다르고 어텐션 레이어 유무도 다름)이라 독립적일
가능성이 있지만, 확인 없이 앙상블 코드부터 짜면 같은 실수를 반복할 위험이
있음 - 그래서 앙상블 로직을 짜기 전에 먼저 이 스크립트로 실제 오차 상관관계
데이터를 확보함.

실행:
    python diagnose_ensemble.py

결과:
    diagnose_ensemble_result.csv 에 파일별 두 모델의 답/확신도/정답여부를
    나란히 기록. 이 CSV로 아래를 확인할 수 있음:
    - 어텐션은 틀렸는데 BiLSTM은 맞은 사진이 몇 장이나 되는지(있으면 앙상블
      가치 있음)
    - 반대로 BiLSTM만 맞은 사진이 몇 장인지
    - 둘 다 틀릴 때 같은 오답을 내는지(상관 - 앙상블 무의미) 아니면 다른
      오답을 내는지(독립 - 적어도 "둘이 다르면 재검토 필요" 플래그는 가능)
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
    YOLO_CONF,
    YOLO_IOU,
    IMAGE_DIRS,
    clean_plate_text,
    _is_plausible_plate_text,
    extract_ground_truth,
    pick_best_model,
    adaptive_imgsz,
    padded_crop,
    enhance_low_conf_crop,
)

# 어텐션 모델(현재 프로덕션) - plate_ocr_crnn_attn.py
from plate_ocr_crnn_attn import CRNNRecognizer as AttnCRNNRecognizer

# BiLSTM 단독 모델(예전 모듈) - plate_ocr_crnn.py
# 두 모듈 다 CRNN이라는 이름의 클래스를 갖고 있지만 CRNNRecognizer만 가져다 쓰므로
# 이름 충돌 없음(각자 내부에서 알아서 자기 CRNN 클래스를 씀).
from plate_ocr_crnn import CRNNRecognizer as BiLSTMCRNNRecognizer

ATTN_MODEL_DIR = APP_DIR / "ocr_model_attn"
ATTN_MODEL_PATH = ATTN_MODEL_DIR / "best_recognizer_attn.pth"
ATTN_CHARS_PATH = ATTN_MODEL_DIR / "chars_attn.json"

BILSTM_MODEL_DIR = APP_DIR / "ocr_model"
BILSTM_MODEL_PATH = BILSTM_MODEL_DIR / "best_recognizer.pth"
BILSTM_CHARS_PATH = BILSTM_MODEL_DIR / "chars.json"


def _clean_and_validate(text):
    if not text:
        return ""
    text = clean_plate_text(text)
    if text and not _is_plausible_plate_text(text):
        return ""
    return text.replace(" ", "") if text else ""


def main():
    from ultralytics import YOLO

    if not (ATTN_MODEL_PATH.exists() and ATTN_CHARS_PATH.exists()):
        raise SystemExit(f"어텐션 모델을 못 찾음: {ATTN_MODEL_PATH}")
    if not (BILSTM_MODEL_PATH.exists() and BILSTM_CHARS_PATH.exists()):
        raise SystemExit(f"BiLSTM단독 모델을 못 찾음: {BILSTM_MODEL_PATH}")

    model_path, imgsz = pick_best_model()
    yolo = YOLO(model_path)

    attn_crnn = AttnCRNNRecognizer()
    attn_crnn.load(str(ATTN_MODEL_PATH), str(ATTN_CHARS_PATH))
    print(f"[어텐션] 로드 완료: {ATTN_MODEL_PATH.name}")

    bilstm_crnn = BiLSTMCRNNRecognizer()
    bilstm_crnn.load(str(BILSTM_MODEL_PATH), str(BILSTM_CHARS_PATH))
    print(f"[BiLSTM단독] 로드 완료: {BILSTM_MODEL_PATH.name}")

    rows = []
    n_done = 0
    # 요약용 카운터
    both_correct = attn_only = bilstm_only = both_wrong_same = both_wrong_diff = neither_read = 0

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
                rows.append([split_name, path.name, gt, "", "", "", "", "", "", "이미지 로드 실패"])
                continue

            img_imgsz = adaptive_imgsz(img, imgsz)
            result = yolo.predict(source=img, conf=YOLO_CONF, iou=YOLO_IOU, imgsz=img_imgsz, verbose=False)[0]
            boxes_xyxy = result.boxes.xyxy.cpu().numpy()
            boxes_conf = result.boxes.conf.cpu().numpy()
            if len(boxes_xyxy) == 0:
                rows.append([split_name, path.name, gt, "", "", "", "", "", "", "검출 실패"])
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
            box_ratio = (x2 - x1) / (y2 - y1) if (y2 - y1) > 0 else None
            crop = padded_crop(img, x1, y1, x2, y2, pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio)
            if low_conf_box:
                crop = enhance_low_conf_crop(crop)

            # 어텐션 모델(현재 프로덕션과 100% 동일한 호출 방식)
            attn_text_raw, attn_conf = attn_crnn.recognize(
                crop, tta=True, decode="beam", box_ratio=box_ratio,
                pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio,
            )
            attn_pred = _clean_and_validate(attn_text_raw)

            # BiLSTM단독 모델(구형 인터페이스 - box_ratio 등 인자 없음)
            bilstm_text_raw, bilstm_conf = bilstm_crnn.recognize(crop, tta=True, decode="beam")
            bilstm_pred = _clean_and_validate(bilstm_text_raw)

            attn_correct = bool(attn_pred) and attn_pred == gt
            bilstm_correct = bool(bilstm_pred) and bilstm_pred == gt

            if attn_correct and bilstm_correct:
                case = "둘다정답"
                both_correct += 1
            elif attn_correct and not bilstm_correct:
                case = "어텐션만정답"
                attn_only += 1
            elif bilstm_correct and not attn_correct:
                case = "BiLSTM만정답"
                bilstm_only += 1
            elif not attn_pred and not bilstm_pred:
                case = "둘다판독불가"
                neither_read += 1
            elif attn_pred and bilstm_pred and attn_pred == bilstm_pred:
                case = "둘다오답_같음"
                both_wrong_same += 1
            else:
                case = "둘다오답_다름(또는한쪽만판독불가)"
                both_wrong_diff += 1

            rows.append([
                split_name, path.name, gt,
                attn_pred, f"{attn_conf:.3f}" if attn_conf is not None else "", attn_correct,
                bilstm_pred, f"{bilstm_conf:.3f}" if bilstm_conf is not None else "", bilstm_correct,
                case,
            ])
            n_done += 1
            print(f"[{split_name}] {n_done:4d} 정답={gt:12s} 어텐션={attn_pred or '(없음)':12s} "
                  f"BiLSTM={bilstm_pred or '(없음)':12s} [{case}] ({path.name})")

    out_path = APP_DIR / "diagnose_ensemble_result.csv"
    with open(out_path, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow([
            "구분", "파일명", "정답",
            "어텐션답", "어텐션확신도", "어텐션정답여부",
            "BiLSTM답", "BiLSTM확신도", "BiLSTM정답여부",
            "케이스",
        ])
        w.writerows(rows)

    print(f"\n총 {n_done}장 처리 완료. 결과: {out_path.name}")
    print("==================== 오차 상관관계 요약 ====================")
    print(f"둘다정답: {both_correct}")
    print(f"어텐션만정답(BiLSTM은 틀림) - 앙상블로 지킬 수 있었을 정답: {attn_only}")
    print(f"BiLSTM만정답(어텐션은 틀림) - 앙상블로 구할 수 있는 정답: {bilstm_only}")
    print(f"둘다오답_같음(완전히 상관된 오류 - 앙상블 무의미): {both_wrong_same}")
    print(f"둘다오답_다름 또는 한쪽만판독불가: {both_wrong_diff}")
    print(f"둘다판독불가: {neither_read}")
    print("\n판단 기준: BiLSTM만정답 건수가 유의미하게 크고(예: 10건 이상) 둘다오답_같음")
    print("비율이 낮으면 앙상블을 시도할 가치가 있음. 반대로 BiLSTM만정답이 거의 없으면")
    print("(어텐션이 이미 BiLSTM의 상위호환) 앙상블은 복잡도만 늘리고 얻는 게 없음.")


if __name__ == "__main__":
    main()
