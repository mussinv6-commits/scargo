# -*- coding: utf-8 -*-
"""
plate_ocr_crnn_attn.py
====================================================
[실험판] train_ocr_recognizer_attn.py로 학습시킨 "BiLSTM + self-attention 정제
레이어" 구조 모델을 불러와 쓰기 위한 모듈. 기존 plate_ocr_crnn.py(지금 앱이 실제로
쓰는 파일)는 전혀 건드리지 않고 완전히 별도로 존재합니다.

이 파일의 CRNN 클래스는 train_ocr_recognizer_attn.py의 것과 100% 동일해야 합니다
(다르면 state_dict를 못 불러옴). 기존 plate_ocr_crnn.py의 CRNN과는 구조가 달라서
서로 호환되지 않습니다 - best_recognizer.pth(기존)를 이 파일로 불러오려 하거나,
best_recognizer_attn.pth(실험판)를 기존 plate_ocr_crnn.py로 불러오려 하면 둘 다
반드시 실패합니다(의도된 동작).

사용법
====================================================
    from plate_ocr_crnn_attn import CRNNRecognizer

    recognizer = CRNNRecognizer()
    recognizer.load(model_path, chars_path)
    text, conf = recognizer.recognize(crop_bgr)
"""
import json
import re
from collections import Counter

import cv2
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

IMG_H = 48
IMG_W = 320

VALID_PLATE_REGIONS = frozenset([
    "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
    "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
])

# 2026-09-27 (v3.13): plate_detector_gui.py의 동일 이름 정규식/함수와 로직이 100%
# 같아야 함 - 이 모듈은 완전히 별도로 존재하므로(모듈 docstring 참고) import하지
# 않고 여기서도 직접 정의함. TTA 결합(_combine_tta_results)에서 "형식이 명백히
# 깨진 결과"를 걸러내는 용도로만 씀.
_PLATE_RE_WITH_REGION = re.compile(r"^(" + "|".join(VALID_PLATE_REGIONS) + r")\d{2}[가-힣]\d{4}$")
_PLATE_RE_NO_REGION = re.compile(r"^\d{3}[가-힣]\d{4}$")
_PLATE_RE_SHORT_NO_CODE = re.compile(r"^\d{2}[가-힣]\d{4}$")

# 2026-09-29: 767장 실측에서 CRNN이 확신도 0.5~0.98로 "자신있게 틀린" 21건을
# 다 까봤더니, 14건(지역명 7 + 차종글자 7)이 완전히 무작위가 아니라 아래 두
# 그룹끼리만 서로 헷갈렸음(batch_test_result.csv 분석, 이 커밋 시점 기준):
#   지역명: 울산<->부산, 전남<->경남, 대전<->대구, 충남<->울산
#   글자:   아/바/배/자/사 (다섯 글자끼리 서로)
# 재학습으로 이걸 고치려던 시도 2번(train_ocr_recognizer_attn.py 역빈도 가중치)이
# 둘 다 전체 정확도를 대폭 깎아먹어서(87.4%->60.5%->13.3%) 완전히 되돌렸음 - 그
# 커밋의 주석 참고. 최종 확신도 값만으로는 이 오답들을 못 걸러냄(0.54~0.98로
# 정답 사례들과 겹쳐서 임계값을 어디에 둬도 정답까지 같이 걸러지거나 오답이
# 그냥 통과함).
#
# 그래서 예측 텍스트 자체는 전혀 안 건드리고("고치기"는 다시 시도 안 함 -
# 위 두 번 실패 경험상 이 레벨에서 규칙으로 값을 바꾸는 건 항상 다른 걸 깨뜨림),
# 대신 beam search가 이미 계산해 둔 2~10위 후보들 중에 "위 혼동 쌍의 반대쪽"이
# 1위 후보와 가까운 점수로 들어있는지만 확인해서 CSV/GUI에 "확인필요" 표시만
# 추가함 - 진짜 애매했던 케이스만 사람이 다시 보게 하려는 목적. 이 표시 로직이
# 예측값에 영향을 주는 경로는 없으므로 최악의 경우에도 정확도 숫자는 그대로임.
CONFUSABLE_REGION_GROUPS = [
    frozenset({"울산", "부산"}),
    frozenset({"전남", "경남"}),
    frozenset({"대전", "대구"}),
    frozenset({"충남", "울산"}),
]
CONFUSABLE_LETTER_GROUP = frozenset({"아", "바", "배", "자", "사"})

# beam 2~10위 후보의 점수(p_b+p_nb)가 1위 후보 점수의 이 비율 이상이면 "가까운
# 대안"으로 보고 확인필요 표시를 함. 너무 낮추면(예: 0.05) 항상 존재하는 잡음
# 수준의 후보까지 다 걸려서 표시가 무의미해지고, 너무 높이면(예: 0.8) 진짜
# 애매했던 사례도 놓침 - 첫 실측값을 보고 조정 예정(아래 diagnostic 컬럼으로
# 실제 분포를 CSV에 남겨서 사람이 눈으로 보고 정할 수 있게 함).
CONFUSABLE_ALT_SCORE_RATIO = 0.15


def _region_confusable_group(region):
    for group in CONFUSABLE_REGION_GROUPS:
        if region in group:
            return group
    return None


def _find_confusable_alt(ranked, idx_to_char, best_prefix):
    """beam search의 전체 순위(ranked, 1위 포함)를 보고, 1위 텍스트와 지역명 2글자
    또는 차종 글자 1글자만 다르면서 그 차이가 위 혼동 그룹 안에 속하는 후보가
    있으면 (대안텍스트, 대안점수비율) 을 반환. 없으면 None. 예측값은 안 바꾸고
    참고 정보만 만드는 함수라서 호출부에서 이 결과를 텍스트 결정에 쓰면 안 됨."""
    if not ranked:
        return None
    best_text = "".join(idx_to_char[i] for i in best_prefix)
    best_score = None
    for prefix, (p_b, p_nb) in ranked:
        if prefix == best_prefix:
            best_score = p_b + p_nb
            break
    if best_score is None or best_score <= 0:
        return None
    best_region = best_text[:2] if len(best_text) == 9 else None
    best_letter = best_text[-5] if len(best_text) == 9 else None
    for prefix, (p_b, p_nb) in ranked:
        if prefix == best_prefix:
            continue
        alt_text = "".join(idx_to_char[i] for i in prefix)
        if len(alt_text) != len(best_text):
            continue
        alt_score = p_b + p_nb
        ratio = alt_score / best_score if best_score else 0.0
        if ratio < CONFUSABLE_ALT_SCORE_RATIO:
            continue
        # 지역명 쌍만 다르고 나머지가 동일한 경우
        if (
            best_region is not None
            and alt_text[2:] == best_text[2:]
            and alt_text[:2] != best_region
        ):
            group = _region_confusable_group(best_region)
            if group and alt_text[:2] in group:
                return alt_text, ratio, f"지역명({best_region}<->{alt_text[:2]})"
        # 차종 글자 한 글자만 다르고 나머지가 동일한 경우
        if (
            best_letter is not None
            and alt_text[:-5] + alt_text[-4:] == best_text[:-5] + best_text[-4:]
            and alt_text[-5] != best_letter
            and best_letter in CONFUSABLE_LETTER_GROUP
            and alt_text[-5] in CONFUSABLE_LETTER_GROUP
        ):
            return alt_text, ratio, f"글자({best_letter}<->{alt_text[-5]})"
    return None


def _is_valid_plate_format(text):
    if not text:
        return False
    return bool(
        _PLATE_RE_WITH_REGION.match(text)
        or _PLATE_RE_NO_REGION.match(text)
        or _PLATE_RE_SHORT_NO_CODE.match(text)
    )


# 2026-09-28: 위/아래 두 줄 경계를 "가장 잉크가 적은 행(row_sum 최솟값)"으로 찾던
# 기존 방식은, 한국 번호판이 위(지역명+기호, 작은 글씨) 아래(일련번호, 굵고 큰 글씨)로
# 굵기/크기가 크게 다르다는 특성 때문에 실패하는 경우가 실측으로 확인됨 - 예를 들어
# "경기94/아5208" 케이스는 번호판 맨 아래(글자 다 끝난 뒤 배경~범퍼 경계)가 두 줄
# 사이 진짜 틈보다 더 깊은 골짜기로 잡혀서, 위쪽 밴드에 두 줄이 다 뭉쳐 들어가고
# 아래쪽 밴드는 텅 비어버리는 증상이 나옴(CRNN이 우겨넣어진 작은 글씨를 잘못 읽어
# 9->8 같은 숫자 오인식으로 이어짐 - 확신도도 낮게 나옴). 그런데 YOLO 박스(여백
# 더하기 전 원본)의 세로 범위를 알면, 그 안에서 위쪽 줄은 대략 35~45% 지점에서
# 끝난다는 게 실측 두 사례(정상 케이스/실패 케이스) 모두에서 확인됨 - 그래서 잉크
# 분포에 의존하지 않고 원본 박스 범위(inner_range, 여백 비율로 계산해서 넘겨받음)
# 안에서 고정 비율로 나누는 방식으로 바꿈. inner_range가 없으면(하위 호환) 예전처럼
# crop 전체 기준 고정 비율을 씀.
LINE1_HEIGHT_RATIO = 0.40


def unwrap_two_line(gray: np.ndarray, inner_range: tuple = None) -> np.ndarray:
    h, w = gray.shape[:2]
    if inner_range is not None:
        it = max(0, min(h - 1, int(h * inner_range[0])))
        ib = max(it + 1, min(h, int(h * inner_range[1])))
    else:
        it, ib = 0, h
    split = it + int((ib - it) * LINE1_HEIGHT_RATIO)
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


def guess_line_count(crop_bgr: np.ndarray, box_ratio: float = None) -> int:
    """2026-09-28: 원래 crop_bgr 자체의 가로/세로 비율만 보고 판단했는데, 이 crop은
    이미 _padded_crop의 여백이 더해진 상태임 - 확신도 낮은 박스는 위/좌우 30% +
    아래쪽만 65%로 비대칭 여백을 주는데(2026-09-27 추가), 이 비대칭 여백이 비율을
    약 18% 줄여버려서(가로는 1.6배, 세로는 1.95배 커짐) 원래 박스 비율이 2.8~3.4
    사이인 (실측 데이터의 대다수가 여기 몰려있음) '명백한 1줄' 번호판이 확신도가
    낮아지는 순간 갑자기 2줄로 잘못 분류되는 경우가 실제 crop 109장 중 45장(41%)
    에서 재현됨 - 확신도 높을 땐 멀쩡하다가 확신도만 낮아지면(원거리/각도/저조도)
    엉뚱한 지역명이 섞여 나오는 증상, 그리고 박스를 수동으로 살짝 옮기면(수동
    보정 경로는 이 비대칭 여백을 안 씀) 바로 정상으로 돌아오는 증상과 정확히
    일치함. box_ratio(YOLO 박스 자체의 비율, 여백 더하기 전)를 넘겨주면 그걸
    우선 사용해서 이 왜곡을 피함 - 안 넘겨주면 기존처럼 crop 자체 비율로 판단
    (하위 호환)."""
    if box_ratio is not None:
        ratio = box_ratio
    else:
        h, w = crop_bgr.shape[:2]
        if h == 0:
            return 1
        ratio = w / h
    return 1 if ratio >= 2.8 else 2


def preprocess_for_model(img_bgr: np.ndarray, is_two_line: bool, inner_range: tuple = None) -> np.ndarray:
    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
    if is_two_line:
        gray = unwrap_two_line(gray, inner_range=inner_range)

    h, w = gray.shape[:2]
    scale = IMG_H / max(h, 1)
    new_w = min(IMG_W, max(1, int(w * scale)))
    resized = cv2.resize(gray, (new_w, IMG_H), interpolation=cv2.INTER_CUBIC)

    canvas = np.full((IMG_H, IMG_W), 255, dtype=np.uint8)
    canvas[:, :new_w] = resized
    return canvas


# 2026-09-28 (2줄 번호판 확신도 진단용, 프로덕션 로직 미변경): ocr_plate()의
# docstring에 남긴 것처럼 "2줄은 CRNN 정확도(96.8%)가 1줄(96.9%)과 거의 같은데
# 확신도 임계값(0.5) 미달로 폴백되는 비율만 훨씬 높다(27% vs 7.6%)"는 게 실측으로
# 확인돼 있었음 - 원인을 실측 15장 표본(1줄 6장/2줄 9장, 정답/판독불가 섞어서)으로
# 추적해본 결과: ctc_beam_search_decode의 확신도(p_b+p_nb)는 "타임스텝 수(T)만큼
# 곱해진 정규화 안 된 결합확률"인데, unwrap_two_line이 위/아래 두 줄을 옆으로
# 이어붙이면서 폭이 1줄보다 훨씬 넓어짐 - 실측: 1줄 캔버스 내용폭 150~171px(T≈37~42),
# 2줄은 표본 9장 전부(정답/판독불가 무관하게) 캔버스 상한(320px, T=80)에 딱 걸림.
# 즉 T가 약 2배라서, 같은 글자 정확도라도 확신도가 구조적으로 더 낮게 나올 수밖에
# 없는 것으로 추정됨(threshold=0.5가 사실상 2줄에만 훨씬 가혹하게 적용되는 셈).
# 이 함수는 모델을 다시 돌리지 않고(이미 ocr_plate가 계산한 crnn_raw_conf를 그대로
# 받아서) 전처리 캔버스 크기만으로 T를 추정함 - CNN이 MaxPool2d(2,2) 두 번으로
# 폭을 1/4로 줄이는 구조라 T ≈ 캔버스 내용폭(흰 여백 제외) // 4.
# ocr_plate()/CRNNRecognizer.recognize()의 실제 채택 로직은 전혀 안 건드림 - 이
# 값은 batch_test_images.py가 CSV에 진단 컬럼으로만 남겨서, 767장 재실행 후
# "확신도를 T로 기하평균 정규화하면(conf ** (1/T)) 2줄 정확도가 실제로 오르는지
# (오답은 안 느는지)"를 diagnose_crnn_threshold.py와 같은 방식(CSV로 임계값
# 시뮬레이션 후 확인되면만 반영)으로 검증하기 위한 용도.
def estimate_timesteps(crop_bgr, box_ratio=None, pad_ratio=None, pad_bottom_ratio=None):
    is_two_line = guess_line_count(crop_bgr, box_ratio=box_ratio) == 2
    inner_range = None
    if pad_ratio is not None and pad_bottom_ratio is not None:
        total = pad_ratio + 1.0 + pad_bottom_ratio
        inner_range = (pad_ratio / total, (pad_ratio + 1.0) / total)
    gray = cv2.cvtColor(crop_bgr, cv2.COLOR_BGR2GRAY)
    if is_two_line:
        gray = unwrap_two_line(gray, inner_range=inner_range)
    h, w = gray.shape[:2]
    scale = IMG_H / max(h, 1)
    content_w = min(IMG_W, max(1, int(w * scale)))
    t = content_w // 2 // 2  # MaxPool2d(2,2) x2 (CRNN.cnn 구조 참고)
    return max(t, 1)


def normalized_confidence(raw_conf, timesteps):
    """raw_conf(정규화 안 된 T-스텝 결합확률)를 타임스텝 수로 기하평균 정규화함
    (conf ** (1/T)) - estimate_timesteps() 주석 참고. raw_conf가 없거나 0 이하면
    그대로 반환(진단용, None/0 안전 처리)."""
    if raw_conf is None or raw_conf <= 0 or not timesteps:
        return raw_conf
    return raw_conf ** (1.0 / timesteps)


class PositionalEncoding(nn.Module):
    def __init__(self, d_model, max_len=200):
        super().__init__()
        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float32).unsqueeze(1)
        div_term = torch.exp(torch.arange(0, d_model, 2).float() * (-np.log(10000.0) / d_model))
        pe[:, 0::2] = torch.sin(position * div_term)
        pe[:, 1::2] = torch.cos(position * div_term)
        self.register_buffer("pe", pe.unsqueeze(0))

    def forward(self, x):
        return x + self.pe[:, : x.size(1), :]


class CRNN(nn.Module):
    """train_ocr_recognizer_attn.py의 CRNN과 완전히 동일한 구조여야 함."""

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
        self.pos_enc = PositionalEncoding(512)
        self.attn = nn.MultiheadAttention(embed_dim=512, num_heads=4, dropout=0.1, batch_first=True)
        self.attn_norm = nn.LayerNorm(512)
        self.dropout = nn.Dropout(0.3)
        self.fc = nn.Linear(512, num_classes + 1)

    def forward(self, x):
        feat = self.cnn(x)
        feat = self.height_pool(feat)
        feat = feat.squeeze(2)
        feat = feat.permute(0, 2, 1)
        rnn_out, _ = self.rnn(feat)
        pos = self.pos_enc(rnn_out)
        attn_out, _ = self.attn(pos, pos, pos)
        out = self.attn_norm(rnn_out + attn_out)
        out = self.dropout(out)
        out = self.fc(out)
        return out.permute(1, 0, 2)


def greedy_decode_with_conf(logits, idx_to_char):
    probs = F.softmax(logits, dim=2)
    max_probs, max_idx = probs.max(dim=2)
    max_probs = max_probs.permute(1, 0)
    max_idx = max_idx.permute(1, 0)

    texts, confs = [], []
    for b in range(max_idx.shape[0]):
        chars, char_probs = [], []
        prev = -1
        for t in range(max_idx.shape[1]):
            p = int(max_idx[b, t].item())
            prob = float(max_probs[b, t].item())
            if p != prev and p != 0:
                chars.append(idx_to_char[p])
                char_probs.append(prob)
            prev = p
        texts.append("".join(chars))
        confs.append(sum(char_probs) / len(char_probs) if char_probs else 0.0)
    return texts, confs


def _select_best_valid_region(ranked, idx_to_char, region_lexicon):
    # 2026-09-29: digit-drop 오답 3건(경남72바4552->452, 경남82사3996->396,
    # 경남99사8551->851)을 까보니 전부 "같은 숫자가 연속"인 자리(55,99,55)에서
    # CTC가 그 사이에 blank를 확신 있게 못 찍어 둘이 하나로 뭉개진 경우였음(9자리여야
    # 할 게 8자리로 나옴). CTC 그리디/빔 디코드 자체는 표준 알고리즘이라 여기서
    # 고칠 버그가 아니고, 디코더 레벨에서 할 수 있는 건 "beam 2~10위 중에 blank가
    # 제대로 들어가서 자릿수가 맞는 후보가 있으면 그걸 쓰는 것" 뿐임 - 이미 지역명만
    # 검사하던 이 함수에 전체 문법(_is_valid_plate_format: 9/8/7자리 정규식) 검사를
    # 추가함. 1위 후보가 이미 정상 형식이면 결과 완전히 동일(no-op), 1위가 깨졌을
    # 때만 더 아래 순위에서 정상 형식+지역명 후보를 찾아 그걸 반환함 - 그래도 하나도
    # 없으면(전부 형식이 깨짐) 기존과 동일하게 1위를 그대로 반환하므로 최악의 경우에도
    # 이전 동작보다 나빠지지 않음.
    for prefix, score in ranked:
        text = "".join(idx_to_char[i] for i in prefix)
        if not _is_valid_plate_format(text):
            continue
        if region_lexicon and len(text) == 9 and text[:2] not in region_lexicon:
            continue
        return prefix, score
    return ranked[0]


def ctc_beam_search_decode(logits, idx_to_char, beam_width=10, prune_top_k=15,
                            region_lexicon=VALID_PLATE_REGIONS):
    # 2026-09-27 (v3.14): v3.13에서 10->12/15->18로 넓혀서 767장 실측했더니 정확히
    # 일치 644장(84.0%)/판독불가 58장(7.6%)/오답 65장(8.5%) - v3.12와 숫자가 완전히
    # 똑같았음(효과 0). 이득이 전혀 없는데 이미지당 연산량만 늘리는 거라 원래
    # 값(10/15)으로 되돌림 - 처리 속도를 우선시하는 방침에 맞춤.
    probs = F.softmax(logits, dim=2).detach().cpu().numpy()
    T, B, C = probs.shape
    texts, confs, alt_infos = [], [], []
    for b in range(B):
        p = probs[:, b, :]
        beams = {(): (1.0, 0.0)}
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
                    if c == 0:
                        add(prefix, p_tot * pr, 0.0)
                    elif c == last_char_idx:
                        add(prefix, 0.0, p_nb * pr)
                        add(prefix + (c,), 0.0, p_b * pr)
                    else:
                        add(prefix + (c,), 0.0, p_tot * pr)
            beams = dict(sorted(new_beams.items(), key=lambda kv: -(kv[1][0] + kv[1][1]))[:beam_width])
        ranked = sorted(beams.items(), key=lambda kv: -(kv[1][0] + kv[1][1]))
        best_prefix, (p_b, p_nb) = _select_best_valid_region(ranked, idx_to_char, region_lexicon)
        texts.append("".join(idx_to_char[i] for i in best_prefix))
        confs.append(p_b + p_nb)
        alt_infos.append(_find_confusable_alt(ranked, idx_to_char, best_prefix))
    return texts, confs, alt_infos


def _sharpen_variant(crop_bgr):
    """언샵 마스크로 가장자리를 살짝 강화한 변형 - CRNN 입력용 크롭 화질 개선
    (2026-09-29). digit-drop 오류(예: 4552->452)의 원인 중 하나가 인접한 같은
    숫자 사이 경계가 흐릿해서 모델이 blank를 확신 있게 못 찍는 것으로 진단됨
    (_select_best_valid_region 주석 참고) - 경계를 선명하게 하면 도움이 될 수
    있다는 가설. 기존 TTA(회전/밝기)와 똑같이 목록에 하나 더 추가하는 것뿐이라
    아래 _combine_tta_results의 다수결 로직이 그대로 안전망 역할을 함 - 이
    변형이 나쁜 결과를 내도 다수결에서 소수 의견으로 묻히거나(과반 필요),
    다수결이 안 갈리면 기존과 동일한 폴백(형식유효 후보 중 최고 확신도)이
    그대로 유지되므로 최악의 경우에도 이전 동작보다 나빠지지 않음."""
    blurred = cv2.GaussianBlur(crop_bgr, (0, 0), sigmaX=1.0)
    return cv2.addWeighted(crop_bgr, 1.5, blurred, -0.5, 0)


def _tta_variants(crop_bgr):
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
    variants.append(_sharpen_variant(crop_bgr))
    return variants


def _combine_tta_results(results):
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
    # 2026-09-27 (v3.13): 다수결이 안 갈릴 때(동률이거나 5개 변형이 전부 다름) 예전엔
    # 그냥 신뢰도 최고인 후보를 뽑았음 - 그런데 신뢰도가 가장 높은 변형이 형식 자체가
    # 깨진(자릿수/글자위치 오류) 오인식이고, 신뢰도가 조금 낮은 다른 TTA 변형은 형식이
    # 맞는 경우가 실측에서 확인됨. 형식이 깨진 텍스트는 100% 오답이 확정이므로 버리는
    # 게 항상 이득임. 단, 비교는 "같은 CRNN 모델의 TTA 변형끼리만" 하고(EasyOCR과는
    # 절대 비교 안 함 - v3.8에서 그렇게 했다가 전체 인식률이 떨어져서 되돌린 전례
    # 있음) 형식 유효 후보가 하나도 없으면 기존 로직(전체 최고 신뢰도)과 완전히
    # 동일하게 동작해서 최악의 경우에도 이전 동작보다 나빠지지 않음.
    valid_results = [(t, c) for t, c in results if _is_valid_plate_format(t)]
    if valid_results:
        return max(valid_results, key=lambda tc: tc[1] if tc[1] is not None else -1.0)
    return max(results, key=lambda tc: tc[1] if tc[1] is not None else -1.0)


class CRNNRecognizer:
    """plate_ocr_crnn.py의 CRNNRecognizer와 인터페이스(recognize 시그니처)는 동일함 -
    verify_integration_attn.py나 plate_detector_gui.py에서 import만 바꾸면 그대로
    바꿔 끼울 수 있음."""

    def __init__(self):
        self.model = None
        self.idx_to_char = None
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.loaded = False
        # 2026-09-29: recognize() 반환값(text, conf) 튜플 형태는 plate_detector_gui.py
        # 등 여러 호출부가 이미 그대로 언패킹해서 쓰고 있어서 건드리면 파급 범위가
        # 큼 - 그래서 시그니처는 그대로 두고, "확인필요" 참고 정보(_find_confusable_alt
        # 결과)만 이 속성에 곁다리로 남겨서 호출부가 필요할 때만 골라서 보게 함.
        # recognize() 호출마다 새로 채워짐 - 이전 호출 결과가 남아있지 않음.
        self.last_alt_info = None
        self._pending_alt_by_text = {}

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

    def recognize(self, crop_bgr, tta=False, decode="greedy", box_ratio=None,
                  pad_ratio=None, pad_bottom_ratio=None):
        """pad_ratio/pad_bottom_ratio: 2026-09-28 추가 - 호출부가 _padded_crop에
        준 여백 비율을 그대로 넘겨주면, 2줄 번호판을 위/아래로 나눌 때 원본(여백
        더하기 전) YOLO 박스가 이 crop 안에서 세로로 어디부터 어디까지인지
        계산해서 unwrap_two_line에 넘김(위 unwrap_two_line 위 주석 참고) - 안
        넘기면(하위 호환) crop 전체 기준으로 나눔.

        2026-09-29 추가(LINE_COUNT_GRAY_ZONE): 006너9793(지역명 없는 8자리, 항상
        1줄이 정답) 실패 사례 분석 - 라벨 기준 실제 박스 비율은 2.99(컷오프 2.8
        기준 1줄이 맞음)인데, YOLO가 실제로 검출한 박스는 미세한 오차로 2.8 밑으로
        떨어져 2줄로 오판단됨 -> unwrap_two_line이 실제로는 1줄인 이미지를 억지로
        위/아래로 잘라 CRNN이 아예 못 읽음(원본 추측 "부산80자8302", 확신도 0.025).
        box_ratio가 컷오프(2.8) 근처(±0.5)일 때만 반대쪽 줄수 해석도 TTA 후보에
        추가해서, 이미 검증된 결합 로직(_combine_tta_results: 다수결->형식유효->
        최고신뢰도)이 고르게 함 - 이 구간 밖(대다수 이미지)은 기존과 완전히 동일
        (회귀 없음), tta=False 경로도 전혀 안 건드림."""
        self.last_alt_info = None
        self._pending_alt_by_text = {}
        if not self.loaded or crop_bgr is None or crop_bgr.size == 0:
            return "", None
        if not tta:
            text, conf = self._recognize_single(
                crop_bgr, decode=decode, box_ratio=box_ratio,
                pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio,
            )
            self.last_alt_info = self._pending_alt_by_text.get(text)
            return text, conf
        results = [
            self._recognize_single(
                v, decode=decode, box_ratio=box_ratio,
                pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio,
            )
            for v in _tta_variants(crop_bgr)
        ]
        if box_ratio is not None and abs(box_ratio - 2.8) <= 0.5:
            current_is_two = guess_line_count(crop_bgr, box_ratio=box_ratio) == 2
            results.append(
                self._recognize_single(
                    crop_bgr, decode=decode, box_ratio=box_ratio,
                    pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio,
                    force_two_line=not current_is_two,
                )
            )
        results = [(t, c) for t, c in results if t]
        winner_text, winner_conf = _combine_tta_results(results)
        self.last_alt_info = self._pending_alt_by_text.get(winner_text)
        return winner_text, winner_conf

    def _recognize_single(self, crop_bgr, decode="greedy", box_ratio=None,
                           pad_ratio=None, pad_bottom_ratio=None, force_two_line=None):
        try:
            h, w = crop_bgr.shape[:2]
            if h == 0 or w == 0:
                return "", None
            if force_two_line is not None:
                is_two_line = force_two_line
            else:
                is_two_line = guess_line_count(crop_bgr, box_ratio=box_ratio) == 2
            inner_range = None
            if pad_ratio is not None and pad_bottom_ratio is not None:
                total = pad_ratio + 1.0 + pad_bottom_ratio
                inner_range = (pad_ratio / total, (pad_ratio + 1.0) / total)
            canvas = preprocess_for_model(crop_bgr, is_two_line, inner_range=inner_range)
            tensor = (
                torch.from_numpy(canvas).float().unsqueeze(0).unsqueeze(0).to(self.device) / 255.0
            )
            with torch.no_grad():
                logits = self.model(tensor)
            if decode == "beam":
                texts, confs, alt_infos = ctc_beam_search_decode(logits, self.idx_to_char)
                if alt_infos[0] is not None and texts[0] not in self._pending_alt_by_text:
                    self._pending_alt_by_text[texts[0]] = alt_infos[0]
            else:
                texts, confs = greedy_decode_with_conf(logits, self.idx_to_char)
            return texts[0], confs[0]
        except Exception:
            return "", None
