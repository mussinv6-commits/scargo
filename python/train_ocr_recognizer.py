# -*- coding: utf-8 -*-
"""
train_ocr_recognizer.py
====================================================
번호판 전용 글자 인식(OCR) 모델을 처음부터 학습시키는 스크립트 (2단계).

배경
====================================================
EasyOCR(범용 한국어 모델)은 파라미터 튜닝(해상도/패딩/선명화/beamsearch)을 다 해봐도
글자 인식률이 50~60%대에 머물렀습니다. 예전 트러블슈팅 과정에서도 같은 벽에 부딪혀서
"작은 획의 한글 차종기호를 EasyOCR이 구조적으로 못 읽는다"고 결론 낸 적이 있어서,
이번엔 번호판 글자만 보고 학습한 전용 인식 모델을 새로 만들어봅니다.

이 스크립트가 하는 일
====================================================
1) prepare_ocr_training_data.py가 만든 ocr_train_data/crops + labels.csv를 읽음
   (1,961장, 문자 종류 50개 - 숫자 10개 + 번호판에 실제 쓰이는 한글 40개)
2) 2줄 번호판(위: 지역명 등 / 아래: 나머지)은 위/아래 줄을 옆으로 이어붙여 한 줄짜리
   "읽기 순서" 이미지로 펴줌 (CRNN 계열 모델은 한 줄 텍스트를 왼쪽->오른쪽으로 읽는
   구조라, 2줄을 그대로 넣으면 두 줄이 섞여서 읽힘 - 정답 텍스트 자체도 지역명을 먼저
   쓰고 번호를 나중에 쓰는 순서라 이 펴기 순서와 일치함)
3) CRNN(CNN + BiLSTM + CTC) 구조의 작은 인식 모델을 처음부터 학습
   (EasyOCR을 대체하는 용도라 EasyOCR 내부 구조에 맞출 필요 없이 독립적으로 설계함 -
    나중에 앱에 넣을 때도 EasyOCR을 그대로 우회하고 이 모델을 직접 호출하면 됨)
4) 학습/검증 정확도(문자 단위 오류율 CER, 완전 일치율)를 매 epoch 출력하고,
   검증 완전 일치율이 가장 높았던 시점의 가중치를 best_recognizer.pth로 저장
5) 문자 목록도 chars.json으로 같이 저장 (나중에 앱에 통합할 때 디코딩에 필요)

====================================================
실행 방법 (Anaconda Prompt) - GPU 있는 환경(base) 권장, 없으면 자동으로 CPU로 돔
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    python train_ocr_recognizer.py

1,961장 규모면 GPU 기준 대략 30분~1시간 정도로 예상하지만(정확한 시간은 PC마다 다름),
이 예상이 크게 빗나가면(예: 몇 시간씩 걸리거나 CPU로만 돌면) 알려주세요 - epoch 수나
배치 크기를 조정하겠습니다. 학습 중 epoch마다 나오는 정확도 로그를 그대로 캡처해서
보내주시면 진행 상황을 같이 볼 수 있습니다.
"""
import csv
import json
import os
import random
import shutil
from datetime import datetime
from pathlib import Path

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import cv2
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader

PROJECT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
DATA_DIR = PROJECT_DIR / "ocr_train_data"
CROPS_DIR = DATA_DIR / "crops"
LABELS_CSV = DATA_DIR / "labels.csv"

OUT_DIR = PROJECT_DIR / "ocr_model"
BEST_MODEL_PATH = OUT_DIR / "best_recognizer.pth"
CHARS_JSON_PATH = OUT_DIR / "chars.json"

# YOLO 검출 실험은 experiments_log.csv로 설정값·결과를 계속 기록해왔는데, OCR 인식
# 모델 쪽은 이런 기록이 없어서 "그때 정확히 어떤 설정으로 73.5%가 나왔는지"를 나중에
# 알 수 없는 문제가 있었음 - 같은 일이 반복되지 않도록 매 실행마다 자동으로 한 줄씩 남김.
OCR_LOG_CSV = PROJECT_DIR / "ocr_experiments_log.csv"

# generate_synthetic_plates.py + pretrain_synthetic.py로 미리 사전학습을 해뒀다면
# 이 두 파일이 생기는데, 있으면 처음부터(random init) 학습하는 대신 이 가중치에서
# 이어서 미세조정(fine-tuning)함 - 합성 데이터로 다양한 지역명 조합을 먼저 본 상태로
# 시작하면, 실제 사진 1,765장만으로 처음부터 학습할 때보다 지역명을 통째로 헷갈리는
# 오류가 줄어들 것으로 기대함. 두 파일이 없으면 예전과 완전히 동일하게 동작함.
PRETRAINED_MODEL_PATH = OUT_DIR / "pretrained_recognizer.pth"
PRETRAINED_CHARS_PATH = OUT_DIR / "pretrained_chars.json"

IMG_H = 48
IMG_W = 320
BATCH_SIZE = 32
EPOCHS = 150  # 80epoch까지도 조기종료 없이 끝까지 다 쓰면서 계속 좋아지고 있었어서
              # (마지막까지 loss/CER이 내려가는 중) 더 오를 여지를 보려고 크게 늘림
LR = 3e-4          # 1e-3은 무작위 초기화 상태의 CRNN+CTC에 너무 높아 "제일 흔한 글자 조합만
                    # 찍어내는" 붕괴(collapse)에 빠지기 쉬움 - 낮춰서 안정적으로 수렴하도록 함
GRAD_CLIP_NORM = 5.0  # gradient clipping - 학습 초반 기울기 폭주로 인한 붕괴를 막는 표준 처방
WARMUP_STEPS = 100    # 처음 100 step 동안 LR을 0 -> LR까지 선형으로 서서히 올림 - gradient
                      # clipping과 함께 학습 극초반 붕괴 재발을 막는 표준 처방 (약 2epoch 분량)
VAL_RATIO = 0.1
SEED = 42
PATIENCE = 20  # 이 epoch 수만큼 검증 정확도 개선이 없으면 조기 종료 (12->20: 아직 개선 중일 때
               # 너무 일찍 끊기지 않도록 여유를 더 줌)

random.seed(SEED)
np.random.seed(SEED)
torch.manual_seed(SEED)


def imread_unicode(path: Path):
    data = np.fromfile(str(path), dtype=np.uint8)
    if data.size == 0:
        return None
    return cv2.imdecode(data, cv2.IMREAD_COLOR)


def unwrap_two_line(gray: np.ndarray):
    """2줄 crop을 위/아래로 나눠서 옆으로 이어붙임 (정답 텍스트의 "지역명 먼저, 번호
    나중" 순서와 맞추기 위해 위쪽 줄을 왼쪽에 배치). 수평 투영(행별 글자 픽셀 수)에서
    가장 여백이 많은 행을 두 줄 사이 경계로 추정함."""
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
    """crop 이미지 한 장을 모델 입력 크기(IMG_H x IMG_W, 그레이스케일)로 정규화.
    2줄이면 먼저 한 줄로 펴고, 그 다음 공통으로 가로세로 비율 유지 확대 + 패딩."""
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


def augment(gray: np.ndarray) -> np.ndarray:
    """학습용 데이터에만 적용하는 가벼운 증강 (회전/원근왜곡/명암/노이즈/블러) - 데이터가
    2천장 이내로 많지 않아서 과적합을 줄이고, 실제 촬영 사진의 열화(비스듬한 각도, 흔들림)를
    흉내내기 위함."""
    h, w = gray.shape[:2]
    if random.random() < 0.5:
        angle = random.uniform(-3, 3)
        M = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1.0)
        gray = cv2.warpAffine(gray, M, (w, h), borderValue=255)
    if random.random() < 0.35:
        # 약한 원근 왜곡 - 정면이 아니라 비스듬히 찍힌 번호판을 흉내냄
        jitter = max(2, int(min(h, w) * 0.05))
        src = np.float32([[0, 0], [w, 0], [0, h], [w, h]])
        dst = np.float32([
            [random.uniform(0, jitter), random.uniform(0, jitter)],
            [w - random.uniform(0, jitter), random.uniform(0, jitter)],
            [random.uniform(0, jitter), h - random.uniform(0, jitter)],
            [w - random.uniform(0, jitter), h - random.uniform(0, jitter)],
        ])
        Mp = cv2.getPerspectiveTransform(src, dst)
        gray = cv2.warpPerspective(gray, Mp, (w, h), borderValue=255)
    if random.random() < 0.5:
        alpha = random.uniform(0.7, 1.3)  # 대비
        beta = random.uniform(-20, 20)    # 밝기
        gray = np.clip(gray.astype(np.float32) * alpha + beta, 0, 255).astype(np.uint8)
    if random.random() < 0.3:
        noise = np.random.normal(0, 8, gray.shape).astype(np.float32)
        gray = np.clip(gray.astype(np.float32) + noise, 0, 255).astype(np.uint8)
    if random.random() < 0.25:
        # 약한 블러 - 흔들리거나 초점이 살짝 나간 실사진을 흉내냄
        k = random.choice([3, 5])
        gray = cv2.GaussianBlur(gray, (k, k), 0)
    return gray


class PlateOCRDataset(Dataset):
    def __init__(self, rows, char_to_idx, train: bool):
        self.rows = rows
        self.char_to_idx = char_to_idx
        self.train = train

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, idx):
        filename, text, line_count = self.rows[idx]
        img = imread_unicode(CROPS_DIR / filename)
        is_two_line = str(line_count) == "2"
        canvas = preprocess_for_model(img, is_two_line)
        if self.train:
            canvas = augment(canvas)
        tensor = torch.from_numpy(canvas).float().unsqueeze(0) / 255.0  # (1, H, W)
        target = torch.tensor([self.char_to_idx[c] for c in text], dtype=torch.long)
        return tensor, target, len(text), text


def collate_fn(batch):
    imgs = torch.stack([b[0] for b in batch], dim=0)
    targets = torch.cat([b[1] for b in batch])
    target_lengths = torch.tensor([b[2] for b in batch], dtype=torch.long)
    texts = [b[3] for b in batch]
    return imgs, targets, target_lengths, texts


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
        # 높이를 정확히 1로 눌러줌 (중간 conv/pool 단계의 정확한 크기 계산에 의존하지
        # 않도록 adaptive pooling을 씀 - 입력 크기를 바꿔도 항상 안전하게 동작함)
        self.height_pool = nn.AdaptiveAvgPool2d((1, None))
        # dropout=0.3: 두 LSTM 레이어 사이에 드롭아웃 적용 - 학습 데이터가 1,765장으로
        # 적은 편이라 과적합(=검증에서만 성능이 안 나오는 것) 위험을 줄이기 위함
        self.rnn = nn.LSTM(512, 256, num_layers=2, bidirectional=True, batch_first=True, dropout=0.3)
        self.dropout = nn.Dropout(0.3)
        self.fc = nn.Linear(512, num_classes + 1)  # +1 = CTC blank(index 0)

    def forward(self, x):
        feat = self.cnn(x)              # (B, 512, H', W')
        feat = self.height_pool(feat)   # (B, 512, 1, W')
        feat = feat.squeeze(2)          # (B, 512, W')
        feat = feat.permute(0, 2, 1)    # (B, W', 512)
        out, _ = self.rnn(feat)         # (B, W', 512)
        out = self.dropout(out)
        out = self.fc(out)              # (B, W', num_classes+1)
        return out.permute(1, 0, 2)     # (W', B, num_classes+1) - CTCLoss가 요구하는 형태


def greedy_decode(logits, idx_to_char):
    """logits: (T, B, C) -> 배치별 디코딩된 문자열 리스트."""
    pred = logits.argmax(dim=2).permute(1, 0)  # (B, T)
    texts = []
    for seq in pred:
        chars = []
        prev = -1
        for p in seq.tolist():
            if p != prev and p != 0:  # 0 = blank
                chars.append(idx_to_char[p])
            prev = p
        texts.append("".join(chars))
    return texts


def char_error_rate(pred: str, gt: str) -> float:
    """편집거리 기반 문자 오류율 (Levenshtein distance / 정답 길이)."""
    if len(gt) == 0:
        return 0.0 if len(pred) == 0 else 1.0
    dp = list(range(len(gt) + 1))
    for i, pc in enumerate(pred, start=1):
        prev = dp[0]
        dp[0] = i
        for j, gc in enumerate(gt, start=1):
            cur = dp[j]
            dp[j] = min(
                dp[j] + 1,       # 삭제
                dp[j - 1] + 1,   # 삽입
                prev + (0 if pc == gc else 1),  # 치환/일치
            )
            prev = cur
    return dp[len(gt)] / len(gt)


def main():
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"사용 장치: {device}")

    if not LABELS_CSV.exists():
        print(f"[오류] {LABELS_CSV} 를 찾을 수 없습니다. prepare_ocr_training_data.py를 먼저 실행하세요.")
        return

    with open(LABELS_CSV, "r", encoding="utf-8-sig") as f:
        rows = [(r["filename"], r["text"], r["line_count_guess"]) for r in csv.DictReader(f)]
    print(f"전체 데이터: {len(rows)}장")

    # 사전학습된 가중치를 이어받으려면, 그때 썼던 문자 목록(순서까지)을 그대로 써야
    # 각 문자의 인덱스가 어긋나지 않음 - 그래서 이 경우엔 labels.csv에서 새로 문자
    # 목록을 만들지 않고, 사전학습 때 저장해둔 목록을 그대로 불러옴.
    use_pretrained = PRETRAINED_MODEL_PATH.exists() and PRETRAINED_CHARS_PATH.exists()
    if use_pretrained:
        with open(PRETRAINED_CHARS_PATH, "r", encoding="utf-8") as f:
            all_chars = json.load(f)["chars"]
        real_chars = {c for _, text, _ in rows for c in text}
        missing = sorted(real_chars - set(all_chars))
        if missing:
            print(
                f"[경고] 실제 라벨에 사전학습 어휘에 없는 문자가 있어({missing}) "
                "가중치를 이어받을 수 없습니다. 처음부터(random init) 학습합니다."
            )
            use_pretrained = False

    if not use_pretrained:
        all_chars = sorted({c for _, text, _ in rows for c in text})

    char_to_idx = {c: i + 1 for i, c in enumerate(all_chars)}  # 0은 CTC blank로 예약
    idx_to_char = {i + 1: c for i, c in enumerate(all_chars)}
    print(f"문자 종류: {len(all_chars)}개 -> {''.join(all_chars)}")

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    # 이번 학습이 이전 학습보다 더 나쁜 모델을 만들어도, best_recognizer.pth는 CER이
    # "이번 실행 안에서" 개선될 때마다 무조건 덮어쓰기 때문에 이전에 저장해둔 더 좋은
    # 모델이 통째로 사라질 수 있음 - 실제로 이런 일이 있었어서, 학습을 새로 시작할
    # 때마다 기존 파일을 타임스탬프 붙여 항상 백업해둠 (필요 없어지면 나중에 직접 지우면 됨).
    if BEST_MODEL_PATH.exists():
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        backup_model = OUT_DIR / f"best_recognizer_backup_{ts}.pth"
        shutil.copy(BEST_MODEL_PATH, backup_model)
        print(f"[백업] 기존 best_recognizer.pth를 {backup_model} 로 백업했습니다 "
              f"(이번 학습이 더 나쁘게 끝나도 이 파일로 되돌릴 수 있습니다).")
        if CHARS_JSON_PATH.exists():
            shutil.copy(CHARS_JSON_PATH, OUT_DIR / f"chars_backup_{ts}.json")

    with open(CHARS_JSON_PATH, "w", encoding="utf-8") as f:
        json.dump({"chars": all_chars}, f, ensure_ascii=False, indent=2)

    random.shuffle(rows)
    n_val = max(1, int(len(rows) * VAL_RATIO))
    val_rows, train_rows = rows[:n_val], rows[n_val:]
    print(f"학습 {len(train_rows)}장 / 검증 {len(val_rows)}장")

    train_ds = PlateOCRDataset(train_rows, char_to_idx, train=True)
    val_ds = PlateOCRDataset(val_rows, char_to_idx, train=False)

    # label_audit_candidates.csv를 눈으로 검수하며 확인한, 모델이 체계적으로 헷갈리는
    # 한글 글자쌍(생김새가 비슷함: 사/자, 바/아, 그리고 같은 패턴으로 보이는 보/모, 고/노,
    # 로/모). 새 이미지를 추가하는 대신, 이 글자가 들어간 "기존" 학습 샘플을 매 epoch
    # 조금 더 자주 뽑히게만 해서(오버샘플링) 모델이 이 글자들을 더 잘 구분하도록 유도함 -
    # 데이터 자체는 한 장도 늘리지 않음. 배율은 공격적으로 주면 오히려 다른 글자를
    # 잊어버릴 수 있어 1.5배로 보수적으로 설정.
    CONFUSABLE_CHARS = set("사자바아보모고노로")
    CONFUSABLE_WEIGHT = 1.5
    sample_weights = [
        CONFUSABLE_WEIGHT if any(c in CONFUSABLE_CHARS for c in text) else 1.0
        for _, text, _ in train_rows
    ]
    n_boosted = sum(1 for w in sample_weights if w > 1.0)
    print(f"글자쌍 오버샘플링: 학습 {len(train_rows)}장 중 {n_boosted}장에 가중치 "
          f"{CONFUSABLE_WEIGHT}x 적용 (사/자/바/아/보/모/고/노/로 포함)")
    train_sampler = torch.utils.data.WeightedRandomSampler(
        sample_weights, num_samples=len(sample_weights), replacement=True
    )

    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, sampler=train_sampler,
                               num_workers=0, collate_fn=collate_fn)
    val_loader = DataLoader(val_ds, batch_size=BATCH_SIZE, shuffle=False,
                             num_workers=0, collate_fn=collate_fn)

    model = CRNN(num_classes=len(all_chars)).to(device)
    if use_pretrained:
        state_dict = torch.load(str(PRETRAINED_MODEL_PATH), map_location=device)
        model.load_state_dict(state_dict)
        # 이미 합성 데이터로 어느 정도 학습된 상태에서 시작하므로, 처음부터 학습할 때(LR)보다
        # 낮은 LR로 미세조정 - 너무 크게 흔들어서 사전학습 때 배운 걸 잊어버리지 않도록 함
        base_lr = LR * 0.5
        print(f"사전학습 가중치를 이어받아 미세조정합니다: {PRETRAINED_MODEL_PATH} (LR={base_lr:.1e})")
    else:
        base_lr = LR
    criterion = nn.CTCLoss(blank=0, zero_infinity=True)
    # AdamW(weight_decay=1e-4)로 바꿔서 돌려본 실행이 오버샘플링과 동시에 들어가는 바람에
    # 뭐가 원인인지 못 갈랐음(CER 0.101->0.109로 오히려 나빠짐) - 변수를 하나씩 분리해서
    # 보기 위해 이번엔 weight_decay를 다시 0으로 되돌리고 오버샘플링만 남겨서 실행함.
    # (오버샘플링만으로 결과가 괜찮으면 weight_decay가 범인, 이것도 나쁘면 오버샘플링
    # 자체나 다른 요인을 의심해야 함)
    WEIGHT_DECAY = 0.0
    optimizer = torch.optim.AdamW(model.parameters(), lr=base_lr, weight_decay=WEIGHT_DECAY)
    # 저장/조기종료 기준을 val_CER(낮을수록 좋음)로 맞췄으므로 스케줄러도 동일하게 mode="min"
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(optimizer, mode="min", factor=0.5, patience=5)

    best_cer = float("inf")   # 체크포인트 저장/조기종료 판단 기준 (문자 단위 오류율)
    best_exact_seen = -1.0    # 참고용: 전체 epoch 중 완전 일치율 최댓값 (저장되는 가중치와 다른 epoch일 수 있음)
    best_exact_at_save = -1.0  # 실제로 저장된 체크포인트(=best_cer 기준)의 완전 일치율
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
                warmup_lr = base_lr * global_step / WARMUP_STEPS
                for group in optimizer.param_groups:
                    group["lr"] = warmup_lr

            optimizer.zero_grad()
            logits = model(imgs)  # (T, B, C)
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
        sample_preds = []  # (pred, gt) 몇 개만 눈으로 확인용으로 남김 - 모델이 이미지 무시하고
                            # 항상 비슷한 문자열만 찍어내는 "붕괴" 여부를 매 epoch 바로 확인 가능
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
                    if len(sample_preds) < 3:
                        sample_preds.append((pred, gt))
        val_acc = exact_match / max(1, n)
        val_cer = total_cer / max(1, n)
        scheduler.step(val_cer)

        if val_acc > best_exact_seen:
            best_exact_seen = val_acc

        print(f"[epoch {epoch:03d}/{EPOCHS}] train_loss={avg_loss:.4f}  "
              f"val_exact_match={val_acc:.3f}  val_CER={val_cer:.3f}")
        sample_str = " | ".join(f"{gt}->{pred or '(빈값)'}" for pred, gt in sample_preds)
        print(f"    예시: {sample_str}")
        # 서로 다른 정답 3개에 대한 예측이 전부 똑같으면 이미지 무시하고 찍어내는 "붕괴" 의심
        if len({p for p, _ in sample_preds}) == 1 and len({g for _, g in sample_preds}) > 1:
            print("    [경고] 서로 다른 입력인데 예측이 전부 동일 - 붕괴(collapse) 의심")

        # CER이 조금이라도(0.001 이상) 좋아졌을 때만 갱신으로 인정 - 부동소수점 오차로
        # 동률인데 "개선"으로 잘못 판정해 조기종료 카운터가 리셋되는 걸 방지
        if val_cer < best_cer - 1e-3:
            best_cer = val_cer
            best_exact_at_save = val_acc
            epochs_no_improve = 0
            torch.save(model.state_dict(), BEST_MODEL_PATH)
            print(f"  -> 최고 기록 갱신(CER 기준), 저장: {BEST_MODEL_PATH} "
                  f"(이 체크포인트의 완전일치율={val_acc:.3f})")
        else:
            epochs_no_improve += 1
            if epochs_no_improve >= PATIENCE:
                print(f"\n{PATIENCE}epoch 동안 CER 개선 없어 조기 종료")
                break

    print("\n=== 학습 완료 ===")
    print(f"최고 검증 CER(낮을수록 좋음): {best_cer:.3f}")
    print(f"저장된 체크포인트의 완전 일치율(={BEST_MODEL_PATH.name}에 실제로 들어있는 값): {best_exact_at_save:.3f}")
    print(f"(참고) 학습 중 어느 epoch에서든 관측된 최고 완전 일치율: {best_exact_seen:.3f} "
          f"- 이 값은 CER이 더 안 좋았던 다른 epoch에서 나온 것일 수 있어 저장 모델과 다를 수 있음")
    print(f"모델: {BEST_MODEL_PATH}")
    print(f"문자 목록: {CHARS_JSON_PATH}")

    # 이번 실행 설정/결과를 ocr_experiments_log.csv에 한 줄 자동 기록 (없으면 헤더부터 생성)
    log_is_new = not OCR_LOG_CSV.exists()
    with open(OCR_LOG_CSV, "a", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        if log_is_new:
            writer.writerow([
                "date", "use_pretrained", "epochs_run", "epochs_max", "batch_size", "lr",
                "optimizer", "weight_decay", "patience", "confusable_oversample",
                "train_images", "val_images", "best_cer", "best_exact_at_save", "note",
            ])
        writer.writerow([
            datetime.now().strftime("%Y-%m-%d %H:%M"), use_pretrained, epoch, EPOCHS,
            BATCH_SIZE, base_lr, "AdamW", WEIGHT_DECAY, PATIENCE, CONFUSABLE_WEIGHT,
            len(train_rows), len(val_rows), f"{best_cer:.4f}", f"{best_exact_at_save:.4f}", "",
        ])
    print(f"\n실행 설정/결과를 {OCR_LOG_CSV}에 기록했습니다 (note 칸은 나중에 직접 채워도 됩니다).")
    print("이 두 파일을 보고 다음 단계(앱에 통합)를 진행하겠습니다.")


if __name__ == "__main__":  # Windows에서 필수
    main()
