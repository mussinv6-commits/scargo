# -*- coding: utf-8 -*-
"""
train_ocr_recognizer_attn.py
====================================================
[실험판] train_ocr_recognizer.py(현재 검증 CER 0.074 / 완전 일치율 74.9%,
AdamW+weight_decay=0+글자쌍 오버샘플링+라벨정제 - 지금 앱이 실제로 쓰는 모델)의
CRNN 구조에 self-attention 정제 레이어를 추가해보는 실험 버전입니다.

*** 안전장치: 이 파일은 기존 train_ocr_recognizer.py를 전혀 건드리지 않고, 완전히
별도의 출력 폴더(ocr_model_attn/)에 저장합니다. 지금 앱(plate_detector_gui.py)이
쓰는 ocr_model/best_recognizer.pth, plate_ocr_crnn.py는 이 스크립트를 몇 번을 돌려도
절대 영향받지 않습니다 - 이 실험이 실패해도(오히려 나빠져도) 지금 잘 되는 시스템은
그대로 안전합니다. ***

무엇이 다른가
====================================================
CNN + BiLSTM(2층) 뒤에, self-attention 정제 레이어를 하나 추가했습니다.
- BiLSTM은 그대로 두고(이미 검증된 부분), 그 출력을 self-attention이 한 번 더
  훑으면서 전체 시퀀스(지역명+코드+글자+일련번호 전체 맥락)를 감안해 각 위치의
  표현을 보강합니다.
- 잔차 연결(residual)을 써서, attention이 학습 초반에 별 도움이 안 되더라도
  최소한 BiLSTM만 썼을 때 성능(0.074/74.9%)을 까먹지 않도록 설계했습니다.
- 다른 설정(옵티마이저, weight_decay, 오버샘플링, 학습률, epoch 수, 데이터)은
  기존과 완전히 동일하게 유지했습니다 - 구조 하나만 바꿔서 이게 실제로 도움이
  되는지 아닌지를 깨끗하게(변수 하나만 바꿔서) 비교하기 위함입니다.

주의: state_dict 구조 자체가 기존과 달라서(attention 레이어가 추가됨),
여기서 저장되는 best_recognizer.pth는 plate_ocr_crnn.py(기존)로는 못 불러옵니다.
이 실험판 모델을 쓰려면 plate_ocr_crnn_attn.py(같이 만든 짝 파일)로 불러와야 합니다.

실행 방법
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    python train_ocr_recognizer_attn.py

끝나면 verify_integration_attn.py로 검증하고, 기존(0.074/74.9%)보다 좋으면
그때 앱에 통합할지 결정하면 됩니다. 결과 로그를 그대로 캡처해서 보내주세요.
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

# *** 기존 ocr_model/ 이 아니라 별도 폴더에 저장 - 지금 앱이 쓰는 파일은 절대 안 건드림 ***
OUT_DIR = PROJECT_DIR / "ocr_model_attn"
BEST_MODEL_PATH = OUT_DIR / "best_recognizer_attn.pth"
CHARS_JSON_PATH = OUT_DIR / "chars_attn.json"

# 실험 결과도 기존 로그와 섞이지 않게 별도 파일에 기록 (구조가 달라서 같은 표에 섞으면
# 나중에 헷갈림)
OCR_LOG_CSV = PROJECT_DIR / "ocr_experiments_log_attn.csv"

# 이 실험판은 처음부터 새로 시작 - 기존 ocr_model/의 사전학습 파일과는 구조가 달라
# 이어받을 수 없고, 애초에 사전학습이 오히려 성능을 깎아먹는다는 게 이미 확인됐으므로
# (0.101 -> 실사진만으로 처음부터 한 게 더 좋았음) 사전학습 경로 자체를 없앴습니다.

IMG_H = 48
IMG_W = 320
BATCH_SIZE = 32
EPOCHS = 150
LR = 3e-4
GRAD_CLIP_NORM = 5.0
WARMUP_STEPS = 100
VAL_RATIO = 0.1
SEED = 42
PATIENCE = 20
WEIGHT_DECAY = 0.0  # 지금까지 확인된 최선의 설정 그대로 유지 (구조만 바꿔서 비교하기 위함)

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


def _motion_blur(gray: np.ndarray, max_len: int = 9) -> np.ndarray:
    """달리는 트럭을 사진/영상 프레임으로 찍을 때 생기는 방향성 블러(가우시안 블러와는
    질감이 다름 - 한쪽으로 쭉 늘어지는 흐림)를 흉내냄. 특히 영상 프레임 처리(속도
    우선이라 TTA를 못 씀)에서 자주 겪을 상황이라 학습 단계에서 미리 노출시켜 둠.
    번호판은 가로로 길고 차량은 좌우로 지나가는 경우가 많아 커널 각도를 가로 위주
    (±15도 이내)로 제한함."""
    length = random.choice([3, 5, 7, max_len])
    angle = random.uniform(-15, 15)
    kernel = np.zeros((length, length), dtype=np.float32)
    kernel[length // 2, :] = 1.0
    rot = cv2.getRotationMatrix2D((length / 2, length / 2), angle, 1.0)
    kernel = cv2.warpAffine(kernel, rot, (length, length))
    total = kernel.sum()
    if total > 1e-6:
        kernel /= total
    return cv2.filter2D(gray, -1, kernel)


def _random_erase(gray: np.ndarray, max_patches: int = 2) -> np.ndarray:
    """실제 사진에서는 나뭇잎/먼지/반사광(글레어) 등이 번호판 글자 일부를 가리는 경우가
    흔한데(예: 나뭇잎에 가려진 트럭 번호판 사례), 기존 학습 crop들은 대부분 깨끗하게
    찍혀서 모델이 이런 가림을 겪어본 적이 없었음. 무작위로 작은 사각형을 어둡게(그림자/
    오염) 또는 밝게(글레어) 채워서, 글자 일부가 안 보여도 나머지 글자+번호판 문법으로
    유추하는 능력을 학습시킴. 인식 자체가 불가능할 정도로 크게는 가리지 않음(폭 기준
    최대 18%까지, 최대 2군데)."""
    h, w = gray.shape[:2]
    out = gray.copy()
    for _ in range(random.randint(1, max_patches)):
        pw = random.randint(max(1, int(w * 0.06)), max(2, int(w * 0.18)))
        ph = random.randint(max(1, int(h * 0.3)), h)
        x0 = random.randint(0, max(0, w - pw))
        y0 = random.randint(0, max(0, h - ph))
        fill = random.randint(0, 40) if random.random() < 0.5 else random.randint(215, 255)
        out[y0:y0 + ph, x0:x0 + pw] = fill
    return out


def _low_res_sim(gray: np.ndarray) -> np.ndarray:
    """실제 실패 사례(2026-09-27, 인천항 영상: 검출확신도 41%/58%짜리 멀리 있는 번호판이
    박스 위치를 바로잡고 화질 보정(CLAHE+샤프닝)까지 해도 여전히 안 읽히는 경우)에 대응
    하기 위해 추가함. 기존 학습 crop은 대부분 가깝고 선명하게 찍힌 사진이라, 모델이
    "멀리 있어서 애초에 정보 자체가 부족한" 저해상도 상황을 학습해본 적이 없을 가능성이
    있음. 무작위로 축소했다가 원래 크기로 다시 늘려서(보간 손실로 세부 묘사가 뭉개짐)
    멀리서 찍힌 번호판과 비슷한 정보 손실을 인위적으로 재현함 - 모델이 이런 저해상도
    입력에서도 문법·잔여 단서로 유추하는 능력을 기르도록 함(가우시안/모션 블러와는 다른
    종류의 열화라 함께 적용해도 됨).
    2026-09-27 1차 시도(축소 비율 30~60%, 적용확률 25%) 결과: 검증 CER/완전일치율이
    기존 최고 기록보다 오히려 나빠지고(0.055->0.062, 83.1%->80.0%), 실제 영상 재확인에서도
    판독 불가가 더 늘어남 - 열화가 너무 심하고 너무 자주 적용돼 정상 케이스 학습까지
    방해한 것으로 판단, 축소 비율을 완화(45~70%)하고 적용확률도 낮춤(25%->12%)."""
    h, w = gray.shape[:2]
    scale = random.uniform(0.45, 0.7)
    small_w, small_h = max(1, int(w * scale)), max(1, int(h * scale))
    small = cv2.resize(gray, (small_w, small_h), interpolation=cv2.INTER_AREA)
    return cv2.resize(small, (w, h), interpolation=cv2.INTER_LINEAR)


def augment(gray: np.ndarray) -> np.ndarray:
    h, w = gray.shape[:2]
    if random.random() < 0.5:
        angle = random.uniform(-3, 3)
        M = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1.0)
        gray = cv2.warpAffine(gray, M, (w, h), borderValue=255)
    if random.random() < 0.35:
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
        alpha = random.uniform(0.7, 1.3)
        beta = random.uniform(-20, 20)
        gray = np.clip(gray.astype(np.float32) * alpha + beta, 0, 255).astype(np.uint8)
    if random.random() < 0.3:
        noise = np.random.normal(0, 8, gray.shape).astype(np.float32)
        gray = np.clip(gray.astype(np.float32) + noise, 0, 255).astype(np.uint8)
    if random.random() < 0.25:
        k = random.choice([3, 5])
        gray = cv2.GaussianBlur(gray, (k, k), 0)
    # 2026-09-26 추가: 실사용 중 관찰된 두 가지 실제 실패 패턴(달리는 차량의 방향성
    # 블러, 나뭇잎/글레어에 의한 부분 가림)에 대응하기 위해 새 데이터 없이 기존 crop을
    # 더 다양하게 증강함(둘 다 서로 다른 물리적 현상이라 함께 적용해도 문제 없음).
    if random.random() < 0.2:
        gray = _motion_blur(gray)
    if random.random() < 0.25:
        gray = _random_erase(gray)
    # 2026-09-27 추가: 검출확신도 낮은(멀리 있는/작은) 번호판이 박스·크롭 보정을 다 해도
    # 여전히 안 읽히는 실패 사례에 대응 - 저해상도 재현 증강을 추가로 노출시킴.
    # (1차 시도 25%는 너무 강했음 - 위 _low_res_sim 주석 참고. 12%로 완화)
    if random.random() < 0.12:
        gray = _low_res_sim(gray)
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
        tensor = torch.from_numpy(canvas).float().unsqueeze(0) / 255.0
        target = torch.tensor([self.char_to_idx[c] for c in text], dtype=torch.long)
        return tensor, target, len(text), text


def collate_fn(batch):
    imgs = torch.stack([b[0] for b in batch], dim=0)
    targets = torch.cat([b[1] for b in batch])
    target_lengths = torch.tensor([b[2] for b in batch], dtype=torch.long)
    texts = [b[3] for b in batch]
    return imgs, targets, target_lengths, texts


class PositionalEncoding(nn.Module):
    """표준 sinusoidal 위치 인코딩(학습 파라미터 없음) - self-attention은 그 자체로는
    순서를 모르는 구조라(같은 문자 집합이어도 순서가 다르면 다른 번호판이므로) 각
    시점에 위치 정보를 더해줌. BiLSTM 출력에 더하는 것이라 이미 어느 정도 순서
    정보가 녹아있긴 하지만, attention이 스스로도 위치를 명시적으로 참고할 수 있게 함."""

    def __init__(self, d_model, max_len=200):
        super().__init__()
        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float32).unsqueeze(1)
        div_term = torch.exp(torch.arange(0, d_model, 2).float() * (-np.log(10000.0) / d_model))
        pe[:, 0::2] = torch.sin(position * div_term)
        pe[:, 1::2] = torch.cos(position * div_term)
        self.register_buffer("pe", pe.unsqueeze(0))  # (1, max_len, d_model)

    def forward(self, x):  # x: (B, T, d_model)
        return x + self.pe[:, : x.size(1), :]


class CRNN(nn.Module):
    """[실험판] 기존 CRNN(CNN+BiLSTM+CTC, CER 0.074) 뒤에 self-attention 정제
    레이어를 추가한 구조. CNN/BiLSTM 부분은 기존과 완전히 동일 - 여기에 "정답을
    맞추는 능력"이 이미 대부분 들어있으므로 건드리지 않음. 그 위에 self-attention을
    residual(잔차 연결)로 얹어서, 도움이 안 되면 최소한 기존 성능은 보존하고
    (attention 출력이 0에 가깝게 수렴하면 사실상 기존 BiLSTM 그대로가 됨), 도움이
    되면 전체 시퀀스 맥락(예: 지역명+코드+글자+일련번호 전체 패턴)을 반영해 개별
    글자 인식을 보강할 걸 기대함."""

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

        # ---- 여기부터 신규: self-attention 정제 레이어 ----
        self.pos_enc = PositionalEncoding(512)
        self.attn = nn.MultiheadAttention(embed_dim=512, num_heads=4, dropout=0.1, batch_first=True)
        self.attn_norm = nn.LayerNorm(512)
        # ---------------------------------------------------

        self.dropout = nn.Dropout(0.3)
        self.fc = nn.Linear(512, num_classes + 1)

    def forward(self, x):
        feat = self.cnn(x)              # (B, 512, H', W')
        feat = self.height_pool(feat)   # (B, 512, 1, W')
        feat = feat.squeeze(2)          # (B, 512, W')
        feat = feat.permute(0, 2, 1)    # (B, W', 512)
        rnn_out, _ = self.rnn(feat)     # (B, W', 512)

        pos = self.pos_enc(rnn_out)
        attn_out, _ = self.attn(pos, pos, pos)  # self-attention: 자기 자신 시퀀스 안에서 참고
        out = self.attn_norm(rnn_out + attn_out)  # 잔차 연결 - 최소한 BiLSTM 성능은 보존

        out = self.dropout(out)
        out = self.fc(out)              # (B, W', num_classes+1)
        return out.permute(1, 0, 2)     # (W', B, num_classes+1)


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
    print("[실험판] BiLSTM + self-attention 정제 레이어 구조로 처음부터 학습합니다.")
    print(f"출력 폴더: {OUT_DIR} (기존 ocr_model/ 은 전혀 건드리지 않습니다)")

    if not LABELS_CSV.exists():
        print(f"[오류] {LABELS_CSV} 를 찾을 수 없습니다.")
        return

    with open(LABELS_CSV, "r", encoding="utf-8-sig") as f:
        rows = [(r["filename"], r["text"], r["line_count_guess"]) for r in csv.DictReader(f)]
    print(f"전체 데이터: {len(rows)}장")

    all_chars = sorted({c for _, text, _ in rows for c in text})
    char_to_idx = {c: i + 1 for i, c in enumerate(all_chars)}
    idx_to_char = {i + 1: c for i, c in enumerate(all_chars)}
    print(f"문자 종류: {len(all_chars)}개 -> {''.join(all_chars)}")

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    if BEST_MODEL_PATH.exists():
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        backup_model = OUT_DIR / f"best_recognizer_attn_backup_{ts}.pth"
        shutil.copy(BEST_MODEL_PATH, backup_model)
        print(f"[백업] 기존 {BEST_MODEL_PATH.name}를 {backup_model} 로 백업했습니다.")
        if CHARS_JSON_PATH.exists():
            shutil.copy(CHARS_JSON_PATH, OUT_DIR / f"chars_attn_backup_{ts}.json")

    with open(CHARS_JSON_PATH, "w", encoding="utf-8") as f:
        json.dump({"chars": all_chars}, f, ensure_ascii=False, indent=2)

    random.shuffle(rows)
    n_val = max(1, int(len(rows) * VAL_RATIO))
    val_rows, train_rows = rows[:n_val], rows[n_val:]
    print(f"학습 {len(train_rows)}장 / 검증 {len(val_rows)}장")

    train_ds = PlateOCRDataset(train_rows, char_to_idx, train=True)
    val_ds = PlateOCRDataset(val_rows, char_to_idx, train=False)

    # 2026-09-27 추가: "배"가 기존 CONFUSABLE_CHARS에 빠져있었음 - batch_test_images.py로
    # 767장 실측 후 오답을 분석해보니 "바"<->"배" 혼동이 실제로 반복 확인됐는데
    # (부산90바5567->부산90배5567, 부산90배4277->부산90바4277, 울산80아7396 등),
    # labels.csv 실측 결과 "바"는 457장인데 "배"는 79장뿐(5.8배 차이) - 이 불균형이
    # 혼동의 실제 원인일 가능성이 커서 "배"를 추가함.
    CONFUSABLE_CHARS = set("사자바아보모고노로배")
    CONFUSABLE_WEIGHT = 1.5
    # 2026-09-26 추가: labels.csv 실측 결과 9자리(지역명 포함) 형식이 97.6%(1907/1954)를
    # 차지하고, 8자리(지역명 없음, "006"+글자+4자리 - 예: 006너7923)는 2.2%(43장),
    # 7자리(2자리+글자+4자리, 버스/대여 등 - 예: 71해4006)는 0.2%(4장)뿐이었음. 이 극심한
    # 불균형 때문에 검증에서 8자리 트럭 번호판을 모델이 9자리(지역명 포함)로 잘못 읽는
    # 사례가 실제로 확인됨(verify_integration_attn.py worst-15) - 모델이 "번호판=9자리"로
    # 사실상 암기해버린 것으로 보여, 드문 두 형식을 오버샘플링해서 노출을 늘림.
    RARE_FORMAT_WEIGHT = 6.0
    # 2026-09-27 추가: batch_test_images.py 767장 실측 오답 중 상당수가 "뒤 4자리는
    # 맞는데 지역명만 틀림"(전남80배3059->경남80배3059, 전남80아1096->경남80아1096,
    # 울산90배3020->부산90배3020 등)이었음. labels.csv에서 지역명별 개수를 세어보니
    # 전남 52장/경남 633장(12배!), 울산 87장/부산 310장(3.6배), 서울 71장/부산 310장
    # (4.4배)처럼 실제로 혼동되는 지역명 쌍끼리 학습 데이터 양이 크게 차이났음 - 모델이
    # 애매하면 데이터가 더 많은 쪽(경남/부산)으로 쏠리는 것으로 추정. 부산(310)/경남(633)/
    # 경기(206)/경북(253) 등 이미 충분한 지역명은 그대로 두고, 그보다 적은 지역명만
    # 오버샘플링해서 노출을 늘림(위 두 오버샘플링과 같은 방식 - 기존에 검증된 안전한
    # 방법을 그대로 확장한 것일 뿐, 새 증강 로직을 추가한 게 아님).
    REGION_RARE_THRESHOLD = 100
    REGION_RARE_WEIGHT = 2.5

    # 2026-09-29: 역빈도 비례 가중치를 두 가지 방식(개별 캡, 결합값 캡)으로 시도
    # 했다가 둘 다 실측 후 되돌림 - CHANGELOG.md의 "CRNN 학습 오버샘플링 가중치..."
    # 항목들 참고. 1차(개별 항목 각각 10x 캡 후 곱함, 최대 100x)는 내부 검증
    # 완전일치율 0.749->0.605로 악화, 2차(곱한 결합값에 10x 캡, 최대 10x로 더
    # 낮췄음에도) 오히려 0.133으로 더 악화됨 - 캡을 더 낮췄는데 더 나빠졌다는 건
    # "가중치가 너무 크다"는 최초 가설 자체가 틀렸다는 뜻. 두 번 연속 실측으로
    # 반증된 상태라 원인 불명 - 더 추측성 수정을 반복하지 않고 실제로 87.4%
    # (배치 테스트, best_recognizer_attn_backup_20260928_122831.pth)를 만들어낸
    # 아래 원래 방식(고정 배수)으로 완전히 되돌림. 이 두 항목(CONFUSABLE_WEIGHT/
    # REGION_RARE_WEIGHT)은 그대로 사용.
    sample_weights = []
    n_boosted_confusable = 0
    n_boosted_rare = 0
    n_boosted_region = 0
    region_counts_train = {}
    for _, text, _ in train_rows:
        if len(text) == 9:
            region_counts_train[text[:2]] = region_counts_train.get(text[:2], 0) + 1
    for _, text, _ in train_rows:
        w = 1.0
        if any(c in CONFUSABLE_CHARS for c in text):
            w *= CONFUSABLE_WEIGHT
            n_boosted_confusable += 1
        if len(text) != 9:
            w *= RARE_FORMAT_WEIGHT
            n_boosted_rare += 1
        elif region_counts_train.get(text[:2], 0) < REGION_RARE_THRESHOLD:
            w *= REGION_RARE_WEIGHT
            n_boosted_region += 1
        sample_weights.append(w)
    print(f"글자쌍 오버샘플링: 학습 {len(train_rows)}장 중 {n_boosted_confusable}장에 가중치 "
          f"{CONFUSABLE_WEIGHT}x 적용 ('배' 추가)")
    print(f"드문 형식(7/8자리, 지역명 없음) 오버샘플링: {n_boosted_rare}장에 가중치 "
          f"{RARE_FORMAT_WEIGHT}x 추가 적용")
    print(f"드문 지역명(<{REGION_RARE_THRESHOLD}장) 오버샘플링: {n_boosted_region}장에 가중치 "
          f"{REGION_RARE_WEIGHT}x 추가 적용 (세 가중치는 곱해서 중첩 적용됨)")
    train_sampler = torch.utils.data.WeightedRandomSampler(
        sample_weights, num_samples=len(sample_weights), replacement=True
    )

    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, sampler=train_sampler,
                               num_workers=0, collate_fn=collate_fn)
    val_loader = DataLoader(val_ds, batch_size=BATCH_SIZE, shuffle=False,
                             num_workers=0, collate_fn=collate_fn)

    model = CRNN(num_classes=len(all_chars)).to(device)
    n_params = sum(p.numel() for p in model.parameters())
    print(f"모델 파라미터 수: {n_params:,}개 (참고용 - 기존 구조 대비 attention 레이어만큼 늘어남)")
    base_lr = LR
    criterion = nn.CTCLoss(blank=0, zero_infinity=True)
    optimizer = torch.optim.AdamW(model.parameters(), lr=base_lr, weight_decay=WEIGHT_DECAY)
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(optimizer, mode="min", factor=0.5, patience=5)

    best_cer = float("inf")
    best_exact_seen = -1.0
    best_exact_at_save = -1.0
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
        sample_preds = []
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
        if len({p for p, _ in sample_preds}) == 1 and len({g for _, g in sample_preds}) > 1:
            print("    [경고] 서로 다른 입력인데 예측이 전부 동일 - 붕괴(collapse) 의심")

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

    print("\n=== [실험판] 학습 완료 ===")
    print(f"최고 검증 CER(낮을수록 좋음): {best_cer:.3f}   (기존 BiLSTM단독 기록: 0.074)")
    print(f"저장된 체크포인트의 완전 일치율: {best_exact_at_save:.3f}   (기존 기록: 0.749)")
    print(f"(참고) 학습 중 어느 epoch에서든 관측된 최고 완전 일치율: {best_exact_seen:.3f}")
    print(f"모델: {BEST_MODEL_PATH}")
    print(f"문자 목록: {CHARS_JSON_PATH}")
    if best_cer < 0.074:
        print("-> 기존보다 개선됨! verify_integration_attn.py로 한 번 더 확인해보세요.")
    else:
        print("-> 기존(0.074)보다 나아지지 않음 - attention 레이어가 이 정도 데이터량(약 1,950장)")
        print("   에서는 도움이 안 된 것으로 보입니다. 기존 시스템(ocr_model/, plate_ocr_crnn.py)은")
        print("   이 실험과 무관하게 그대로 안전하게 남아있습니다.")

    log_is_new = not OCR_LOG_CSV.exists()
    with open(OCR_LOG_CSV, "a", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        if log_is_new:
            writer.writerow([
                "date", "architecture", "epochs_run", "epochs_max", "batch_size", "lr",
                "optimizer", "weight_decay", "patience", "confusable_oversample",
                "rare_format_oversample", "train_images", "val_images", "n_params",
                "best_cer", "best_exact_at_save", "note",
            ])
        writer.writerow([
            datetime.now().strftime("%Y-%m-%d %H:%M"), "BiLSTM+SelfAttention", epoch, EPOCHS,
            BATCH_SIZE, base_lr, "AdamW", WEIGHT_DECAY, PATIENCE, CONFUSABLE_WEIGHT,
            RARE_FORMAT_WEIGHT, len(train_rows), len(val_rows), n_params,
            f"{best_cer:.4f}", f"{best_exact_at_save:.4f}",
            "'배' 글자쌍 추가 + 드문 지역명(전남/울산/서울 등) 오버샘플링 추가 - "
            "767장 실측에서 확인된 지역명/글자 혼동(전남->경남, 바<->배 등) 개선 시도",
        ])
    print(f"\n실행 설정/결과를 {OCR_LOG_CSV}에 기록했습니다.")


if __name__ == "__main__":
    main()
