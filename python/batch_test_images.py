# -*- coding: utf-8 -*-
"""
batch_test_images.py
====================================================
test/images 폴더(파일명이 실제 번호판인 사진들)를 전부 돌려서 지금 앱(v3.10)과
똑같은 로직으로 검출+인식하고, 파일명(정답)과 비교해서 정확도를 계산함.

목적: 스크린샷 몇 장으로 감으로 판단하지 말고, 화요일에 실제로 보여줄 사진들
전체에 대해 진짜 숫자로 확인하기 위함. GUI를 켜지 않고 커맨드라인에서 실행.

실행:
    python batch_test_images.py

결과:
    - 화면에 요약(전체/정확히 맞음/판독불가/오답) 출력
    - batch_test_result.csv 에 파일별 상세 결과 저장 (정답, 예측, 검출확신도,
      글자인식률, 맞았는지 여부) - 엑셀로 열어서 어떤 사진이 틀렸는지 바로 확인 가능
"""
import os

# torch/numpy가 서로 다른 OpenMP 런타임(libiomp5md.dll)을 각자 끌고 들어와서 충돌하는
# 흔한 Windows/conda 환경 문제 - 다른 라이브러리 import보다 반드시 먼저 설정해야 함.
# setdefault가 아니라 강제로 덮어씀 - conda 환경에 이 값이 이미 빈 문자열 등으로
# 설정되어 있으면 setdefault는 그 값을 안 건드려서(이미 "있는" 값으로 취급) 효과가 없음.
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import csv
import functools
import re
import sys
from pathlib import Path

import cv2
import numpy as np
import torch


def _exe_dir():
    """plate_detector_gui.py의 _exe_dir()와 동일한 이유/방식(2026-09-29 추가) -
    PyInstaller onedir로 빌드된 exe에서 이 모듈이 gate_watch_service.py 등에
    import되어 실행될 때도 runs/detect, ocr_model_attn 같은(빌드에 안 묶이고
    exe 옆에 따로 두는) 폴더를 exe가 실제로 있는 최상위 폴더 기준으로 정확히
    찾기 위함. 일반 파이썬 스크립트로 실행할 때(frozen=False)는 기존과 완전히
    동일하게 동작함(__file__ 기준) - 동작 변화 없음."""
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


APP_DIR = _exe_dir()
RUNS_DIR = APP_DIR / "runs" / "detect"
OCR_MODEL_DIR = APP_DIR / "ocr_model_attn"
CRNN_MODEL_PATH = OCR_MODEL_DIR / "best_recognizer_attn.pth"
CRNN_CHARS_PATH = OCR_MODEL_DIR / "chars_attn.json"
# test/images(254장)만 썼을 때 "그거밖에 안 되냐"는 지적을 받아 valid/images(1081장)도
# 추가함 - train/images(2000장)는 일부러 뺐음: YOLO가 학습 때 이미 본 사진이라
# 거기서 측정하면 실제보다 부풀려진(외운) 정확도가 나와서 공정한 테스트가 아님.
# valid는 학습 가중치 업데이트에는 안 쓰이고 에폭 선택에만 쓰여서(train보다는) 훨씬
# 공정한 기준임 - 그래도 test처럼 완전히 순수하진 않을 수 있어 둘을 폴더별로 나눠서 출력함.
IMAGE_DIRS = [APP_DIR / "test" / "images", APP_DIR / "valid" / "images"]

CRNN_CONF_THRESHOLD = 0.5  # plate_detector_gui.py 와 동일 (v3.10 기준)
YOLO_CONF = 0.25           # 검출 하한 - 낮은 확신도 박스도 다 포함해서 인식률을 보기 위함
YOLO_IOU = 0.5

VALID_PLATE_REGIONS = frozenset([
    "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
    "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
])
_PLATE_RE_WITH_REGION = re.compile(r"^(" + "|".join(VALID_PLATE_REGIONS) + r")\d{2}[가-힣]\d{4}$")
_PLATE_RE_NO_REGION = re.compile(r"^\d{3}[가-힣]\d{4}$")
_PLATE_RE_SHORT_NO_CODE = re.compile(r"^\d{2}[가-힣]\d{4}$")

# 파일명에서 정답 번호판만 뽑아내기 위한 패턴(지역명 있는/없는 버전 다 허용, 공백 제거)
_GT_RE = re.compile(
    r"^(?:(?:" + "|".join(VALID_PLATE_REGIONS) + r")\d{2}|\d{2,3})[가-힣]\d{4}$"
)


def _is_valid_plate_format(text):
    if not text:
        return False
    return bool(
        _PLATE_RE_WITH_REGION.match(text)
        or _PLATE_RE_NO_REGION.match(text)
        or _PLATE_RE_SHORT_NO_CODE.match(text)
    )


def _edit_distance(a, b):
    m, n = len(a), len(b)
    dp = [[0] * (n + 1) for _ in range(m + 1)]
    for i in range(m + 1):
        dp[i][0] = i
    for j in range(n + 1):
        dp[0][j] = j
    for i in range(1, m + 1):
        for j in range(1, n + 1):
            cost = 0 if a[i - 1] == b[j - 1] else 1
            dp[i][j] = min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
    return dp[m][n]


def clean_plate_text(text):
    """plate_detector_gui.py의 _clean_plate_text와 동일 - 괄호/기호 제거 +
    지역명 앞 2글자 편집거리1 교정."""
    if not text:
        return text
    cleaned = re.sub(r"[^\w가-힣0-9]", "", text)
    if len(cleaned) >= 2:
        prefix = cleaned[:2]
        if prefix not in VALID_PLATE_REGIONS:
            candidates = [r for r in VALID_PLATE_REGIONS if _edit_distance(prefix, r) == 1]
            if len(candidates) == 1:
                cleaned = candidates[0] + cleaned[2:]
    letter_idx = {9: 4, 8: 3, 7: 2}.get(len(cleaned))
    if letter_idx is not None and cleaned[letter_idx] == "0":
        candidate = cleaned[:letter_idx] + "아" + cleaned[letter_idx + 1:]
        if _is_valid_plate_format(candidate):
            cleaned = candidate
    return cleaned


def _is_plausible_plate_text(text):
    if not text:
        return False
    has_hangul = any("가" <= ch <= "힣" for ch in text)
    digit_count = sum(ch.isdigit() for ch in text)
    return has_hangul and digit_count >= 3


def extract_ground_truth(filename_stem):
    """파일명(확장자 제외)에서 실제 번호판 정답을 뽑아냄. 로보플로우 자동생성 이름
    (예: '-89-1996_jpg.rf.xxxx')이나 뽑아내지 못하는 이름은 None을 반환해서 건너뜀."""
    name = filename_stem.replace(" ", "")
    name = re.sub(r"_truck\d*$", "", name)  # '_truck2' 같은 접미사 제거
    if _GT_RE.match(name):
        return name
    return None


def _read_run_metrics(run_dir: Path):
    csv_path = run_dir / "results.csv"
    if not csv_path.exists():
        return None
    try:
        with open(csv_path, newline="", encoding="utf-8") as f:
            rows = list(csv.DictReader(f))
        if not rows:
            return None
        best_row = max(rows, key=lambda r: float(r["metrics/mAP50-95(B)"]))
        return {"map5095": float(best_row["metrics/mAP50-95(B)"])}
    except Exception:
        return None


def _read_run_imgsz(run_dir: Path):
    yaml_path = run_dir / "args.yaml"
    if not yaml_path.exists():
        return None
    try:
        import yaml
        with open(yaml_path, encoding="utf-8") as f:
            args = yaml.safe_load(f)
        imgsz = args.get("imgsz") if isinstance(args, dict) else None
        return int(imgsz) if imgsz else None
    except Exception:
        return None


def pick_best_model():
    """plate_detector_gui.py의 _load_model_list와 동일한 기준(mAP50-95, 근소한
    차이는 최신 모델 우선)으로 지금 GUI가 기본으로 쓰는 모델과 같은 걸 고름."""
    models = {}
    for w in sorted(RUNS_DIR.glob("*/weights/best.pt")):
        run_dir = w.parent.parent
        models[run_dir.name] = {
            "path": str(w),
            "metrics": _read_run_metrics(run_dir),
            "imgsz": _read_run_imgsz(run_dir),
            "mtime": w.stat().st_mtime,
        }
    if not models:
        raise SystemExit(f"{RUNS_DIR} 에서 학습된 모델을 찾지 못함")

    TIE_TOLERANCE = 0.01

    def _cmp(a, b):
        m_a, m_b = a[1]["metrics"], b[1]["metrics"]
        if (m_a is None) != (m_b is None):
            return -1 if m_a is not None else 1
        if m_a is None and m_b is None:
            return 0
        diff = m_a["map5095"] - m_b["map5095"]
        if abs(diff) > TIE_TOLERANCE:
            return -1 if diff > 0 else 1
        mt = a[1]["mtime"] - b[1]["mtime"]
        return -1 if mt > 0 else (1 if mt < 0 else 0)

    ordered = sorted(models.items(), key=functools.cmp_to_key(_cmp))
    name, info = ordered[0]
    print(f"[모델 선택] {name}  (mAP50-95={info['metrics']['map5095'] if info['metrics'] else 'N/A'}, imgsz={info['imgsz'] or 960})")
    return info["path"], (info["imgsz"] or 960)


def adaptive_imgsz(img, base_imgsz):
    """2026-09-28: 767장 중 .jpg(소문자) 확장자 29장(4624x3468, 스마트폰 풀해상도 -
    나머지 738장은 거의 다 1536x2048)만 따로 보면 정확히 일치 20.7%, 검출 자체
    실패도 3건 - 나머지 99.4%(89.3%)와 완전히 다른 모집단으로 확인됨(CHANGELOG
    2026-09-28 참고). 실제로 열어보면 다 노란 영업용 번호판 사진이라 색상/구도가
    학습 데이터 주류(흰색 자가용, 전면 촬영)와 다르긴 하지만, 원본 해상도가
    base_imgsz(보통 960)의 4~5배라서 YOLO 추론용으로 리사이즈되는 순간 번호판이
    장변 기준 4~5배 더 작아지는 것도 검출확신도를 깎는 별개의 요인일 수 있음 -
    이 함수는 그 부분만 저비용으로 검증하기 위한 것: 원본 장변이 base_imgsz의
    3배를 넘는 사진만(=이 29장 같은 초고해상도) 추론용 imgsz를 2배로 키움
    (YOLO가 요구하는 32의 배수로 반올림). 대다수(원본이 크지 않은 사진)는
    base_imgsz 그대로라 기존 동작과 완전히 동일 - 이 29장 같은 소수의
    초고해상도 사진에서만 연산량이 늘어남.

    2026-09-29 수정: 최초 버전은 기준을 base_imgsz*2(예: 1920)로 잡았는데,
    나머지 738장(1536x2048, 장변 2048)도 이미 1920을 넘어서 사실상 전체
    데이터셋의 imgsz가 다 바뀌어버리는 버그가 있었음 - 실측 결과 767장
    기준 86.7%(1줄 89.5%/2줄 84.2%)에서 83.4%(1줄 84.1%/2줄 84.7%)로
    오히려 악화됨(특히 1줄이 -5.4%p로 가장 크게 깨짐). 정상 모집단(장변
    ~2048)은 그대로 두고 진짜 초고해상도 이상치(장변 ~4624)만 걸러내도록
    기준을 base_imgsz*3(예: 2880)으로 올림 - 정상 모집단은 다시 기존과
    동일하게 base_imgsz 그대로 사용됨. 이 수정판으로 재실측 필요."""
    h, w = img.shape[:2]
    long_side = max(h, w)
    if long_side <= base_imgsz * 3:
        return base_imgsz
    target = base_imgsz * 2
    return int(((target + 31) // 32) * 32)  # YOLO는 stride(32)의 배수 요구


def padded_crop(cv_img, x1, y1, x2, y2, pad_ratio=0.18, pad_bottom_ratio=None):
    h_img, w_img = cv_img.shape[:2]
    bw, bh = x2 - x1, y2 - y1
    pad_x = max(5.0, bw * pad_ratio)
    pad_y_top = max(5.0, bh * pad_ratio)
    pad_y_bottom = max(5.0, bh * (pad_bottom_ratio if pad_bottom_ratio is not None else pad_ratio))
    px1 = max(0, int(x1 - pad_x))
    py1 = max(0, int(y1 - pad_y_top))
    px2 = min(w_img, int(x2 + pad_x))
    py2 = min(h_img, int(y2 + pad_y_bottom))
    return cv_img[py1:py2, px1:px2].copy()


def enhance_low_conf_crop(crop_bgr):
    gray = cv2.cvtColor(crop_bgr, cv2.COLOR_BGR2GRAY)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    enhanced = clahe.apply(gray)
    blurred = cv2.GaussianBlur(enhanced, (0, 0), sigmaX=1.0)
    sharpened = cv2.addWeighted(enhanced, 1.5, blurred, -0.5, 0)
    return cv2.cvtColor(sharpened, cv2.COLOR_GRAY2BGR)


def order_ocr_fragments(raw):
    if not raw:
        return "", None
    filtered = [item for item in raw if item[2] >= 0.2]
    items = filtered if filtered else raw
    ocr_conf = sum(it[2] for it in items) / len(items)

    def y_center(bbox):
        return sum(p[1] for p in bbox) / len(bbox)

    def x_center(bbox):
        return sum(p[0] for p in bbox) / len(bbox)

    def box_height(bbox):
        ys = [p[1] for p in bbox]
        return max(ys) - min(ys)

    avg_h = sum(box_height(it[0]) for it in items) / len(items) or 1.0
    items_by_y = sorted(items, key=lambda it: y_center(it[0]))
    rows = []
    for it in items_by_y:
        placed = False
        for row in rows:
            if abs(y_center(it[0]) - y_center(row[0][0])) < avg_h * 0.6:
                row.append(it)
                placed = True
                break
        if not placed:
            rows.append([it])
    rows.sort(key=lambda row: min(y_center(it[0]) for it in row))
    line_texts = []
    for row in rows:
        row.sort(key=lambda it: x_center(it[0]))
        line_texts.append(" ".join(it[1] for it in row))
    return " ".join(line_texts).strip(), ocr_conf


def ocr_once(ocr_reader, crop, alt=False):
    if ocr_reader is None or crop is None or crop.size == 0:
        return "", None
    try:
        gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
        h, w = gray.shape[:2]
        target_h = 200
        if h < target_h:
            scale = target_h / max(h, 1)
            gray = cv2.resize(gray, (max(1, int(w * scale)), target_h), interpolation=cv2.INTER_CUBIC)
        if alt and float(gray.mean()) < 100.0:
            gamma = 1.8
            inv_gamma = 1.0 / gamma
            lut = np.array([((i / 255.0) ** inv_gamma) * 255 for i in range(256)]).astype("uint8")
            gray = cv2.LUT(gray, lut)
        clip_limit = 4.0 if alt else 2.0
        clahe = cv2.createCLAHE(clipLimit=clip_limit, tileGridSize=(8, 8))
        gray = clahe.apply(gray)
        blurred = cv2.GaussianBlur(gray, (0, 0), sigmaX=1.2)
        gray = cv2.addWeighted(gray, 1.5, blurred, -0.5, 0)
        if alt:
            raw = ocr_reader.readtext(
                gray, detail=1, paragraph=False, text_threshold=0.4, low_text=0.3,
                link_threshold=0.3, decoder="beamsearch", beamWidth=5,
            )
        else:
            raw = ocr_reader.readtext(
                gray, detail=1, paragraph=False, text_threshold=0.4, low_text=0.3, link_threshold=0.3
            )
        return order_ocr_fragments(raw)
    except Exception:
        return "", None


def ocr_plate(crnn, ocr_reader, crop, crnn_conf_threshold, box_ratio=None,
              pad_ratio=None, pad_bottom_ratio=None):
    """CRNN 확신도 이상이면 즉시 채택. 미달이면 EasyOCR(+저조도 재시도)로 폴백하되,
    2026-09-28: 폴백 결과는 신뢰도가 낮아 호출부(main())에서 clean_plate_text로
    지역명 교정을 거친 뒤 엄격한 번호판 문법(_is_valid_plate_format)을 통과한
    것만 채택하고, 안 맞으면 판독불가로 버림 - engine 태그("easyocr"/"easyocr_alt")로
    CRNN 결과("crnn")와 구분해서 그 필터를 CRNN 결과에는 절대 안 걸리게 함.
    767장 실측(batch_test_result.csv)으로 확인: EasyOCR 폴백이 실제로 쓰인 65건
    (판독불가 제외) 중 정답 20건은 전부(100%) 이 문법을 통과했고, 오답 45건 중
    34건(76%)은 애초에 문법부터 안 맞는 엉터리 답이었음 - 이 필터만으로 정답은
    하나도 안 잃으면서 오답 대부분을 걸러낼 수 있음(EasyOCR을 아예 안 쓰는 것보다
    나은 절충안 - 사용자 확인, 2026-09-28). 남은 오답 11건(지역명 오인식 등 문법은
    맞지만 내용이 틀린 경우)은 이 검사로는 못 잡음 - 이 필터 적용 시 전체 기준
    완전일치는 EasyOCR을 그대로 신뢰하던 원래 수치(84.0%)를 유지하면서 오답만
    8.5%->4.0%로 줄고 판독불가가 7.6%->12.0%로 늘어나는 걸로 추정됨.
    이 문법 필터는 engine!="crnn"이면(crnn이 아예 없어서 처음부터 EasyOCR만
    쓴 경우 포함) 항상 적용함 - CRNN 결과만 절대 안 건드림.

    2026-09-28 추가: 2줄 번호판은 CRNN 자체 정확도(확신도 통과했을 때 96.8%)는
    1줄(96.9%)과 거의 같은데, 확신도가 임계값(0.5)을 못 넘어 폴백으로 빠지는
    비율이 1줄(7.6%)보다 훨씬 높음(27%) - 즉 "틀려서"가 아니라 "확신도 계산이
    박해서" 2줄 전체 정확도가 낮게 나올 가능성이 있음. 이걸 확인하려고 임계값
    미달이어도 CRNN이 뭐라고 읽었는지/확신도가 얼마였는지를 항상 같이 반환함
    (반환값 뒤에 crnn_text, crnn_conf 추가) - main()에서 CSV에 그대로 남겨서,
    2줄에 한해 임계값을 낮추면 정답률이 오르는지(오답은 안 느는지) 사후 분석할
    수 있게 함. 실제 폴백 채택 로직 자체는 안 바꿈(임계값은 여전히 0.5 그대로).

    2026-09-29: CRNN 확신도 통과 시에도 EasyOCR을 상시 대조해서 3자 다수결로
    뒤집는 방식을 시도했다가 실측(767장) 후 되돌림 - CHANGELOG.md의 "CRNN
    확신도 통과 시에도 EasyOCR 상시 대조... 실측 후 되돌림" 항목 참고. 원인:
    EasyOCR 본선/alt는 같은 리더기(다른 전처리 파라미터일 뿐)라 서로 "독립된
    두 표"가 아니었음 - 1줄 번호판에서 지역명 글자를 둘 다 똑같이 못 읽고
    숫자만 반환하는 공통 약점을 공유했는데, 그 "지역명 없는" 형태도 옛 번호판
    문법(_PLATE_RE_NO_REGION)과 우연히 맞아떨어져 문법 검사를 통과해버림 ->
    두 EasyOCR이 "일치"할 때마다 다수결로 이겨서, CRNN이 지역명까지 맞게 읽은
    정답 73건을 오답으로 뒤집음(1줄 정확도 89.5%->75.3% 급락, 전체 87.4%->78.4%).
    코드는 이 실패 이전 버전(CRNN 확신도 임계값 통과 시 즉시 채택)으로 유지."""
    crnn_text = crnn_conf = None
    if crnn is not None:
        crnn_text, crnn_conf = crnn.recognize(
            crop, tta=True, decode="beam", box_ratio=box_ratio,
            pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio,
        )
        if crnn_text and crnn_conf is not None and crnn_conf >= crnn_conf_threshold:
            return crnn_text, crnn_conf, "crnn", crnn_text, crnn_conf

    text, ocr_conf = ocr_once(ocr_reader, crop, alt=False)
    if text and ocr_conf is not None and ocr_conf >= 0.65:
        return text, ocr_conf, "easyocr", crnn_text, crnn_conf
    alt_text, alt_conf = ocr_once(ocr_reader, crop, alt=True)
    if not alt_text:
        return text, ocr_conf, "easyocr", crnn_text, crnn_conf
    if not text or ocr_conf is None or (alt_conf is not None and alt_conf > ocr_conf):
        return alt_text, alt_conf, "easyocr_alt", crnn_text, crnn_conf
    return text, ocr_conf, "easyocr", crnn_text, crnn_conf


def main():
    from ultralytics import YOLO
    import easyocr
    from plate_ocr_crnn_attn import (
        CRNNRecognizer,
        estimate_timesteps,
        guess_line_count,
        normalized_confidence,
    )

    model_path, imgsz = pick_best_model()
    model = YOLO(model_path)

    crnn = None
    if CRNN_MODEL_PATH.exists() and CRNN_CHARS_PATH.exists():
        crnn = CRNNRecognizer()
        crnn.load(str(CRNN_MODEL_PATH), str(CRNN_CHARS_PATH))
        print(f"[CRNN] 로드 완료: {CRNN_MODEL_PATH.name}")
    else:
        print("[CRNN] 가중치를 못 찾음 - EasyOCR만 사용")

    use_gpu = torch.cuda.is_available()
    print(f"[EasyOCR] GPU 사용: {use_gpu} - 로딩 중(시간 좀 걸림)...")
    ocr_reader = easyocr.Reader(["ko", "en"], gpu=use_gpu)

    rows = []
    # split별로 따로 집계(터미널 요약에 test/valid 나눠서 보여주기 위함) + 전체 합계
    stats = {}  # split -> {"total":.., "exact":.., "unread":.., "wrong":..}

    for split_dir in IMAGE_DIRS:
        split_name = split_dir.parent.name  # "test" or "valid"
        if not split_dir.exists():
            print(f"[건너뜀] {split_dir} 없음")
            continue
        stats[split_name] = {"total": 0, "exact": 0, "unread": 0, "wrong": 0, "no_detect": 0}
        files = sorted(split_dir.glob("*"))

        for path in files:
            if path.suffix.lower() not in (".jpg", ".jpeg", ".png"):
                continue
            gt = extract_ground_truth(path.stem)
            if gt is None:
                continue  # 정답을 알 수 없는(로보플로우 자동생성 등) 파일은 평가에서 제외
            stats[split_name]["total"] += 1

            img = cv2.imread(str(path))
            if img is None:
                rows.append([
                    split_name, path.name, gt, "", "", "", "이미지 로드 실패", "", "", "", "", "", "", "", "",
                ])
                continue

            img_imgsz = adaptive_imgsz(img, imgsz)
            result = model.predict(source=img, conf=YOLO_CONF, iou=YOLO_IOU, imgsz=img_imgsz, verbose=False)[0]
            boxes_xyxy = result.boxes.xyxy.cpu().numpy()
            boxes_conf = result.boxes.conf.cpu().numpy()
            if len(boxes_xyxy) == 0:
                rows.append([
                    split_name, path.name, gt, "", "", "", "검출 실패", "", "", "", "", "", "",
                    str(img_imgsz), "",
                ])
                stats[split_name]["unread"] += 1
                stats[split_name]["no_detect"] += 1
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
            # 2026-09-28: 1줄/2줄 판단은 비대칭 여백을 더하기 전 박스 자체 비율로
            # (plate_detector_gui.py와 동일한 수정 - plate_ocr_crnn_attn.guess_line_count 참고)
            box_ratio = (x2 - x1) / (y2 - y1) if (y2 - y1) > 0 else None
            crop = padded_crop(img, x1, y1, x2, y2, pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio)
            if low_conf_box:
                crop = enhance_low_conf_crop(crop)

            # 2026-09-28: 1줄/2줄 판단 결과를 CSV에 남겨서 실제로 잘 판단되는지
            # 검토할 수 있게 함(정답 형식상 지역명 없는 7/8자리는 항상 1줄이 맞음 -
            # 이 경우와 다르게 나오면 오분류로 바로 확인 가능).
            line_count = guess_line_count(crop, box_ratio=box_ratio) if crnn is not None else None

            text, ocr_conf, engine, crnn_raw_text, crnn_raw_conf = ocr_plate(
                crnn, ocr_reader, crop, CRNN_CONF_THRESHOLD, box_ratio=box_ratio,
                pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio,
            )
            # 2026-09-29: 예측값은 절대 안 바꾸고, plate_ocr_crnn_attn.CRNNRecognizer가
            # 방금 호출(recognize)에서 beam 2~10위 중 찾아둔 "혼동 쌍 대안" 참고
            # 정보만 곁다리로 꺼내서 CSV에 남김(위 ocr_plate 주석/plate_ocr_crnn_attn.py
            # 상단 CONFUSABLE_REGION_GROUPS 주석 참고) - 정확도 계산 경로에는 전혀
            # 영향 없음(diagnostic 컬럼만 추가).
            alt_info = crnn.last_alt_info if crnn is not None else None
            if text:
                text = clean_plate_text(text)
            # 2026-09-28: EasyOCR 폴백("easyocr"/"easyocr_alt")은 CRNN보다 신뢰도가
            # 낮으므로 엄격한 번호판 문법까지 통과해야만 채택 - ocr_plate() 위 docstring 참고.
            if text and engine != "crnn" and not _is_valid_plate_format(text):
                text = ""
            # 2026-09-29: plate_detector_gui.py와 동일하게 추가 - EasyOCR이 지역명을
            # 놓쳐서 우연히 "지역명 없는 8자리/7자리" 형식과 맞아떨어지는 경우를 걸러냄
            # (이번 767장 실측에서 이 패턴 9건 전부 오답이었음 - plate_detector_gui.py의
            # 해당 위치 주석 참고).
            if text and engine != "crnn":
                if len(text) == 8 and not text.startswith("006"):
                    text = ""
                elif len(text) == 7:
                    text = ""
            if text and not _is_plausible_plate_text(text):
                text = ""

            pred = text.replace(" ", "") if text else ""
            status = ""
            if not pred:
                stats[split_name]["unread"] += 1
                status = "판독불가"
            elif pred == gt:
                stats[split_name]["exact"] += 1
                status = "정답"
            else:
                stats[split_name]["wrong"] += 1
                status = "오답"

            # 2026-09-28: CRNN이 임계값 미달로 폴백했을 때도 CRNN 자신은 뭐라고
            # 읽었는지/확신도가 얼마였는지 같이 남김(ocr_plate() 위 docstring 참고) -
            # 2줄 번호판의 "확신도만 낮았지 사실 맞았던" 케이스를 찾아 임계값을
            # 조정할 근거로 쓰기 위함. engine=="crnn"이면 최종 채택 결과와 동일하므로
            # 중복 기록이지만, 분석 스크립트를 단순하게 유지하려고 그대로 둠.
            crnn_raw_correct = ""
            if crnn_raw_text:
                crnn_raw_pred = clean_plate_text(crnn_raw_text).replace(" ", "")
                crnn_raw_correct = "정답" if crnn_raw_pred == gt else "오답"

            # 2026-09-28 (2줄 번호판 확신도 진단용, plate_ocr_crnn_attn.estimate_timesteps
            # 주석 참고): 모델을 다시 안 돌리고 전처리 크기만으로 T를 추정해서, 이미
            # 계산된 crnn_raw_conf를 T로 기하평균 정규화한 값을 같이 남김. ocr_plate()의
            # 실제 채택/폴백 판단에는 전혀 영향 없음(진단 컬럼만 추가).
            t_est = estimate_timesteps(
                crop, box_ratio=box_ratio, pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio,
            ) if crnn is not None else None
            crnn_norm_conf = normalized_confidence(crnn_raw_conf, t_est)

            alt_display = ""
            if alt_info is not None:
                alt_text, alt_ratio, alt_reason = alt_info
                alt_display = f"{alt_text} ({alt_reason}, 점수비율{alt_ratio:.2f})"

            rows.append([
                split_name, path.name, gt, pred, f"{det_conf:.2f}",
                f"{ocr_conf:.2f}" if ocr_conf is not None else "", status, engine,
                str(line_count) if line_count is not None else "",
                crnn_raw_text or "", f"{crnn_raw_conf:.3f}" if crnn_raw_conf is not None else "",
                str(t_est) if t_est is not None else "",
                f"{crnn_norm_conf:.3f}" if crnn_norm_conf is not None else "",
                str(img_imgsz), alt_display,
            ])
            print(f"[{split_name}] {status:6s}  정답={gt:12s}  예측={pred or '(없음)':12s}  검출={det_conf:.2f}  [{path.name}]")

    with open(APP_DIR / "batch_test_result.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow([
            "구분", "파일명", "정답", "예측", "검출확신도", "글자인식률", "결과", "엔진", "줄수판단",
            "CRNN원본추측(임계값무관)", "CRNN확신도(임계값무관)",
            "CTC타임스텝수T(진단용)", "T정규화확신도(진단용,임계값미적용)",
            "적용된YOLO_imgsz", "확인필요(beam대안,diagnostic-예측값에영향없음)",
        ])
        w.writerows(rows)

    print("\n==================== 요약 ====================")
    total_all = exact_all = unread_all = wrong_all = 0
    for split_name, s in stats.items():
        n = s["total"]
        total_all += n
        exact_all += s["exact"]
        unread_all += s["unread"]
        wrong_all += s["wrong"]
        if n:
            print(f"[{split_name}] {n}장 - 정확히 일치 {s['exact']}장({s['exact']/n*100:.1f}%) / "
                  f"판독불가 {s['unread']}장({s['unread']/n*100:.1f}%) / 오답 {s['wrong']}장({s['wrong']/n*100:.1f}%)")
    if total_all:
        print(f"\n[전체 합계] {total_all}장 - 정확히 일치 {exact_all}장({exact_all/total_all*100:.1f}%) / "
              f"판독불가 {unread_all}장({unread_all/total_all*100:.1f}%) / 오답 {wrong_all}장({wrong_all/total_all*100:.1f}%)")
    print("(train/images 2000장은 YOLO가 학습 때 이미 외운 사진이라 일부러 뺐음 - "
          "거기 포함하면 실제보다 부풀려진 정확도가 나옴)")

    # 2026-09-29 추가: 위 '요약'의 판독불가는 "YOLO가 번호판을 못 찾은 경우"와
    # "YOLO는 찾았는데 OCR이 못 읽은 경우"가 섞여 있어서, 정확도가 떨어졌을 때
    # 검출(YOLO) 문제인지 인식(OCR) 문제인지 바로 구분이 안 됐음. 아래는 그 둘을
    # 분리해서 YOLO 검출율과, "검출된 것"만 기준으로 한 OCR 인식률을 따로 보여줌
    # (위 '요약' 수치는 전혀 안 바꿈 - 그냥 원인 분리용 보조 지표를 추가한 것).
    print("\n==================== YOLO 검출율 / OCR 인식률 (원인 분리) ====================")
    detect_all = total_all2 = exact_all2 = unread_ocr_all = wrong_all2 = 0
    for split_name, s in stats.items():
        n = s["total"]
        if not n:
            continue
        detected = n - s["no_detect"]
        unread_ocr = s["unread"] - s["no_detect"]  # 검출은 됐는데 OCR이 못 읽은 것만
        total_all2 += n
        detect_all += detected
        exact_all2 += s["exact"]
        unread_ocr_all += unread_ocr
        wrong_all2 += s["wrong"]
        ocr_pct = (s["exact"] / detected * 100) if detected else 0.0
        print(f"[{split_name:5s}] YOLO 검출 {detected:4d}/{n:4d}장({detected/n*100:5.1f}%)  "
              f"검출실패 {s['no_detect']:3d}장   ->  검출된 것 중 OCR 정답 {s['exact']:4d}/{detected:4d}장"
              f"({ocr_pct:5.1f}%)  (판독불가 {unread_ocr:3d} / 오답 {s['wrong']:3d})")
    if total_all2:
        detect_pct_all = detect_all / total_all2 * 100
        ocr_pct_all = (exact_all2 / detect_all * 100) if detect_all else 0.0
        print(f"[전체  ] YOLO 검출 {detect_all:4d}/{total_all2:4d}장({detect_pct_all:5.1f}%)  "
              f"검출실패 {total_all2 - detect_all:3d}장   ->  검출된 것 중 OCR 정답 {exact_all2:4d}/{detect_all:4d}장"
              f"({ocr_pct_all:5.1f}%)  (판독불가 {unread_ocr_all:3d} / 오답 {wrong_all2:3d})")
        print("(위 '요약'과 다른 점: 여기 OCR 정답률은 YOLO가 못 찾은 사진은 분모에서 빼고, "
              "'YOLO가 박스는 찾아준' 사진만 놓고 OCR이 얼마나 맞혔는지를 봄)")

    # 2026-09-28: 1줄/2줄 판단이 실제로 잘 되고 있는지 별도로 확인.
    # 정답 형식상 지역명이 없는 7/8자리 번호판은 항상 1줄이 맞으므로, 이 경우에
    # 한해 line_count가 2로 잘못 나온 비율을 오분류율로 그대로 볼 수 있음(9자리는
    # 1줄/2줄 둘 다 가능한 형식이라 파일명만으로는 정답을 알 수 없어 제외).
    line1 = [r for r in rows if len(r) > 8 and r[8] == "1"]
    line2 = [r for r in rows if len(r) > 8 and r[8] == "2"]
    no_region_rows = [r for r in rows if len(r) > 8 and r[8] and len(r[2]) in (7, 8)]
    no_region_misclassified = [r for r in no_region_rows if r[8] != "1"]
    print("\n==================== 1줄/2줄 판단 확인 ====================")
    print(f"1줄로 판단: {len(line1):4d}장 / 2줄로 판단: {len(line2):4d}장")
    for label, subset in (("1줄 판단", line1), ("2줄 판단", line2)):
        n = len(subset)
        if n:
            exact = sum(1 for r in subset if r[6] == "정답")
            unread = sum(1 for r in subset if r[6] == "판독불가")
            wrong = sum(1 for r in subset if r[6] == "오답")
            print(f"  {label} {n:4d}장 중 - 정답 {exact:4d}장({exact/n*100:5.1f}%) / "
                  f"판독불가 {unread:4d}장({unread/n*100:5.1f}%) / 오답 {wrong:4d}장({wrong/n*100:5.1f}%)")
    if no_region_rows:
        print(f"지역명 없는(7/8자리, 항상 1줄이 정답) {len(no_region_rows)}장 중 "
              f"2줄로 잘못 판단: {len(no_region_misclassified)}장")
        for r in no_region_misclassified:
            print(f"    잘못 판단됨: {r[1]} (정답={r[2]}, 예측={r[3]})")

    print("상세 결과: batch_test_result.csv (마지막 '줄수판단' 열에서 1/2 확인 가능)")


if __name__ == "__main__":
    main()
