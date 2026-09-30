# -*- coding: utf-8 -*-
"""
audit_labels.py
====================================================
학습/검증에 쓰인 라벨(ocr_train_data/labels.csv) 중, 실제로 틀린 라벨(파일명에서
자동 추출하는 과정에서 생긴 오타/오류 등)이 얼마나 섞여있는지 찾아내는 스크립트.

예전에 "서울31바8105.jpeg" 파일을 직접 눈으로 봤더니 실제 번호판은
"서울83...1551"에 가까웠던 사례(라벨 자체가 틀렸던 경우)가 있었음 - 이런 사례가
1,961장 전체에 더 있는지, 사람이 일일이 다 보지 않고도 찾아내기 위한 스크립트.

원리
====================================================
지금 학습된 모델(best_recognizer.pth)로 전체 이미지를 예측했을 때,
"예측이 라벨과 다른데 확신도(confidence)는 오히려 높은" 경우는 두 가지 중 하나임:
  1) 라벨이 틀렸고, 모델이 맞게 읽은 경우 (진짜 라벨 오류)
  2) 모델이 그럴듯하게 틀리게 읽었지만 확신은 있는 경우 (진짜 모델 오류)
확신도가 높을수록 1)일 가능성이 커짐 - 그래서 확신도 순으로 정렬해서 사람이
직접 눈으로 확인할 후보 목록을 좁혀줌 (1,961장을 다 보는 대신 상위 N개만 보면 됨).

주의: 검증셋(196장)뿐 아니라 학습셋(1,765장)도 같이 검사함. 학습셋은 모델이
이미 본 데이터라 "확신도 높은데 라벨과 다름"이 상대적으로 드물게 나오지만
(모델이 라벨을 어느 정도 외웠을 수 있어서), 그래도 나오면 라벨 오류일 가능성이
높다는 신호로 봐도 됨.

실행 방법
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    python audit_labels.py

결과로 label_audit_candidates.csv 파일이 ocr_train_data 폴더에 생성됩니다.
엑셀로 열어서 "predicted"가 실제로 맞다고 판단되면(=label이 틀렸으면),
new_label 칸에 맞는 정답을 적어주세요. 아니면(=지금 label이 맞고 모델이
틀린 거면) new_label을 비워두면 됩니다. 다 확인한 뒤 apply_label_fixes.py를
실행하면 new_label이 채워진 행만 labels.csv에 자동으로 반영됩니다.
"""
import csv
from pathlib import Path

import cv2
import numpy as np
import torch

from plate_ocr_crnn import CRNN, greedy_decode_with_conf, guess_line_count, preprocess_for_model

PROJECT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
DATA_DIR = PROJECT_DIR / "ocr_train_data"
CROPS_DIR = DATA_DIR / "crops"
LABELS_CSV = DATA_DIR / "labels.csv"

OUT_DIR = PROJECT_DIR / "ocr_model"
BEST_MODEL_PATH = OUT_DIR / "best_recognizer.pth"
CHARS_JSON_PATH = OUT_DIR / "chars.json"

OUT_CSV = DATA_DIR / "label_audit_candidates.csv"

# 이 확신도 이상인데 라벨과 다르면 "라벨 오류 의심" 후보로 뽑음.
# 너무 낮으면(예: 0.3) 그냥 모델이 못 읽는 어려운 사진까지 다 섞여서 검토량이 늘어나고,
# 너무 높으면(예: 0.95) 진짜 오류를 놓칠 수 있어서 0.6을 기본값으로 둠 -
# 실행 후 후보가 너무 많거나 적으면 이 값을 조정해서 다시 돌리면 됨.
CONF_THRESHOLD = 0.6


def imread_unicode(path: Path):
    data = np.fromfile(str(path), dtype=np.uint8)
    if data.size == 0:
        return None
    return cv2.imdecode(data, cv2.IMREAD_COLOR)


def char_error_rate(pred: str, gt: str) -> float:
    if len(gt) == 0:
        return 0.0 if len(pred) == 0 else 1.0
    dp = list(range(len(gt) + 1))
    for i, pc in enumerate(pred, start=1):
        prev = dp[0]
        dp[0] = i
        for j, gc in enumerate(gt, start=1):
            cur = dp[j]
            dp[j] = min(dp[j] + 1, dp[j - 1] + 1, prev + (0 if pc == gc else 1))
            prev = cur
    return dp[len(gt)] / len(gt)


def main():
    import json

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    with open(CHARS_JSON_PATH, "r", encoding="utf-8") as f:
        all_chars = json.load(f)["chars"]
    idx_to_char = {i + 1: c for i, c in enumerate(all_chars)}

    model = CRNN(num_classes=len(all_chars)).to(device)
    model.load_state_dict(torch.load(str(BEST_MODEL_PATH), map_location=device))
    model.eval()

    with open(LABELS_CSV, "r", encoding="utf-8-sig") as f:
        rows = [(r["filename"], r["text"], r["line_count_guess"]) for r in csv.DictReader(f)]
    print(f"전체 {len(rows)}장 검사 시작...\n")

    candidates = []
    checked = 0
    with torch.no_grad():
        for filename, gt, line_count in rows:
            img = imread_unicode(CROPS_DIR / filename)
            if img is None:
                continue
            is_two = str(line_count) == "2"
            canvas = preprocess_for_model(img, is_two)
            tensor = torch.from_numpy(canvas).float().unsqueeze(0).unsqueeze(0).to(device) / 255.0
            logits = model(tensor)
            texts, confs = greedy_decode_with_conf(logits, idx_to_char)
            pred, conf = texts[0], confs[0]
            checked += 1

            if pred != gt and conf >= CONF_THRESHOLD:
                cer = char_error_rate(pred, gt)
                candidates.append((conf, cer, filename, gt, pred))

            if checked % 300 == 0:
                print(f"  ...{checked}/{len(rows)}장 검사됨")

    candidates.sort(key=lambda t: -t[0])  # 확신도 높은 순 = 라벨 오류일 가능성 높은 순

    with open(OUT_CSV, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(["filename", "label(현재)", "predicted(모델 예측)", "confidence", "cer", "new_label(정답이면 여기에 입력)"])
        for conf, cer, filename, gt, pred in candidates:
            writer.writerow([filename, gt, pred, f"{conf:.3f}", f"{cer:.2f}", ""])

    print(f"\n=== 완료 ===")
    print(f"검사한 이미지: {checked}장")
    print(f"라벨 오류 의심 후보(확신도 >= {CONF_THRESHOLD}): {len(candidates)}건")
    print(f"목록 저장: {OUT_CSV}")
    print(
        "\n엑셀로 열어서 confidence 높은 순서대로 위쪽부터 훑어보시고, predicted가 실제로 "
        "맞으면 new_label 칸에 정답을 적어주세요(비워두면 원래 label 그대로 유지됩니다). "
        "다 하신 뒤 apply_label_fixes.py를 실행하면 labels.csv에 반영됩니다."
    )


if __name__ == "__main__":
    main()
