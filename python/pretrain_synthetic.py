# -*- coding: utf-8 -*-
"""
pretrain_synthetic.py
====================================================
generate_synthetic_plates.py로 만든 합성 번호판 대량 데이터로 CRNN을 처음부터
사전학습시키는 스크립트 (3단계 중 첫 단계). 결과물(pretrained_recognizer.pth +
pretrained_chars.json)을 train_ocr_recognizer.py가 자동으로 감지해서, 그 가중치에서
이어서 실제 사진 1,765장으로 미세조정(fine-tuning)함.

실행 순서 (전체 파이프라인)
====================================================
    1) python generate_synthetic_plates.py   <- 합성 데이터 생성
    2) python pretrain_synthetic.py          <- 이 스크립트: 합성 데이터로 사전학습
    3) python train_ocr_recognizer.py        <- pretrained_*.pth를 자동 감지해서
                                                 실제 사진으로 이어서 미세조정

실행 방법
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    python pretrain_synthetic.py

합성 데이터가 실제 사진(1,961장)보다 훨씬 많아서(기본 20,000장) 학습 시간이
train_ocr_recognizer.py보다 오래 걸릴 수 있습니다 - GPU 기준 체감 1~2시간 내외로
예상하지만 PC마다 다르니, 크게 벗어나면 알려주세요.
"""
import csv
import json
import os
import random
from pathlib import Path

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import cv2
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader

PROJECT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
SYN_DATA_DIR = PROJECT_DIR / "ocr_train_data_synthetic"
SYN_CROPS_DIR = SYN_DATA_DIR / "crops"
SYN_LABELS_CSV = SYN_DATA_DIR / "labels.csv"

OUT_DIR = PROJECT_DIR / "ocr_model"
# 실제 학습 때 이미 만들어둔 문자 목록을 그대로 씀(새로 만들지 않음) - 합성 데이터가
# 실제보다 더 다양한 지역명을 쓰긴 하지만, generate_synthetic_plates.py가 애초에 이
# chars.json 안의 문자만 골라서 텍스트를 만들기 때문에 항상 부분집합임.
REAL_CHARS_JSON_PATH = OUT_DIR / "chars.json"

PRETRAINED_MODEL_PATH = OUT_DIR / "pretrained_recognizer.pth"
PRETRAINED_CHARS_PATH = OUT_DIR / "pretrained_chars.json"

IMG_H = 48
IMG_W = 320
BATCH_SIZE = 32  # 합성 데이터가 generate_synthetic_plates.py의 NUM_SAMPLES(기본 200장)
                  # 기준으로 크지 않으므로 배치도 실제 학습(train_ocr_recognizer.py)과 비슷하게 둠
EPOCHS = 60       # 데이터 양이 적어졌으니(실제 데이터의 약 10%) 같은 이미지를 여러 번
                   # 반복해서 봐야 해서 epoch을 늘림 - NUM_SAMPLES를 키우면 이 값은
                   # 다시 줄여도 됨(예: 몇 천 장 이상이면 15~20 정도로 충분)
LR = 3e-4
GRAD_CLIP_NORM = 5.0
WARMUP_STEPS = 100
VAL_RATIO = 0.1   # 사전학습 단계의 모니터링용 - 실제 성능 판단은 어차피 미세조정 후
                  # train_ocr_recognizer.py의 실제 검증셋(196장)으로 함
SEED = 42
PATIENCE = 15

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


class SyntheticOCRDataset(Dataset):
    """generate_synthetic_plates.py가 이미 이미지 자체에 회전/노이즈/블러 등을 강하게
    입혀놨으므로, 여기서 추가로 augment()를 또 걸지는 않음(이중 증강으로 과하게
    망가지는 것을 피함)."""

    def __init__(self, rows, char_to_idx):
        self.rows = rows
        self.char_to_idx = char_to_idx

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, idx):
        filename, text, line_count = self.rows[idx]
        img = imread_unicode(SYN_CROPS_DIR / filename)
        is_two_line = str(line_count) == "2"
        canvas = preprocess_for_model(img, is_two_line)
        tensor = torch.from_numpy(canvas).float().unsqueeze(0) / 255.0
        target = torch.tensor([self.char_to_idx[c] for c in text], dtype=torch.long)
        return tensor, target, len(text), text


def collate_fn(batch):
    imgs = torch.stack([b[0] for b in batch], dim=0)
    targets = torch.cat([b[1] for b in batch])
    target_lengths = torch.tensor([b[2] for b in batch], dtype=torch.long)
    texts = [b[3] for b in batch]
    return imgs, targets, target_lengths, texts


class CRNN(nn.Module):
    """train_ocr_recognizer.py / plate_ocr_crnn.py의 CRNN과 완전히 동일한 구조여야
    가중치를 그대로 이어받을 수 있음 - 하나라도 다르면 안 됨."""

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
        self.rnn = nn.LSTM(512, 256, num_layers=2, bidirectional=True, batch_first=True, dropout=0.3)
        self.dropout = nn.Dropout(0.3)
        self.fc = nn.Linear(512, num_classes + 1)

    def forward(self, x):
        feat = self.cnn(x)
        feat = self.height_pool(feat)
        feat = feat.squeeze(2)
        feat = feat.permute(0, 2, 1)
        out, _ = self.rnn(feat)
        out = self.dropout(out)
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
    print(f"사용 장치: {device}")

    if not SYN_LABELS_CSV.exists():
        print(f"[오류] {SYN_LABELS_CSV} 가 없습니다. 먼저 generate_synthetic_plates.py를 실행하세요.")
        return
    if not REAL_CHARS_JSON_PATH.exists():
        print(f"[오류] {REAL_CHARS_JSON_PATH} 가 없습니다. train_ocr_recognizer.py를 "
              "한 번이라도 먼저 실행해서 실제 문자 목록을 만들어둬야 합니다.")
        return

    with open(REAL_CHARS_JSON_PATH, "r", encoding="utf-8") as f:
        all_chars = json.load(f)["chars"]
    char_to_idx = {c: i + 1 for i, c in enumerate(all_chars)}
    idx_to_char = {i + 1: c for i, c in enumerate(all_chars)}
    print(f"문자 종류: {len(all_chars)}개 (실제 학습 때 쓴 목록 그대로 사용)")

    with open(SYN_LABELS_CSV, "r", encoding="utf-8-sig") as f:
        rows = [(r["filename"], r["text"], r["line_count_guess"]) for r in csv.DictReader(f)]

    # 방어적으로, 어휘에 없는 문자가 섞인 합성 샘플이 있으면 걸러냄(있으면 안 되지만
    # generate_synthetic_plates.py를 손으로 고쳐 쓴 경우 등 예외 상황 대비)
    vocab = set(all_chars)
    before = len(rows)
    rows = [r for r in rows if all(c in vocab for c in r[1])]
    if len(rows) < before:
        print(f"[안내] 어휘에 없는 문자가 섞인 {before - len(rows)}장은 제외하고 진행합니다.")
    print(f"합성 데이터: {len(rows)}장")

    random.shuffle(rows)
    n_val = max(1, int(len(rows) * VAL_RATIO))
    val_rows, train_rows = rows[:n_val], rows[n_val:]
    print(f"학습 {len(train_rows)}장 / 검증(모니터링용) {len(val_rows)}장")

    train_ds = SyntheticOCRDataset(train_rows, char_to_idx)
    val_ds = SyntheticOCRDataset(val_rows, char_to_idx)
    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True,
                               num_workers=0, collate_fn=collate_fn)
    val_loader = DataLoader(val_ds, batch_size=BATCH_SIZE, shuffle=False,
                             num_workers=0, collate_fn=collate_fn)

    model = CRNN(num_classes=len(all_chars)).to(device)
    criterion = nn.CTCLoss(blank=0, zero_infinity=True)
    optimizer = torch.optim.Adam(model.parameters(), lr=LR)
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(optimizer, mode="min", factor=0.5, patience=2)

    best_cer = float("inf")
    epochs_no_improve = 0
    global_step = 0

    for epoch in range(1, EPOCHS + 1):
        model.train()
        total_loss = 0.0
        for imgs, targets, target_lengths, _ in train_loader:
            imgs = imgs.to(device)
            targets = targets.to(device)
            target_lengths = target_lengths.to(device)

            global_step += 1
            if global_step <= WARMUP_STEPS:
                warmup_lr = LR * global_step / WARMUP_STEPS
                for group in optimizer.param_groups:
                    group["lr"] = warmup_lr

            optimizer.zero_grad()
            logits = model(imgs)
            log_probs = logits.log_softmax(2)
            input_lengths = torch.full((imgs.size(0),), log_probs.size(0), dtype=torch.long, device=device)
            loss = criterion(log_probs, targets, input_lengths, target_lengths)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), GRAD_CLIP_NORM)
            optimizer.step()
            total_loss += loss.item() * imgs.size(0)

        avg_loss = total_loss / max(1, len(train_ds))

        model.eval()
        exact_match, total_cer, n = 0, 0.0, 0
        with torch.no_grad():
            for imgs, _, _, texts in val_loader:
                imgs = imgs.to(device)
                logits = model(imgs)
                preds = greedy_decode(logits, idx_to_char)
                for pred, gt in zip(preds, texts):
                    if pred == gt:
                        exact_match += 1
                    total_cer += char_error_rate(pred, gt)
                    n += 1
        val_acc = exact_match / max(1, n)
        val_cer = total_cer / max(1, n)
        scheduler.step(val_cer)

        print(f"[epoch {epoch:02d}/{EPOCHS}] train_loss={avg_loss:.4f}  "
              f"val_exact_match={val_acc:.3f}  val_CER={val_cer:.3f}")

        if val_cer < best_cer - 1e-3:
            best_cer = val_cer
            epochs_no_improve = 0
            torch.save(model.state_dict(), PRETRAINED_MODEL_PATH)
            with open(PRETRAINED_CHARS_PATH, "w", encoding="utf-8") as f:
                json.dump({"chars": all_chars}, f, ensure_ascii=False, indent=2)
            print(f"  -> 최고 기록 갱신(CER 기준), 저장: {PRETRAINED_MODEL_PATH}")
        else:
            epochs_no_improve += 1
            if epochs_no_improve >= PATIENCE:
                print(f"\n{PATIENCE}epoch 동안 CER 개선 없어 조기 종료")
                break

    print("\n=== 사전학습 완료 ===")
    print(f"최고 검증(합성) CER: {best_cer:.3f}")
    print(f"저장: {PRETRAINED_MODEL_PATH}, {PRETRAINED_CHARS_PATH}")
    print("\n다음 단계: python train_ocr_recognizer.py 를 실행하면 이 가중치를 자동으로 "
          "감지해서 실제 사진 1,765장으로 이어서 미세조정합니다.")


if __name__ == "__main__":
    main()
