# -*- coding: utf-8 -*-
"""
plate_ocr_crnn.py
====================================================
train_ocr_recognizer.py로 직접 학습시킨 CRNN(자체 인식 모델)을
plate_detector_gui.py에서 불러와 쓰기 위한 모듈.
(검증 CER 0.085 / 완전 일치율 73.5% - 기존 EasyOCR 최종 검증치 54.1%보다 확실히 우수)

이 파일에 있는 CRNN 클래스/전처리 함수는 train_ocr_recognizer.py의 것과
반드시 100% 동일해야 함 (다르면 저장된 가중치(state_dict)를 못 불러오거나,
학습 때와 다른 방식으로 전처리해서 실제로는 정확도가 떨어짐).

사용법
====================================================
    from plate_ocr_crnn import CRNNRecognizer

    recognizer = CRNNRecognizer()
    recognizer.load(model_path, chars_path)   # 앱 시작 시 한 번
    text, conf = recognizer.recognize(crop_bgr)  # crop마다 호출
"""
import json
from collections import Counter

import cv2
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

# 학습 스크립트(train_ocr_recognizer.py)와 반드시 동일해야 하는 값들
IMG_H = 48
IMG_W = 320


def unwrap_two_line(gray: np.ndarray) -> np.ndarray:
    """2줄 번호판 crop(그레이스케일)을 위/아래로 나눠서 옆으로 이어붙임
    (정답 텍스트의 "지역명 먼저, 번호 나중" 순서와 맞추기 위해 위쪽 줄을 왼쪽에 배치).
    수평 투영(행별 글자 픽셀 수)에서 가장 여백이 많은 행을 두 줄 사이 경계로 추정함.
    train_ocr_recognizer.py의 동명 함수와 완전히 동일한 로직."""
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


def guess_line_count(crop_bgr: np.ndarray) -> int:
    """crop의 가로/세로 비율로 1줄/2줄을 대략 판별
    (1줄은 가로가 세로의 2.8배 이상으로 납작하고, 2줄은 그보다 정사각형에 가까움).
    prepare_ocr_training_data.py의 guess_line_count와 완전히 동일한 기준
    - 학습 데이터 라벨과 같은 규칙으로 판별해야 학습 때와 동일하게 동작함."""
    h, w = crop_bgr.shape[:2]
    if h == 0:
        return 1
    ratio = w / h
    return 1 if ratio >= 2.8 else 2


def preprocess_for_model(img_bgr: np.ndarray, is_two_line: bool) -> np.ndarray:
    """crop 이미지 한 장을 모델 입력 크기(IMG_H x IMG_W, 그레이스케일)로 정규화.
    2줄이면 먼저 한 줄로 펴고, 그 다음 공통으로 가로세로 비율 유지 확대 + 패딩.
    train_ocr_recognizer.py의 동명 함수와 완전히 동일한 로직."""
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
    """train_ocr_recognizer.py의 CRNN과 완전히 동일한 구조
    (레이어 순서/크기가 하나라도 다르면 state_dict 로드 자체가 실패함)."""

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
        self.fc = nn.Linear(512, num_classes + 1)  # +1 = CTC blank(index 0)

    def forward(self, x):
        feat = self.cnn(x)              # (B, 512, H', W')
        feat = self.height_pool(feat)   # (B, 512, 1, W')
        feat = feat.squeeze(2)          # (B, 512, W')
        feat = feat.permute(0, 2, 1)    # (B, W', 512)
        out, _ = self.rnn(feat)         # (B, W', 512)
        out = self.dropout(out)
        out = self.fc(out)              # (B, W', num_classes+1)
        return out.permute(1, 0, 2)     # (W', B, num_classes+1)


def greedy_decode_with_conf(logits, idx_to_char):
    """logits: (T, B, C) -> (배치별 디코딩 문자열 리스트, 배치별 확신도 리스트).
    확신도는 CTC collapse 후 최종 채택된 각 글자 위치의 softmax 확률 평균
    (겹치는 blank/중복 timestep은 제외하고, 실제로 글자로 채택된 지점만 반영)."""
    probs = F.softmax(logits, dim=2)          # (T, B, C)
    max_probs, max_idx = probs.max(dim=2)     # (T, B), (T, B)
    max_probs = max_probs.permute(1, 0)       # (B, T)
    max_idx = max_idx.permute(1, 0)           # (B, T)

    texts, confs = [], []
    for b in range(max_idx.shape[0]):
        chars, char_probs = [], []
        prev = -1
        for t in range(max_idx.shape[1]):
            p = int(max_idx[b, t].item())
            prob = float(max_probs[b, t].item())
            if p != prev and p != 0:  # 0 = blank
                chars.append(idx_to_char[p])
                char_probs.append(prob)
            prev = p
        texts.append("".join(chars))
        confs.append(sum(char_probs) / len(char_probs) if char_probs else 0.0)
    return texts, confs


def ctc_beam_search_decode(logits, idx_to_char, beam_width=10, prune_top_k=15):
    """logits: (T, B, C) -> (배치별 디코딩 문자열 리스트, 배치별 확신도 리스트).
    greedy_decode_with_conf는 매 timestep에서 가장 높은 글자만 그때그때 고르는데,
    그러다 보니 특정 timestep 하나가 애매하게 흔들리면 전체 문자열이 틀어질 수 있음.
    beam search는 매 timestep마다 유망한 후보 문자열(prefix) 여러 개를 동시에 들고
    가면서, CTC 특유의 "blank로 구분된 반복=같은 글자, blank 없이 반복=한 글자로 합침"
    규칙까지 고려해 전체 시퀀스 확률이 가장 높은 문자열을 고름 - 재학습 없이 추론
    방식만 바꿔서 정확도를 끌어올릴 수 있는 방법.
    beam_width: 매 timestep에서 유지할 후보 prefix 개수 (많을수록 정확하지만 느려짐).
    prune_top_k: 매 timestep에서 검토할 문자 후보 수 (전체 문자 종류가 50개 안팎이라
    이 정도만 봐도 충분 - 확률이 극히 낮은 나머지 문자는 어차피 채택될 일이 없음)."""
    probs = F.softmax(logits, dim=2).detach().cpu().numpy()  # (T, B, C)
    T, B, C = probs.shape
    texts, confs = [], []
    for b in range(B):
        p = probs[:, b, :]  # (T, C)
        beams = {(): (1.0, 0.0)}  # prefix(문자 인덱스 튜플) -> (blank로 끝날 확률, 글자로 끝날 확률)
        for t in range(T):
            p_t = p[t]
            top_chars = np.argsort(-p_t)[:prune_top_k]
            new_beams = {}

            def add(prefix, p_b_add, p_nb_add):
                eb, enb = new_beams.get(prefix, (0.0, 0.0))
                new_beams[prefix] = (eb + p_b_add, enb + p_nb_add)

            for prefix, (p_b, p_nb) in beams.items():
                p_tot = p_b + p_nb
                last_char_idx = prefix[-1] if prefix else None
                for c in top_chars:
                    pr = float(p_t[c])
                    if pr <= 0:
                        continue
                    if c == 0:  # blank
                        add(prefix, p_tot * pr, 0.0)
                    elif c == last_char_idx:
                        # blank 없이 같은 글자가 이어짐 -> CTC 규칙상 한 글자로 합쳐짐(추가 안 됨)
                        add(prefix, 0.0, p_nb * pr)
                        # blank를 거친 뒤 같은 글자가 다시 나옴 -> 진짜 반복 글자로 인정
                        add(prefix + (c,), 0.0, p_b * pr)
                    else:
                        add(prefix + (c,), 0.0, p_tot * pr)
            beams = dict(
                sorted(new_beams.items(), key=lambda kv: -(kv[1][0] + kv[1][1]))[:beam_width]
            )
        best_prefix, (p_b, p_nb) = max(beams.items(), key=lambda kv: kv[1][0] + kv[1][1])
        texts.append("".join(idx_to_char[i] for i in best_prefix))
        confs.append(p_b + p_nb)
    return texts, confs


def _tta_variants(crop_bgr):
    """crop 한 장에서 살짝씩 다르게 변형된 버전 여러 개를 만듦 (원본 + 회전 +-3도 +
    밝기 +-25) - 사진마다 흔들림/조명 차이로 인식이 애매할 때, 여러 조건에서 반복
    시도해서 다수결로 더 안정적인 답을 얻기 위함. 변형 개수를 많이 늘리면 그만큼
    느려지므로, 체감상 부담 없는 선에서(원본 포함 5개) 제한함."""
    variants = [crop_bgr]
    h, w = crop_bgr.shape[:2]
    center = (w / 2, h / 2)
    for angle in (3, -3):
        rot_mat = cv2.getRotationMatrix2D(center, angle, 1.0)
        rotated = cv2.warpAffine(
            crop_bgr, rot_mat, (w, h), borderValue=(255, 255, 255), flags=cv2.INTER_LINEAR
        )
        variants.append(rotated)
    variants.append(cv2.convertScaleAbs(crop_bgr, alpha=1.0, beta=25))
    variants.append(cv2.convertScaleAbs(crop_bgr, alpha=1.0, beta=-25))
    return variants


def _combine_tta_results(results):
    """여러 변형에서 나온 (텍스트, 확신도) 목록을 하나의 최종 답으로 합침.
    같은 텍스트가 2번 이상 나오면(다수결) 그 텍스트를 채택하고, 그 텍스트를 낸
    시도들의 평균 확신도를 최종 확신도로 씀 - 여러 조건에서 반복해도 같은 답이
    나왔다는 건 그만큼 신뢰할 수 있다는 뜻이라 평균을 내도 됨.
    다수결이 안 갈리면(전부 다른 텍스트) 그중 가장 확신도가 높았던 시도를 그대로
    채택함 - 억지로 다수결을 만들기보다는 가장 자신 있었던 답을 믿는 게 나음."""
    if not results:
        return "", None
    counts = Counter(t for t, _ in results)
    max_count = max(counts.values())
    top_texts = [t for t, cnt in counts.items() if cnt == max_count]
    if len(top_texts) == 1 and max_count >= 2:
        winner = top_texts[0]
        confs = [c for t, c in results if t == winner and c is not None]
        conf = sum(confs) / len(confs) if confs else None
        return winner, conf
    return max(results, key=lambda tc: tc[1] if tc[1] is not None else -1.0)


class CRNNRecognizer:
    """앱에서 실제로 쓰는 래퍼 클래스.
    load()로 학습된 가중치를 한 번 불러온 뒤, recognize(crop_bgr)를 호출해서
    (텍스트, 확신도) 튜플을 얻음. EasyOCR과 인터페이스를 맞춰서 plate_detector_gui.py의
    _ocr_plate에서 그대로 바꿔 끼울 수 있게 함."""

    def __init__(self):
        self.model = None
        self.idx_to_char = None
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.loaded = False

    def load(self, model_path, chars_path):
        with open(chars_path, "r", encoding="utf-8") as f:
            all_chars = json.load(f)["chars"]
        self.idx_to_char = {i + 1: c for i, c in enumerate(all_chars)}

        model = CRNN(num_classes=len(all_chars)).to(self.device)
        state_dict = torch.load(str(model_path), map_location=self.device)
        model.load_state_dict(state_dict)
        model.eval()
        self.model = model
        self.loaded = True

    def recognize(self, crop_bgr, tta=False, decode="greedy"):
        """crop 한 장에서 글자를 읽음. 반환값은 (텍스트, 확신도) 튜플
        - 모델이 아직 안 불려졌거나 crop이 비어있으면 ("", None)을 돌려줌
        (EasyOCR 쪽 _ocr_once/_ocr_plate와 동일한 반환 형태).
        tta=True면 crop을 살짝씩 바꾼 여러 변형(회전/밝기)으로 각각 인식한 뒤
        다수결로 최종 답을 정함 - 한 번의 추론보다 몇 배 느려지므로, 정지 이미지
        단일 인식처럼 여유가 있는 경로에서만 켜고, 영상 프레임 처리처럼 속도가
        중요한 경로에서는 기본값(False)을 그대로 씀.
        decode="greedy"(기본, 기존과 동일) 또는 "beam" - beam은 재학습 없이 추론
        방식만 바꿔서 정확도를 끌어올려보는 옵션. 이미지 한 장당 10ms 안팎 더 걸리는
        정도라 정지 이미지 인식 경로에서는 부담 없이 기본값으로 바꿔도 됨."""
        if not self.loaded or crop_bgr is None or crop_bgr.size == 0:
            return "", None
        if not tta:
            return self._recognize_single(crop_bgr, decode=decode)
        results = [self._recognize_single(v, decode=decode) for v in _tta_variants(crop_bgr)]
        results = [(t, c) for t, c in results if t]  # 빈 결과는 다수결 계산에서 제외
        return _combine_tta_results(results)

    def _recognize_single(self, crop_bgr, decode="greedy"):
        """변형 없이 crop 원본 그대로 한 번 인식함. recognize()의 기본 경로이자
        TTA에서 각 변형마다 호출되는 내부 함수."""
        try:
            h, w = crop_bgr.shape[:2]
            if h == 0 or w == 0:
                return "", None
            is_two_line = guess_line_count(crop_bgr) == 2
            canvas = preprocess_for_model(crop_bgr, is_two_line)
            tensor = (
                torch.from_numpy(canvas).float().unsqueeze(0).unsqueeze(0).to(self.device) / 255.0
            )
            with torch.no_grad():
                logits = self.model(tensor)
            if decode == "beam":
                texts, confs = ctc_beam_search_decode(logits, self.idx_to_char)
            else:
                texts, confs = greedy_decode_with_conf(logits, self.idx_to_char)
            return texts[0], confs[0]
        except Exception:
            return "", None
