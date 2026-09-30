# -*- coding: utf-8 -*-
"""
diagnose_ocr_predictions.py
====================================================
best_recognizer.pth(최고 CER 0.771)가 왜 여전히 정확도가 낮은지, 숫자(CER)만으로는
안 보이는 실제 실패 패턴을 눈으로 확인하기 위한 진단 스크립트.

하는 일
====================================================
1) 학습된 모델을 불러와 검증셋 전체에 대해 예측을 돌림
2) 1줄 번호판 / 2줄 번호판(unwrap_two_line으로 편 것)을 나눠서 각각 평균 CER을 따로 계산
   -> 만약 2줄 쪽 CER이 1줄 쪽보다 훨씬 나쁘면, "2줄 -> 1줄 펴기" 로직이 실제 이미지에서
      자주 잘못 잘리고 있다는 뜻 (Otsu 이진화가 지저분한 실사진에서 잘 안 먹는 경우가 흔함)
3) 예측-정답 쌍을 20개(1줄 10개 + 2줄 10개, 랜덤) 화면에 그대로 출력
   -> 예측이 아예 텅 비어있는지, 글자 수는 맞는데 다른 글자로 틀리는지, 순서가 뒤섞이는지
      눈으로 바로 판단 가능

====================================================
실행 방법
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    python diagnose_ocr_predictions.py

출력되는 내용 전체를 그대로 캡처해서 보내주세요.
"""
import csv
import json
import random
from pathlib import Path

import cv2
import numpy as np
import torch
import torch.nn as nn

PROJECT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
DATA_DIR = PROJECT_DIR / "ocr_train_data"
CROPS_DIR = DATA_DIR / "crops"
LABELS_CSV = DATA_DIR / "labels.csv"

OUT_DIR = PROJECT_DIR / "ocr_model"
BEST_MODEL_PATH = OUT_DIR / "best_recognizer.pth"
CHARS_JSON_PATH = OUT_DIR / "chars.json"

IMG_H = 48
IMG_W = 320
VAL_RATIO = 0.1
SEED = 42  # 학습 스크립트와 동일한 시드 -> 동일한 학습/검증 분할 재현

random.seed(SEED)
np.random.seed(SEED)
torch.manual_seed(SEED)


def imread_unicode(path: Path):
    data = np.fromfile(str(path), dtype=np.uint8)
    if data.size == 0:
        return None
    return cv2.imdecode(data, cv2.IMREAD_COLOR)


def unwrap_two_line(gray: np.ndarray):
    h, w = gray.shape[:2]
    _, mask = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    row_sums = mask.sum(axis=1)
    lo, hi = int(h * 0.3), int(h * 0.7)
    if hi <= lo:
        split = h // 2
    else:
        split = lo + int(np.argmin(row_sums[lo:hi]))
    split = max(4, min(h - 4, split))
    top, bottom = gray[:split, :], gray[split:, :]

    def resize_to_h(band, target_h):
        bh, bw = band.shape[:2]
        if bh == 0 or bw == 0:
            return np.zeros((target_h, 1), dtype=np.uint8)
        scale = target_h / bh
        new_w = max(1, int(bw * scale))
        return cv2.resize(band, (new_w, target_h), interpolation=cv2.INTER_CUBIC)

    band_h = 40
    top_r = resize_to_h(top, band_h)
    bottom_r = resize_to_h(bottom, band_h)
    gap = np.full((band_h, 4), 255, dtype=np.uint8)
    return np.hstack([top_r, gap, bottom_r])


def preprocess_for_model(img_bgr: np.ndarray, is_two_line: bool) -> np.ndarray:
    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
    if is_two_line:
        gray = unwrap_two_line(gray)
    h, w = gray.shape[:2]
    scale = IMG_H / max(h, 1)
    new_w = min(IMG_W, max(1, int(w * scale)))
    resized = cv2.resize(gray, (new_w, IMG_H), interpolation=cv2.INTER_CUBIC)
    canvas = np.full((IMG_H, IMG_W), 255, dtype=np.uint8)
    canvas[:, :new_w] = resized
    return canvas


class CRNN(nn.Module):
    def __init__(self, num_classes: int):
        super().__init__()
        self.cnn = nn.Sequential(
            nn.Conv2d(1, 64, 3, 1, 1), nn.BatchNorm2d(64), nn.ReLU(inplace=True),
            nn.MaxPool2d(2, 2),
            nn.Conv2d(64, 128, 3, 1, 1), nn.BatchNorm2d(128), nn.ReLU(inplace=True),
            nn.MaxPool2d(2, 2),
            nn.Conv2d(128, 256, 3, 1, 1), nn.BatchNorm2d(256), nn.ReLU(inplace=True),
            nn.Conv2d(256, 256, 3, 1, 1), nn.BatchNorm2d(256), nn.ReLU(inplace=True),
            nn.MaxPool2d((2, 1), (2, 1)),
            nn.Conv2d(256, 512, 3, 1, 1), nn.BatchNorm2d(512), nn.ReLU(inplace=True),
            nn.Conv2d(512, 512, 3, 1, 1), nn.BatchNorm2d(512), nn.ReLU(inplace=True),
            nn.MaxPool2d((2, 1), (2, 1)),
        )
        self.height_pool = nn.AdaptiveAvgPool2d((1, None))
        self.rnn = nn.LSTM(512, 256, num_layers=2, bidirectional=True, batch_first=True)
        self.fc = nn.Linear(512, num_classes + 1)

    def forward(self, x):
        feat = self.cnn(x)
        feat = self.height_pool(feat)
        feat = feat.squeeze(2)
        feat = feat.permute(0, 2, 1)
        out, _ = self.rnn(feat)
        out = self.fc(out)
        return out.permute(1, 0, 2)


def greedy_decode(logits, idx_to_char):
    pred = logits.argmax(dim=2).permute(1, 0)
    texts = []
    for seq in pred:
        chars = []
        prev = -1
        for p in seq.tolist():
            if p != prev and p != 0:
                chars.append(idx_to_char[p])
            prev = p
        texts.append("".join(chars))
    return texts


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
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    with open(CHARS_JSON_PATH, "r", encoding="utf-8") as f:
        all_chars = json.load(f)["chars"]
    idx_to_char = {i + 1: c for i, c in enumerate(all_chars)}

    with open(LABELS_CSV, "r", encoding="utf-8-sig") as f:
        rows = [(r["filename"], r["text"], r["line_count_guess"]) for r in csv.DictReader(f)]

    # 학습 스크립트와 동일한 시드로 shuffle -> 그때의 검증 196장을 그대로 재현
    random.shuffle(rows)
    n_val = max(1, int(len(rows) * VAL_RATIO))
    val_rows = rows[:n_val]
    print(f"검증셋 재현: {len(val_rows)}장\n")

    model = CRNN(num_classes=len(all_chars)).to(device)
    model.load_state_dict(torch.load(BEST_MODEL_PATH, map_location=device))
    model.eval()

    line1_cer, line1_n = 0.0, 0
    line2_cer, line2_n = 0.0, 0
    examples_1, examples_2 = [], []

    with torch.no_grad():
        for filename, text, line_count in val_rows:
            img = imread_unicode(CROPS_DIR / filename)
            if img is None:
                continue
            is_two = str(line_count) == "2"
            canvas = preprocess_for_model(img, is_two)
            tensor = torch.from_numpy(canvas).float().unsqueeze(0).unsqueeze(0).to(device) / 255.0
            logits = model(tensor)
            pred = greedy_decode(logits, idx_to_char)[0]
            cer = char_error_rate(pred, text)

            if is_two:
                line2_cer += cer
                line2_n += 1
                if len(examples_2) < 10:
                    examples_2.append((filename, text, pred, cer))
            else:
                line1_cer += cer
                line1_n += 1
                if len(examples_1) < 10:
                    examples_1.append((filename, text, pred, cer))

    print("=== 1줄 번호판 vs 2줄 번호판 CER 비교 ===")
    if line1_n:
        print(f"1줄: {line1_n}장, 평균 CER {line1_cer / line1_n:.3f}")
    else:
        print("1줄: 검증셋에 없음")
    if line2_n:
        print(f"2줄: {line2_n}장, 평균 CER {line2_cer / line2_n:.3f}")
    else:
        print("2줄: 검증셋에 없음")

    print("\n=== 1줄 번호판 예측 샘플 ===")
    for filename, gt, pred, cer in examples_1:
        print(f"  정답: {gt:12s} 예측: {pred:12s} CER: {cer:.2f}   ({filename})")

    print("\n=== 2줄 번호판 예측 샘플 ===")
    for filename, gt, pred, cer in examples_2:
        print(f"  정답: {gt:12s} 예측: {pred:12s} CER: {cer:.2f}   ({filename})")


if __name__ == "__main__":
    main()
