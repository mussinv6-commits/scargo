"""
번호판 인식 GUI (검출 + 글자 인식 + 박스 수동 보정)
- YOLO로 번호판 위치를 찾고(검출), 그 영역을 잘라 EasyOCR로 글자를 읽습니다(인식).
- YOLO가 잡은 박스가 번호판을 다 못 덮어서 글자 인식이 안 되면, 화면에서 박스 모서리를
  직접 드래그해서 크기를 맞춘 뒤 "선택 영역 다시 인식"으로 재시도할 수 있습니다.
- runs/detect/*/weights/best.pt 를 자동으로 찾아 모델 목록에 올려주므로,
  새 실험이 끝나 더 좋은 모델이 생기면 목록에서 골라 바로 테스트할 수 있습니다.

실행: python plate_detector_gui.py  (또는 번호판검출_실행.bat 더블클릭)
필요 패키지: ultralytics, opencv-python, pillow, easyocr, numpy, sv-ttk
"""
import os
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import csv
import functools
import re
import sys
import threading
import time
import traceback
from collections import Counter
import tkinter as tk
import tkinter.font as tkfont
from datetime import datetime
from pathlib import Path
from tkinter import ttk, filedialog, messagebox, scrolledtext

def _exe_dir():
    """exe 파일 자체가 있는 폴더(=사용자가 실제로 복사/배포하는 최상위 폴더).
    PyInstaller onedir로 빌드된 exe는 sys.executable이 exe가 있는 실제(최상위) 폴더를
    가리키는 반면, __file__ 은 그 안의 _internal 폴더를 가리켜서 한 단계 더 들어가
    버림 - crash_log.txt처럼 실행 중 새로 만드는 파일이나, runs\\detect처럼 빌드에
    포함되지 않고 exe 옆에 따로 복사해두는 폴더를 찾을 때는 여기를 기준으로 함."""
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


def _internal_dir():
    """PyInstaller가 datas로 묶은 리소스 파일(app_icon.ico 등)이 실제로 들어있는 위치.
    onedir 빌드는 이런 파일들을 exe 바로 옆이 아니라 _internal 폴더 안에 넣으므로
    exe 위치(_exe_dir)와는 구분해서 찾아야 함."""
    if getattr(sys, "frozen", False):
        return Path(getattr(sys, "_MEIPASS", sys.executable)).resolve()
    return Path(__file__).resolve().parent


APP_DIR = _exe_dir()

# pythonw로 실행하면 콘솔이 없어 에러가 안 보이고 프로그램이 그냥 조용히 꺼짐.
# 그래서 무슨 에러든 exe 옆의 crash_log.txt 에 남기도록 가장 먼저 설정.
CRASH_LOG_PATH = APP_DIR / "crash_log.txt"


def _log_crash(exc_type, exc_value, exc_tb):
    try:
        with open(CRASH_LOG_PATH, "a", encoding="utf-8") as f:
            f.write(f"\n--- {datetime.now()} ---\n")
            traceback.print_exception(exc_type, exc_value, exc_tb, file=f)
    except Exception:
        pass


sys.excepthook = _log_crash

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageTk
from ultralytics import YOLO

try:
    # 직접 학습시킨 CRNN 인식 모델 - 2026-09-26 BiLSTM 출력에 self-attention 보정층을
    # 추가한 실험판(train_ocr_recognizer_attn.py/plate_ocr_crnn_attn.py)이 기존
    # BiLSTM 단독판(CER 0.074/74.9%)보다 확실히 좋아서(검증 CER 0.066, beam+TTA
    # 완전 일치율 80.5%) 이걸로 교체함. EasyOCR보다도 우수해서 1차 인식기로 사용함.
    # 기존 BiLSTM 단독판은 backup_bilstm_v1_20260926/ 에 그대로 백업되어 있어서
    # 문제가 생기면 즉시 되돌릴 수 있음. 파일이 없거나(구버전 배포 폴더 등) torch
    # 로드에 실패해도 앱 자체는 EasyOCR만으로 계속 동작해야 하므로 조용히 넘어감.
    from plate_ocr_crnn_attn import CRNNRecognizer, VALID_PLATE_REGIONS
except ImportError:
    CRNNRecognizer = None
    # plate_ocr_crnn_attn을 못 불러와도(구버전 배포 폴더 등) 영상 처리의 번호판 문법
    # 검증(트랙 다수결에 사용)은 계속 동작하도록 지역명 목록을 여기 그대로 둠.
    VALID_PLATE_REGIONS = frozenset([
        "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
        "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
    ])

try:
    # Windows 11 기본 앱과 같은 계열의 라운드 버튼/스크롤바/콤보박스를 제공하는 검증된
    # ttk 테마. 예전엔 clam 테마 위에 색만 손으로 칠해서 밋밋하고 각진 느낌이었는데,
    # 이걸 쓰면 버튼 모서리가 둥글게 다듬어지고 호버/눌림 효과도 자연스러워져서
    # Windows 기본 프로그램과 비슷한 고급스러운 느낌이 남.
    # 배포 PC에 sv-ttk가 없으면(예: 구버전 폴더) 조용히 예전 clam 테마로 대체됨.
    import sv_ttk
except ImportError:
    sv_ttk = None

def _find_runs_dir():
    """학습된 모델(runs/detect) 폴더를 찾음.
    1) 먼저 exe(또는 스크립트)와 같은 폴더에 runs\\detect 가 있는지 확인 - 다른 PC에
       배포할 때는 dist\\CargoScan 폴더 안에 runs\\detect 를 같이 복사해두면 그대로
       동작함 (예전엔 이 프로젝트 원본 경로가 하드코딩되어 있어서, exe를 다른 PC나
       다른 폴더로 옮기면 "학습된 모델을 찾지 못함" 오류가 나는 문제가 있었음).
    2) 같은 폴더에 없으면, 이 PC에서 개발하던 원본 프로젝트 경로로 대체 - 재빌드해서
       이 PC에서 바로 테스트할 때 매번 runs 폴더를 복사하지 않아도 되는 편의용 대체 경로.
    """
    here = APP_DIR / "runs" / "detect"
    if here.exists():
        return here
    dev_fallback = Path(r"C:\Users\user\Desktop\project.v4i.yolov8") / "runs" / "detect"
    return dev_fallback


PROJECT_DIR = APP_DIR
RUNS_DIR = _find_runs_dir()
# train_ocr_recognizer_attn.py로 학습시킨 CRNN(BiLSTM+self-attention) 인식 모델
# 위치 - ocr_model_attn 폴더를 runs\detect와 같은 방식(exe/스크립트와 같은 폴더
# 기준)으로 찾음. 배포 시에는 ocr_model_attn 폴더를 exe 옆에 같이 복사해두면 됨.
# (기존 BiLSTM 단독판은 ocr_model/ 에 그대로 남아있고, backup_bilstm_v1_20260926/
# 에도 백업되어 있음 - 되돌리려면 이 세 줄과 위 import만 plate_ocr_crnn/ocr_model로
# 바꾸면 됨.)
OCR_MODEL_DIR = PROJECT_DIR / "ocr_model_attn"
CRNN_MODEL_PATH = OCR_MODEL_DIR / "best_recognizer_attn.pth"
CRNN_CHARS_PATH = OCR_MODEL_DIR / "chars_attn.json"
# 예전엔 "현재 제일 좋은 모델" 이름을 여기 손으로 적어놓고 매번 갱신해야 했는데,
# 이제 모델 목록을 만들 때 실제 성능(mAP50-95)을 읽어서 자동으로 최고 성능 모델을 고르므로
# 이 값은 성능 기록이 하나도 없는 예외적인 경우에만 쓰이는 비상용 기본값임.
DEFAULT_RUN = "plate_detect_m"

# ==================== 영상 처리: 프레임 간 번호판 추적 (2026-09-26 추가) ====================
# 정지 사진과 달리 영상은 같은 트럭/번호판이 여러 프레임에 걸쳐 찍히는데, 기존 방식은
# 프레임마다 완전히 독립적으로 인식해서 이 "여러 번 찍힐 기회"를 전혀 활용하지 못했음
# (게다가 영상은 속도 때문에 TTA/저조도 재시도도 꺼놔서 프레임 하나하나의 조건은 정지
# 사진보다도 나쁨). 아래 트래커는 같은 번호판을 프레임 간에 하나로 묶어서, 그 번호판이
# 화면에 머무는 동안 나온 여러 인식 결과 중 확신도 상위 몇 개만 뽑아 TTA+beam으로
# 다시 정밀하게 재인식하고, 번호판 문법(지역명+2자리+글자+4자리, 또는 3자리+글자+4자리)에
# 맞는 것을 우선한 다수결로 최종 답을 하나로 확정함.
TRACK_TOPK = 5           # 트랙별로 재인식(TTA)할 확신도 상위 후보 개수
TRACK_MAX_MISS = 10      # 이 프레임 수만큼 연속으로 못 보면 트랙 종료(화면 이탈로 간주)
IOU_MATCH_THRESHOLD = 0.15  # 이 IOU 미만이면 다른 번호판(새 트랙)으로 취급

# 영상 1단계(프레임별 검출)에서 쓸 conf 상한. 이미지 conf 기본값(0.9)은 "한 장짜리
# 결과를 그대로 믿어야 하니 엄격하게"가 이유인데, 영상은 애초에 같은 번호판이 여러
# 프레임에 걸쳐 찍히는 걸 전제로 만든 파이프라인임 - 프레임 하나하나의 검출이 다소
# 느슨해도, 트랙이 끝날 때 번호판 문법 검증 + 다수결(_PlateTrack.finalize)로 걸러지므로
# 이미지처럼 프레임 단위 conf를 엄격하게 가져갈 이유가 없음. 오히려 다차선 도로 영상처럼
# 번호판이 작고 멀리 있는 장면에서 conf=0.9를 그대로 쓰면 프레임마다 아무것도 안 잡혀
# 트랙 자체가 하나도 안 생기고("영상 전체 미검출"), 그 결과 이 상한이 없으면 사용자가
# 영상 열 때마다 매번 conf를 수동으로 낮춰야 함. imgsz 인자와 달리 이건 GUI conf
# 슬라이더 값과 무관하게 항상 적용됨(이미지 인식에는 영향 없음).
VIDEO_DETECT_CONF_CAP = 0.4

# 영상 1단계에서 같은 트랙에 매 프레임 OCR을 다 돌리면(차 한 대가 60프레임 찍히면 60번)
# 대부분 어차피 상위 TRACK_TOPK개만 남기고 버려지는데도 CRNN/EasyOCR 호출은 매번
# 일어나서, 실제 테스트해보니(1499프레임짜리 실제 영상) 이 반복 OCR 호출이 처리 시간의
# 대부분을 차지함. 이미 있는 트랙은 몇 프레임에 한 번만 OCR해도 상위 후보를 채우는 데
# 지장이 없으므로(다양한 프레임에서 몇 개만 더 샘플링하면 충분), 새로 보는 트랙이거나
# 아직 후보가 TRACK_TOPK개 안 찼을 때만 매번 돌리고, 그 외엔 이 간격만큼 건너뜀.
OCR_FRAME_STRIDE = 3

_PLATE_RE_WITH_REGION = re.compile(r"^(" + "|".join(VALID_PLATE_REGIONS) + r")\d{2}[가-힣]\d{4}$")
_PLATE_RE_NO_REGION = re.compile(r"^\d{3}[가-힣]\d{4}$")
# 버스/렌터카 등에 쓰이는 형식(지역명·3자리 코드 없이 2자리+글자+4자리, 예: 71허4006) -
# labels.csv 실측에서 4장(0.2%) 발견됨. 이것도 안 넣으면 트랙 다수결에서 "형식이 깨졌다"고
# 오판해 진짜 정답을 버릴 수 있어서 셋 다 유효 형식으로 처리해야 함.
_PLATE_RE_SHORT_NO_CODE = re.compile(r"^\d{2}[가-힣]\d{4}$")


def _is_valid_plate_format(text):
    """한국 번호판 문법(9자리: 지역명+2자리+글자+4자리 / 8자리: 3자리+글자+4자리 / 7자리:
    2자리+글자+4자리 - 버스·렌터카 등)에 맞는지만 확인함 - 실제 존재하는 번호인지는 알 수
    없지만, 명백히 형식이 깨진 오인식(자릿수가 안 맞거나 글자 위치가 틀린 것)을 트랙
    다수결에서 걸러내는 용도."""
    if not text:
        return False
    return bool(
        _PLATE_RE_WITH_REGION.match(text)
        or _PLATE_RE_NO_REGION.match(text)
        or _PLATE_RE_SHORT_NO_CODE.match(text)
    )


def _edit_distance(a, b):
    """일반 레벤슈타인 거리 - 지역명(2글자) 교정용으로만 쓰는 짧은 문자열 전용."""
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


def _clean_plate_text(text):
    """OCR 결과에 섞여 들어온 괄호/특수문자 잡음 제거 + 지역명 앞 2글자 교정.
    2026-09-27: 767장(test+valid) 실측에서 오답 72건 중 58건이 "뒤 4자리 숫자는
    맞는데 앞부분(지역명 등)만 틀림"으로 확인됨 - 그중 EasyOCR이 괄호/기호를
    글자로 잘못 읽어 붙인 경우, 그리고 지역명이 유효하지 않은 문자로 깨진 경우를
    코드 수정 없이 실제 데이터로 시뮬레이션해서(기존 정답 633건 중 회귀 0건 확인
    후) 반영함 - 순수 후처리라 크롭/모델 로직은 전혀 안 건드림.
    주의: 지역명 앞 2글자가 이미 유효한 지역명이면 절대 안 건드림 - "울산인데
    부산으로 읽힘"처럼 둘 다 유효한 지역명끼리 헷갈리는 경우는 이 방법으로 구분할
    수 없어서(형식만 봐선 뭐가 진짜인지 알 길이 없음) 못 고침 - 그건 모델 자체의
    정확도 한계라 재학습 없인 못 풂. 이 함수는 그 나머지, "형식 자체가 깨진" 경우만
    고치는 좁은 범위의 안전한 보정임."""
    if not text:
        return text
    cleaned = re.sub(r"[^\w가-힣0-9]", "", text)
    if len(cleaned) >= 2:
        prefix = cleaned[:2]
        if prefix not in VALID_PLATE_REGIONS:
            candidates = [r for r in VALID_PLATE_REGIONS if _edit_distance(prefix, r) == 1]
            if len(candidates) == 1:
                cleaned = candidates[0] + cleaned[2:]
    # "아" 글자를 숫자 "0"으로 잘못 읽는 특정 혼동이 반복적으로 확인됨(2026-09-27,
    # 767장 실측 - "경북84아8611"->"경북8408611"처럼 다른 글자는 다 맞고 글자 자리만
    # "0"으로 깨지는 사례가 7건 중 4건이 이 패턴 하나로 완전히 회복됨). 길이가 9/8/7
    # (지역명 유무에 따른 정상 자릿수)이고 글자가 와야 할 자리에 정확히 "0"이 있을
    # 때만, 그 자리를 "아"로 바꿔서 전체 형식이 유효해지면 채택 - 그 외의 경우(숫자
    # 다른 자리, 다른 숫자)는 이 특정 혼동의 증거가 없어서 건드리지 않음.
    letter_idx = {9: 4, 8: 3, 7: 2}.get(len(cleaned))
    if letter_idx is not None and cleaned[letter_idx] == "0":
        candidate = cleaned[:letter_idx] + "아" + cleaned[letter_idx + 1:]
        if _is_valid_plate_format(candidate):
            cleaned = candidate
    return cleaned


def _is_plausible_plate_text(text):
    """엄격한 자릿수 문법(_is_valid_plate_format)엔 안 맞아도, 최소한 "번호판을 읽으려는
    시도였다"고 볼 수 있는 최소 조건만 확인함(한글 1자 이상 + 숫자 3자 이상 포함).
    2026-09-27: 검출확신도 72%로 꽤 높고 육안으로도 멀쩡히 읽히는 번호판인데("207?9189"
    형태), 자릿수 인식이 한 글자 어긋나는 것만으로 엄격한 정규식을 통과 못 해 통째로
    "판독 불가" 처리되는 사례가 확인됨 - 완전히 다른 언어로 오인식된 경우("Daong"처럼
    한글이 전혀 없는 경우)만 진짜 판독 불가로 걸러내고, 이 정도로 번호판스러운 후보는
    자릿수가 살짝 어긋나도 살려서 다수결에 포함시킴(안 보여주는 것보다 나음)."""
    if not text:
        return False
    has_hangul = any("가" <= ch <= "힣" for ch in text)
    digit_count = sum(ch.isdigit() for ch in text)
    return has_hangul and digit_count >= 3


def _iou(box_a, box_b):
    """두 박스(x1,y1,x2,y2)의 교집합/합집합 비율. 프레임 간 같은 번호판인지 판단하는 데 씀."""
    ax1, ay1, ax2, ay2 = box_a
    bx1, by1, bx2, by2 = box_b
    ix1, iy1 = max(ax1, bx1), max(ay1, by1)
    ix2, iy2 = min(ax2, bx2), min(ay2, by2)
    iw, ih = max(0.0, ix2 - ix1), max(0.0, iy2 - iy1)
    inter = iw * ih
    area_a = max(0.0, ax2 - ax1) * max(0.0, ay2 - ay1)
    area_b = max(0.0, bx2 - bx1) * max(0.0, by2 - by1)
    union = area_a + area_b - inter
    return inter / union if union > 0 else 0.0


class _PlateTrack:
    """영상 한 편 안에서 같은 번호판이 여러 프레임에 걸쳐 나타나는 걸 하나로 묶는 단위.
    프레임마다(빠른 속도 우선 설정으로) 인식한 결과를 확신도 상위 TRACK_TOPK개만
    보관해두었다가, 트랙이 끝나면(트럭이 화면을 벗어나면) 그 후보들만 TTA+beam으로
    다시 한번 정밀하게 재인식해서 최종 답을 정함 - 모든 프레임에 TTA를 쓰면 너무
    느려지므로, "어차피 여러 프레임 중 제일 잘 나온 것들"로만 추린 뒤 정밀 재인식함."""

    __slots__ = (
        "track_id", "last_box", "last_frame_idx", "miss", "candidates", "final_text", "final_conf",
        "last_ocr_frame_idx", "last_det_conf", "smooth_box",
    )

    # 박스 좌표 스무딩 강도(지수이동평균 계수). 낮을수록 더 부드럽지만 실제 이동에 반응이
    # 느려짐 - 번호판은 트럭이 움직여도 화면상 위치가 프레임 사이에 크게 튀지 않으므로
    # 0.3 정도면 떨림은 확실히 줄이면서 실제 이동도 충분히 따라감.
    BOX_SMOOTH_ALPHA = 0.3

    def __init__(self, track_id, box, frame_idx, det_conf=None):
        self.track_id = track_id
        self.last_box = box
        self.last_frame_idx = frame_idx
        self.miss = 0
        self.candidates = []  # [(conf, text, crop), ...] 확신도 내림차순, 최대 TRACK_TOPK개
        self.final_text = None
        self.final_conf = None
        self.last_ocr_frame_idx = None  # 마지막으로 이 트랙에 실제 OCR을 돌린 프레임(속도 최적화용)
        self.last_det_conf = det_conf  # 이번 프레임에 못 잡혀 마지막 위치를 유지해 그릴 때 표시용
        self.smooth_box = box  # 화면 표시(그리기) 전용 - 프레임별 검출 좌표의 미세한 흔들림을
        # 지수이동평균으로 완화한 좌표. IOU 매칭/크롭에는 영향 없음(그건 raw box 그대로 씀).

    def add(self, box, frame_idx, text, conf, crop, det_conf=None):
        self.last_box = box
        self.last_frame_idx = frame_idx
        self.miss = 0
        if det_conf is not None:
            self.last_det_conf = det_conf
        a = self.BOX_SMOOTH_ALPHA
        self.smooth_box = tuple(
            a * nb + (1 - a) * sb for nb, sb in zip(box, self.smooth_box)
        )
        if text and conf is not None:
            self.candidates.append((conf, text, crop))
            self.candidates.sort(key=lambda c: -c[0])
            del self.candidates[TRACK_TOPK:]

    def finalize(self, recognizer, conf_threshold=0.5, ocr_once_fn=None):
        """트랙 종료 시 최종 답을 확정함. recognizer가 있으면(CRNN 로드됨) 보관해둔
        상위 후보들을 TTA+beam으로 재인식해서 더 정확한 값을 얻고, 그 중 번호판
        문법에 맞는 것들을 우선해서(있으면) 다수결 -> 동률이면 확신도 최고값으로 확정.

        ocr_once_fn(crop, alt) - 이미지 모드(_ocr_plate)와 똑같이 CRNN이 확신도
        미달이면 EasyOCR로 폴백함(alt=False 먼저, 그래도 약하면 alt=True도 시도해
        더 나은 쪽 채택). 이게 빠져있으면 "같은 크롭인데 이미지로 돌리면 더 잘
        읽히는" 경우가 생겨서(영상은 CRNN 결과가 약해도 그냥 그대로 썼었음) 이미지
        모드와 동일한 3단계 폴백을 여기도 맞춰 넣음."""
        if not self.candidates:
            return
        refined = []
        for conf, text, crop in self.candidates:
            r_text, r_conf = None, None
            if recognizer is not None and crop is not None:
                try:
                    r_text, r_conf = recognizer.recognize(crop, tta=True, decode="beam")
                except Exception:
                    r_text, r_conf = None, None
            if r_text and r_conf is not None and r_conf >= conf_threshold:
                refined.append((r_conf, r_text))
                continue
            if ocr_once_fn is not None and crop is not None:
                try:
                    alt0_text, alt0_conf = ocr_once_fn(crop, False)
                    if not alt0_text or alt0_conf is None or alt0_conf < 0.65:
                        alt1_text, alt1_conf = ocr_once_fn(crop, True)
                    else:
                        alt1_text, alt1_conf = None, None
                except Exception:
                    alt0_text, alt0_conf, alt1_text, alt1_conf = None, None, None, None
                candidates_here = [c for c in (
                    (r_conf, r_text) if r_text else None,
                    (alt0_conf, alt0_text) if alt0_text else None,
                    (alt1_conf, alt1_text) if alt1_text else None,
                ) if c is not None and c[0] is not None]
                if candidates_here:
                    refined.append(max(candidates_here, key=lambda c: c[0]))
                    continue
            if r_text:
                refined.append((r_conf if r_conf is not None else conf, r_text))
                continue
            refined.append((conf, text))

        # 이미지 모드(_ocr_plate)와 동일하게 괄호/기호 제거 + 지역명 편집거리1 교정
        # (2026-09-27, _clean_plate_text) - 트랙 후보 텍스트에도 똑같이 적용해야
        # 영상 모드만 이 보정을 못 받는 불일치가 생기지 않음.
        refined = [(c, _clean_plate_text(t)) for c, t in refined if t]

        valid = [(c, t) for c, t in refined if _is_valid_plate_format(t)]
        if valid:
            pool = valid
        else:
            # 엄격한 문법(9/8/7자리 정확한 형식)에 맞는 후보가 하나도 없으면, 곧바로 포기하기
            # 전에 "이게 번호판을 읽으려던 시도이긴 한지"만 느슨하게 한 번 더 확인함(한글+숫자
            # 최소 개수 포함 - _is_plausible_plate_text). 2026-09-27: 검출확신도 72%로 꽤
            # 높고 육안으로 멀쩡히 읽히는 번호판인데, 자릿수가 한 글자 어긋난 것만으로 통째로
            # "판독 불가" 처리되는 과도한 사례가 확인되어 이 중간 단계를 추가함.
            plausible = [(c, t) for c, t in refined if _is_plausible_plate_text(t)]
            if plausible:
                pool = plausible
            else:
                # 한글/숫자 조합조차 아닌 경우(예: "Daong")는 크롭 자체가 나빠 신뢰할 수 있는
                # 답이 없다는 뜻일 가능성이 높음 - 확신도 최고값을 억지로 답으로 내느니
                # "판독 불가"로 명확히 표시하는 게 틀린 답을 자신있게 보여주는 것보다 나음
                # (2026-09-27, 검출 41%짜리 박스가 "Daong"으로 확정된 사례에서 확인).
                self.final_text, self.final_conf = None, None
                return
        counts = Counter(t for _, t in pool)
        best_count = counts.most_common(1)[0][1]
        tied = [t for t, c in counts.items() if c == best_count]
        if len(tied) == 1:
            best_text = tied[0]
        else:
            best_text = max((ct for ct in pool if ct[1] in tied), key=lambda ct: ct[0])[1]
        best_conf = max((c for c, t in pool if t == best_text), default=None)
        self.final_text, self.final_conf = best_text, best_conf


KOREAN_FONT_CANDIDATES = [
    r"C:\Windows\Fonts\malgun.ttf",
    r"C:\Windows\Fonts\malgunbd.ttf",
    r"C:\Windows\Fonts\gulim.ttc",
]

HANDLE = 6  # 박스 모서리 드래그 핸들 크기(화면 픽셀)

ICON_PATH = _internal_dir() / "app_icon.ico"

# ---- 다크 테마 색상 (배포용) ----
# sv_ttk(Sun Valley) 테마의 기본 팔레트(배경 #1c1c1c, 글자 #fafafa, 강조색 #57c8ff)에
# 맞춰서 우리가 따로 칠하는 헤더바/SYSTEM LOG 패널 색도 통일함 - 이래야 sv_ttk가
# 그려주는 버튼/콤보박스 등과 우리가 직접 칠한 부분이 이질감 없이 하나로 보임.
BG_DARK = "#1c1c1c"
BG_PANEL = "#252525"       # 헤더바처럼 본문보다 한 단계 밝은 패널
BG_PANEL_ALT = "#141414"   # 캔버스/로그 등 본문보다 한 단계 어두운(사진이 돋보이는) 영역
FG_TEXT = "#fafafa"
FG_MUTED = "#9e9e9e"
ACCENT = "#57c8ff"         # sv_ttk 기본 강조색과 동일 - Accent.TButton 등과 자연스럽게 통일됨
ACCENT_HOVER = "#7dd6ff"
BORDER = "#3a3a3a"
# 카드(BG_PANEL) 위에 놓이는 일반 버튼 전용 색. 카드 배경과 똑같은 색을 쓰면
# 버튼이 카드와 구분이 안 돼서 호버하기 전까진 안 보이는 문제가 생기므로,
# 카드보다 한 단계 더 밝은("떠 있는") 색을 따로 둠.
BTN_NORMAL = "#333333"
BTN_HOVER = "#404040"
BTN_PRESS = "#2a2a2a"

# 2026-09-26 UI 리뉴얼: 상태(헤더 표시등)/결과 요약에 공통으로 쓰는 의미색.
# 예전엔 전부 같은 흰색/회색 글자라 "잘 됐는지 아닌지"를 빠르게 훑어볼 수 없었음 -
# 초록(정상)/주황(로딩 중·주의)/빨강(실패)로 한눈에 구분되게 함.
STATUS_OK = "#4cd471"
STATUS_WARN = "#ffb454"
STATUS_ERR = "#ff6b6b"


def _read_run_metrics(run_dir: Path):
    """실행 폴더(예: runs/detect/plate_detect_m)에서 학습이 끝나면 자동으로 남는
    results.csv 의 마지막 줄(=best.pt를 만든 마지막 epoch)을 읽어 정밀도/재현율/mAP를 반환.
    experiments_log.csv 처럼 손으로 따로 기록하지 않아도 모든 실행 폴더에 이 파일이
    자동 생성되므로, 이 값으로 모델 목록에 실제 성능을 보여줄 수 있음. 파일이 없거나
    형식이 예상과 다르면 None을 반환(그 모델은 "성능 기록 없음"으로 표시됨)."""
    csv_path = run_dir / "results.csv"
    if not csv_path.exists():
        return None
    try:
        with open(csv_path, newline="", encoding="utf-8") as f:
            rows = list(csv.DictReader(f))
        if not rows:
            return None
        # best.pt는 마지막 epoch이 아니라 학습 도중 성능이 가장 좋았던 epoch에서 저장됨.
        # (patience 옵션 때문에 그 이후로도 몇 epoch을 더 돌고 나서 학습이 끝나는 경우가
        # 많아서, results.csv의 마지막 줄을 그대로 쓰면 실제 best.pt 성능보다 낮은 값을
        # 보여주는 오류가 생김) -> mAP50-95가 가장 높은 줄을 best.pt의 실제 성능으로 사용.
        best_row = max(rows, key=lambda r: float(r["metrics/mAP50-95(B)"]))
        return {
            "epochs": len(rows),
            "precision": float(best_row["metrics/precision(B)"]),
            "recall": float(best_row["metrics/recall(B)"]),
            "map50": float(best_row["metrics/mAP50(B)"]),
            "map5095": float(best_row["metrics/mAP50-95(B)"]),
        }
    except Exception:
        return None


def _read_run_imgsz(run_dir: Path):
    """학습 시 저장되는 args.yaml에서 imgsz(학습 해상도)를 읽어옴.
    YOLO는 학습 때 쓴 해상도와 다른 해상도로 추론하면 검출 확신도/정확도가 떨어질 수
    있는데, 예전엔 predict() 호출에 imgsz를 안 넘겨서 기본값(640)으로 돌아갔음 - 실제
    모델들은 960(run-15만 1280)으로 학습되어 있어서 추론 해상도가 안 맞았던 것으로 보임.
    이 함수로 모델마다 학습 때 쓴 해상도를 읽어와서 추론에도 그대로 맞춰줌."""
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


def find_models():
    """runs/detect/*/weights/best.pt 를 모두 찾아
    {실행이름: {"path":경로, "metrics":성능 or None, "imgsz":학습 해상도 or None}} 로 반환"""
    models = {}
    if not RUNS_DIR.exists():
        return models
    for w in sorted(RUNS_DIR.glob("*/weights/best.pt")):
        run_dir = w.parent.parent
        run_name = run_dir.name
        models[run_name] = {
            "path": str(w),
            "metrics": _read_run_metrics(run_dir),
            "imgsz": _read_run_imgsz(run_dir),
            # mAP50-95가 동점인 두 모델 중 어느 쪽을 기본으로 고를지 정할 때 씀 (아래
            # _load_model_list 참고) - best.pt 파일 자체의 수정 시각을 그대로 사용.
            "mtime": w.stat().st_mtime,
        }
    return models


def get_korean_font(size=20):
    for path in KOREAN_FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()


# ======================================================================
# 커스텀 라운드 카드 / 버튼 (배포용 고급 UI)
# ----------------------------------------------------------------------
# sv_ttk를 적용해도 ttk 위젯 자체의 모서리 둥글기/그림자는 아주 미미해서 "고급스러운
# 느낌"이 잘 안 남. 그래서 각 섹션(카드)과 버튼만큼은 tk.Canvas에 직접 둥근 사각형을
# 그려서 훨씬 또렷하게 카드/버튼처럼 보이도록 만듦. 아이콘도 이모지 글꼴 대신 선으로
# 직접 그려서, 배포하는 PC마다 이모지 지원 여부가 달라 깨져 보이는 일이 없게 함.
# ======================================================================

def _round_rect_points(x1, y1, x2, y2, r):
    """둥근 사각형을 표현하는 polygon 좌표 목록. create_polygon(..., smooth=True)와
    함께 쓰면 모서리가 부드러운 곡선으로 그려짐 (tkinter 표준 라운드 사각형 기법)."""
    r = max(0, min(r, (x2 - x1) / 2, (y2 - y1) / 2))
    return [
        x1 + r, y1, x2 - r, y1, x2, y1, x2, y1 + r,
        x2, y2 - r, x2, y2, x2 - r, y2, x1 + r, y2,
        x1, y2, x1, y2 - r, x1, y1 + r, x1, y1,
    ]


# ---- 버튼용 아이콘 (전부 선/도형으로 직접 그림 - 폰트 이모지 사용 안 함) ----
def _icon_photo(cv, x, y, color):
    cv.create_rectangle(x - 8, y - 6, x + 8, y + 6, outline=color, width=1.6)
    cv.create_oval(x - 5, y - 4, x - 2, y - 1, outline=color, width=1.3)
    cv.create_line(x - 6, y + 4, x - 1, y - 1, x + 3, y + 2, x + 7, y - 2, fill=color, width=1.4, joinstyle="round")


def _icon_video(cv, x, y, color):
    cv.create_rectangle(x - 8, y - 6, x + 3, y + 6, outline=color, width=1.6)
    cv.create_polygon(x + 2, y - 4, x + 2, y + 4, x + 8, y, fill=color, outline=color)


def _icon_scan(cv, x, y, color):
    cv.create_oval(x - 7, y - 7, x + 2, y + 2, outline=color, width=1.8)
    cv.create_line(x + 1, y + 1, x + 8, y + 8, fill=color, width=1.8, capstyle="round")


def _icon_target(cv, x, y, color):
    cv.create_oval(x - 7, y - 7, x + 7, y + 7, outline=color, width=1.3)
    cv.create_line(x - 10, y, x + 10, y, fill=color, width=1.1)
    cv.create_line(x, y - 10, x, y + 10, fill=color, width=1.1)
    cv.create_oval(x - 1.6, y - 1.6, x + 1.6, y + 1.6, fill=color, outline=color)


def _icon_trash(cv, x, y, color):
    cv.create_line(x - 7, y - 5, x + 7, y - 5, fill=color, width=1.6)
    cv.create_line(x - 3, y - 7, x + 3, y - 7, fill=color, width=1.6)
    cv.create_rectangle(x - 5, y - 5, x + 5, y + 7, outline=color, width=1.6)


def _icon_save(cv, x, y, color):
    cv.create_line(x, y - 8, x, y + 3, fill=color, width=1.8, capstyle="round")
    cv.create_polygon(x - 4, y - 1, x + 4, y - 1, x, y + 4, fill=color, outline=color)
    cv.create_line(x - 8, y + 8, x + 8, y + 8, fill=color, width=1.8, capstyle="round")


def _icon_folder(cv, x, y, color):
    cv.create_polygon(
        x - 8, y - 3, x - 3, y - 3, x - 1, y - 5, x + 8, y - 5, x + 8, y + 5, x - 8, y + 5,
        fill="", outline=color, width=1.5, joinstyle="round",
    )


def _icon_terminal(cv, x, y, color):
    """SYSTEM LOG 카드 제목용 - 작은 콘솔 프롬프트(">_") 아이콘."""
    cv.create_rectangle(x - 8, y - 6, x + 8, y + 6, outline=color, width=1.3)
    cv.create_line(x - 5, y - 2, x - 2, y, x - 5, y + 2, fill=color, width=1.2, joinstyle="round", capstyle="round")
    cv.create_line(x - 1, y + 2, x + 4, y + 2, fill=color, width=1.2, capstyle="round")


class RoundButton(tk.Canvas):
    """ttk.Button 대신 쓰는 둥근 버튼. 호버/눌림 시 색이 바뀌어 실제로 "눌리는"
    느낌을 주고, icon 함수를 주면 글자 왼쪽에 작은 아이콘을 같이 그림."""

    def __init__(self, parent, text, command=None, icon=None, accent=False, height=38, bg=BG_DARK, **kw):
        super().__init__(parent, height=height, bg=bg, highlightthickness=0, cursor="hand2", **kw)
        self.text = text
        self.command = command
        self.icon = icon
        self.accent = accent
        self._state = "normal"
        if accent:
            self._colors = {"normal": ACCENT, "hover": ACCENT_HOVER, "press": ACCENT_HOVER}
            self._text_color = "#08111a"
        else:
            self._colors = {"normal": BTN_NORMAL, "hover": BTN_HOVER, "press": BTN_PRESS}
            self._text_color = FG_TEXT
        self.bind("<Configure>", self._redraw)
        self.bind("<Enter>", lambda e: self._set_state("hover"))
        self.bind("<Leave>", lambda e: self._set_state("normal"))
        self.bind("<ButtonPress-1>", lambda e: self._set_state("press"))
        self.bind("<ButtonRelease-1>", self._on_release)

    def _inside(self, event):
        return 0 <= event.x <= self.winfo_width() and 0 <= event.y <= self.winfo_height()

    def _set_state(self, state):
        self._state = state
        self._redraw()

    def _on_release(self, event):
        inside = self._inside(event)
        self._state = "hover" if inside else "normal"
        self._redraw()
        if inside and self.command:
            self.command()

    def _text_half_width(self):
        f = tkfont.Font(family="Segoe UI", size=10, weight="bold" if self.accent else "normal")
        return f.measure(self.text) / 2

    def _redraw(self, _event=None):
        w, h = self.winfo_width(), self.winfo_height()
        if w < 4 or h < 4:
            return
        self.delete("all")
        r = min(12, h // 2)
        self.create_polygon(
            _round_rect_points(0, 0, w, h, r), smooth=True, fill=self._colors[self._state], outline=""
        )
        cx = w / 2
        if self.icon:
            icon_x = cx - self._text_half_width() - 12
            self.icon(self, icon_x, h / 2, self._text_color)
            self.create_text(
                icon_x + 14, h / 2, text=self.text, fill=self._text_color, anchor="w",
                font=("Segoe UI", 10, "bold" if self.accent else "normal"),
            )
        else:
            self.create_text(
                cx, h / 2, text=self.text, fill=self._text_color,
                font=("Segoe UI", 10, "bold" if self.accent else "normal"),
            )


class RoundedCard(tk.Canvas):
    """ttk.LabelFrame 대신 쓰는 둥근 카드. 실제 내용물은 self.body(일반 Frame)
    안에 평소대로 pack/grid 하면 됨 - 카드는 그 Frame을 감싸는 둥근 배경만 그림.
    expand_body=True 면 카드 자체가 부모 안에서 늘어나는 만큼 안쪽 body도 같이
    늘어남(사진 캔버스를 담는 LOAD IMAGE 카드용). False(기본)면 카드 높이가
    body 안 내용물 크기에 맞춰 자동으로 정해짐(오른쪽 패널의 MODEL/LOAD 등)."""

    def __init__(self, parent, title, expand_body=False, bg=None, outer_bg=BG_DARK, icon=None, **kw):
        self._fill = bg or BG_PANEL
        self._outer_bg = outer_bg
        self._expand_body = expand_body
        self._icon = icon
        super().__init__(parent, highlightthickness=0, bg=self._outer_bg, **kw)
        self._title = title
        self.body = tk.Frame(self, bg=self._fill)
        self._body_window = None
        self._last_req_h = 0
        self.bind("<Configure>", self._redraw)
        if not expand_body:
            self.body.bind("<Configure>", self._on_body_resize)

    def _on_body_resize(self, _event=None):
        req_h = self.body.winfo_reqheight() + 44
        if req_h != self._last_req_h:
            self._last_req_h = req_h
            self.configure(height=req_h)
        else:
            self._redraw()

    def _redraw(self, _event=None):
        w, h = self.winfo_width(), self.winfo_height()
        if w < 4 or h < 4:
            return
        self.delete("card")
        self.create_polygon(
            _round_rect_points(0, 0, w, h, 14), smooth=True,
            fill=self._fill, outline=BORDER, width=1, tags="card",
        )
        text_x = 16
        if self._icon:
            # 아이콘 그리기 함수는 tags 인자를 받지 않으므로, 그리기 전/후 아이템 id
            # 차집합으로 새로 생긴 것만 찾아 "card" 태그를 붙임 - 그래야 다음 리사이즈 때
            # delete("card")로 같이 지워지고 겹쳐 쌓이지 않음. (self._body_window은 이 시점
            # 이전에 이미 만들어져 있고 태그가 없으므로 이 차집합 방식에 걸리지 않아 안전함)
            before_ids = set(self.find_all())
            self._icon(self, 21, 18, ACCENT)
            for iid in set(self.find_all()) - before_ids:
                self.addtag_withtag("card", iid)
            text_x = 34
        self.create_text(
            text_x, 18, text=self._title, anchor="w", fill=ACCENT,
            font=("Segoe UI", 10, "bold"), tags="card",
        )
        if self._body_window is None:
            self._body_window = self.create_window(14, 34, anchor="nw", window=self.body)
        else:
            self.coords(self._body_window, 14, 34)
        body_w = max(1, w - 28)
        if self._expand_body:
            self.itemconfigure(self._body_window, width=body_w, height=max(1, h - 34 - 12))
        else:
            self.itemconfigure(self._body_window, width=body_w)
        self.tag_raise(self._body_window)


class PlateDetectorApp:
    def __init__(self, root):
        self.root = root
        self.root.title("CargoScan - AI 번호판 인식")
        # 화면(특히 작업표시줄을 뺀 실제 사용 가능 영역)보다 창이 크면 안 되므로
        # 기본 크기(1060x800)를 화면 크기에 맞춰 줄임 - 작은 노트북 화면에서도
        # 아래쪽 SYSTEM LOG/버튼이 화면 밖으로 안 나가게 하기 위함
        screen_w = self.root.winfo_screenwidth()
        screen_h = self.root.winfo_screenheight()
        win_w = min(1060, screen_w - 40)
        win_h = min(800, screen_h - 80)
        self.root.geometry(f"{win_w}x{win_h}")
        self.root.minsize(min(920, win_w), min(700, win_h))
        self.root.configure(bg=BG_DARK)
        try:
            if ICON_PATH.exists():
                self.root.iconbitmap(str(ICON_PATH))
        except Exception:
            pass

        # 버튼 클릭 등 이벤트 처리 중 에러가 나도(=창이 안 꺼지게) crash_log.txt 에 기록만 남김
        self.root.report_callback_exception = self._report_callback_exception

        self._setup_style()

        self.model = None
        self.model_path = None
        self.model_imgsz = 960  # 모델 로드 시 args.yaml에서 실제 학습 해상도로 갱신됨
        self.ocr_reader = None
        self.crnn = None  # 직접 학습시킨 CRNN 인식기 (준비되면 self._load_crnn()이 채움)
        self.crnn_conf_threshold = 0.5  # beam conf 기준, self-attention 모델 재조정값(2026-09-26)
        # 이 이상이면 CRNN 결과를 그대로 채택, 아니면 EasyOCR로 폴백. verify_integration_attn.py로
        # 이 모델 자체의 confidence 분포를 다시 뽑아 재조정함 - 통과 샘플 내 완전일치율/통과율은
        # 0.3(91.3%/85.4%) → 0.4(87.2%/88.2%) → 0.5(84.6%/89.7%) → 0.6(79.5%/91.6%) 순으로,
        # EasyOCR 폴백 정확도(약 54~60%대)를 가중해 전체 기대 정확도를 추정해보면 0.5~0.6
        # 구간에서 가장 높고 그 위로는 통과율이 깎이는 손해가 이득을 넘어섬. 통과율을
        # 크게 희생하지 않는 쪽인 0.5를 선택함(0.3은 기존 BiLSTM 단독판에서 derive했던 값).
        self.korean_font = get_korean_font()

        self.current_image_path = None
        self.current_cv_image = None  # BGR numpy array (원본 크기) - 정지 이미지 모드 전용 상태
        self.display_image = None  # ImageTk 참조 유지용
        self.disp_scale = 1.0
        # 캔버스에 마지막으로 실제로 그린 프레임(정지 이미지든 영상 미리보기/결과 프레임이든
        # 전부 포함) - current_cv_image는 정지 이미지 모드에서만 채워지는 상태라, 이것만 보고
        # "창 크기 조절 시 다시 그릴지"를 판단하면 영상 처리 중엔 항상 None으로 보여서
        # 화면이 안내 문구로 지워지는 버그가 있었음(아래 _on_canvas_resize 참고).
        self._last_shown_cv = None

        self.boxes = []  # [{"x1","y1","x2","y2","text"}, ...] 원본 이미지 좌표
        self.selected_box_idx = None
        self.drag_handle = None  # "tl" | "tr" | "bl" | "br" | None
        self.drag_mode = None  # "resize" | "move" | "new" | None
        self._move_start = None
        self._move_orig = None
        self._new_box_origin = None
        self._drag_moved = False  # 실제로 드래그(그리기/크기조정/이동)가 일어났는지 - 단순 클릭과 구분
        self._ocr_wait_logged = False

        self._build_ui()
        self._load_model_list()
        # CRNN은 파일이 작아 금방 불려지지만, 그래도 UI가 멈추지 않도록 EasyOCR과
        # 마찬가지로 별도 스레드에서 로드함. EasyOCR보다 먼저 끝나는 게 보통이라,
        # EasyOCR 로딩이 끝나기 전에도 CRNN만으로 바로 인식을 시작할 수 있음.
        threading.Thread(target=self._load_crnn, daemon=True).start()
        threading.Thread(target=self._load_ocr, daemon=True).start()

    # ---------- 다크 테마 (배포용) ----------
    def _setup_style(self):
        style = ttk.Style(self.root)
        if sv_ttk is not None:
            # 버튼/스크롤바/콤보박스/스핀박스 전부 sv_ttk가 라운드 처리된 이미지로
            # 그려줌 - 예전에 clam 테마 위에 배경색만 칠했을 때보다 Windows 기본
            # 프로그램에 훨씬 가까운 느낌이 됨. 이 위젯들은 색을 직접 칠하지 않고
            # sv_ttk 기본값을 그대로 씀(어설프게 덮어쓰면 이미지 렌더링이 깨짐).
            sv_ttk.set_theme("dark")
        else:
            try:
                style.theme_use("clam")
            except tk.TclError:
                pass

        # 여기부터는 sv_ttk가 이름을 모르는(=이 프로그램에서만 쓰는) 커스텀 스타일들.
        # 헤더바/SYSTEM LOG처럼 "본문과 다른 한 단계 밝거나 어두운 패널"을 표현하려고
        # 추가한 것들이라, sv_ttk 유무와 상관없이 항상 우리가 직접 칠해줘야 함.
        style.configure("Header.TFrame", background=BG_PANEL)
        style.configure("Header.TLabel", background=BG_PANEL, foreground=FG_TEXT)
        style.configure("Sub.Header.TLabel", background=BG_PANEL, foreground=FG_MUTED)
        style.configure("Accent.Header.TLabel", background=BG_PANEL, foreground=ACCENT)

        # RoundedCard 안(배경 BG_PANEL)에 놓이는 ttk.Label들. 기본 "TLabel" 스타일은
        # sv_ttk 기본 배경(BG_DARK)을 쓰기 때문에, 카드 위에 그대로 쓰면 라벨 뒤에
        # 카드 색과 다른 사각형이 비쳐 보임 - 그래서 카드 전용 배경을 명시한 스타일을
        # 따로 둠.
        style.configure("Card.TLabel", background=BG_PANEL, foreground=FG_TEXT)
        style.configure("Muted.TLabel", background=BG_PANEL, foreground=FG_MUTED)

        if sv_ttk is None:
            # clam 폴백일 때만 옛날 방식대로 색을 손으로 칠함 (sv_ttk가 있으면 위에서
            # 이미 훨씬 나은 스타일을 적용했으므로 아래는 건너뜀).
            style.configure(".", background=BG_DARK, foreground=FG_TEXT, font=("Segoe UI", 10))
            style.configure("TFrame", background=BG_DARK)
            style.configure("TLabel", background=BG_DARK, foreground=FG_TEXT)
            style.configure(
                "TLabelframe", background=BG_DARK, foreground=FG_TEXT, bordercolor=BORDER, relief="flat"
            )
            style.configure(
                "TButton", background=BG_PANEL, foreground=FG_TEXT, borderwidth=0,
                focuscolor=BG_PANEL, padding=(10, 8), font=("Segoe UI", 10),
            )
            style.map("TButton", background=[("active", BORDER), ("pressed", BORDER)])
            style.configure(
                "Accent.TButton", background=ACCENT, foreground="#08111a", borderwidth=0,
                focuscolor=ACCENT, padding=(10, 9), font=("Segoe UI", 10, "bold"),
            )
            style.map("Accent.TButton", background=[("active", ACCENT_HOVER), ("pressed", ACCENT_HOVER)])
            style.configure(
                "TCombobox", fieldbackground=BG_PANEL, background=BG_PANEL, foreground=FG_TEXT,
                arrowcolor=FG_TEXT, bordercolor=BORDER, lightcolor=BG_PANEL, darkcolor=BG_PANEL,
            )
            style.map("TCombobox", fieldbackground=[("readonly", BG_PANEL)])
            style.configure(
                "TSpinbox", fieldbackground=BG_PANEL, background=BG_PANEL, foreground=FG_TEXT,
                arrowcolor=FG_TEXT, bordercolor=BORDER,
            )
            style.configure(
                "Right.Vertical.TScrollbar", background=ACCENT, troughcolor=BG_PANEL,
                bordercolor=BG_PANEL, arrowcolor="#08111a", relief="flat",
            )
            style.map("Right.Vertical.TScrollbar", background=[("active", ACCENT_HOVER)])

    # ---------- 화면 구성 ----------
    def _build_ui(self):
        header = ttk.Frame(self.root, style="Header.TFrame", padding=(18, 14))
        header.pack(side=tk.TOP, fill=tk.X)

        # 브랜드 마크(둥근 사각형 + 스캔 아이콘) - 예전엔 텍스트 워드마크만 있어서
        # 밋밋했는데, 작은 아이콘 하나만 앞에 붙여도 "제품"처럼 보이는 인상이 확 달라짐.
        logo = tk.Canvas(header, width=32, height=32, bg=BG_PANEL, highlightthickness=0)
        logo.pack(side=tk.LEFT, padx=(0, 10))
        logo.create_polygon(_round_rect_points(0, 0, 32, 32, 9), smooth=True, fill=ACCENT, outline="")
        _icon_scan(logo, 16, 16, "#08111a")

        title_col = ttk.Frame(header, style="Header.TFrame")
        title_col.pack(side=tk.LEFT)
        ttk.Label(title_col, text="CargoScan", style="Header.TLabel", font=("Segoe UI", 17, "bold")).pack(
            side=tk.LEFT
        )
        ttk.Label(title_col, text="   AI 화물차 번호판 인식 시스템", style="Sub.Header.TLabel").pack(
            side=tk.LEFT, pady=(3, 0)
        )

        ttk.Label(header, text="v3.14", style="Accent.Header.TLabel", font=("Segoe UI", 9, "bold")).pack(
            side=tk.RIGHT, padx=(14, 0)
        )

        # 상태 표시등 - 검출/인식 엔진이 실제로 준비됐는지를 로그를 안 읽어도 한눈에
        # 볼 수 있게 함(초록=정상, 주황=로딩 중, 빨강=실패/미탑재 - 실패해도 앱은 계속
        # 동작하지만 어떤 경로로 동작 중인지는 알 수 있어야 함).
        status_row = tk.Frame(header, bg=BG_PANEL)
        status_row.pack(side=tk.RIGHT)
        self._status_labels = {}
        for key, label in (("detect", "검출"), ("ocr_crnn", "번호판 인식"), ("ocr_easy", "보조 OCR")):
            lbl = tk.Label(
                status_row, text=f"●  {label}", bg=BG_PANEL, fg=STATUS_WARN, font=("Segoe UI", 9)
            )
            lbl.pack(side=tk.LEFT, padx=(0, 14))
            self._status_labels[key] = lbl

        # SYSTEM LOG(하단 고정 영역)를 먼저 pack해서 자기 몫의 공간을 확보해야
        # 그 다음에 pack되는 body(가운데, expand=True)가 나머지 공간만 차지하게 됨.
        # 순서가 반대였을 때는 body가 남은 공간을 전부 먼저 가져가버려서
        # 로그창 아랫부분이 화면 경계 밖으로 잘려 보이는 문제가 있었음.
        log_box = RoundedCard(self.root, title="시스템 로그", icon=_icon_terminal)
        log_box.pack(side=tk.BOTTOM, fill=tk.X, padx=10, pady=(0, 10))
        self.log_text = scrolledtext.ScrolledText(
            log_box.body, height=8, state="disabled", font=("Consolas", 9),
            bg=BG_PANEL_ALT, fg=FG_TEXT, insertbackground=FG_TEXT,
            relief="flat", borderwidth=0, highlightthickness=0,
        )
        self.log_text.pack(fill=tk.BOTH, expand=True)

        body = ttk.Frame(self.root, padding=10)
        body.pack(side=tk.TOP, fill=tk.BOTH, expand=True)

        left = RoundedCard(body, title="이미지 · 영상", icon=_icon_photo, expand_body=True)
        left.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        hint = tk.Frame(left.body, bg=BG_PANEL)
        hint.pack(side=tk.TOP, fill=tk.X, pady=(0, 6))
        ttk.Label(
            hint,
            text="빈 곳 드래그 = 새 박스   ·   박스 안 드래그 = 이동   ·   모서리 드래그 = 크기 조정",
            style="Sub.Header.TLabel",
            font=("Segoe UI", 9),
            padding=(8, 5),
        ).pack(side=tk.LEFT)

        self.canvas = tk.Canvas(left.body, bg=BG_PANEL_ALT, highlightthickness=0, cursor="tcross")
        self.canvas.pack(fill=tk.BOTH, expand=True)
        self.canvas.bind("<ButtonPress-1>", self._on_canvas_press)
        self.canvas.bind("<B1-Motion>", self._on_canvas_drag)
        self.canvas.bind("<ButtonRelease-1>", self._on_canvas_release)
        # 이미지를 아직 안 불러온 상태에서는 캔버스가 텅 빈 검은 사각형으로만 보이던
        # 문제 - 뭘 해야 할지 알려주는 안내 아이콘+문구를 가운데에 그려줌. 이미지를
        # 불러오면 _show_cv_image가 delete("all")로 지우고 다시 그리므로 자동으로 사라짐.
        self.canvas.bind("<Configure>", self._on_canvas_resize)
        self._draw_canvas_placeholder()

        # 오른쪽 패널(MODEL~RESULT)을 스크롤 가능하게 만듦 - 창이 작아져서
        # 4개 박스(MODEL/LOAD/DETECT+OCR/RESULT)가 세로로 다 안 들어가도
        # RESULT 같은 아래쪽 박스가 화면 밖으로 사라지지 않고 스크롤해서 보이게 함
        # right_outer 너비는 캔버스(270) + 스크롤바 몫까지 포함해야 함.
        # 이전엔 right_outer가 270으로 고정된 상태에서 그 안에 270짜리 캔버스를
        # 또 넣었더니 스크롤바가 들어갈 공간이 아예 없어서(폭 0) 화면에 안 보이고,
        # 그래서 스크롤 자체가 안 되는(=이전과 똑같아 보이는) 문제가 있었음.
        right_outer = ttk.Frame(body, width=288)
        right_outer.pack(side=tk.LEFT, fill=tk.Y, padx=(10, 0))
        right_outer.pack_propagate(False)

        right_canvas = tk.Canvas(right_outer, bg=BG_DARK, highlightthickness=0)
        # sv_ttk를 쓰면 기본 스크롤바 자체가 이미 다크 배경에서 잘 보이는 얇은
        # 라운드 스타일이라 그대로 씀. clam 폴백일 때만 눈에 띄게 직접 칠한
        # "Right.Vertical.TScrollbar"를 사용함(그때는 기본값이 거의 안 보였음).
        scrollbar_style = "Vertical.TScrollbar" if sv_ttk is not None else "Right.Vertical.TScrollbar"
        right_scrollbar = ttk.Scrollbar(
            right_outer, orient="vertical", command=right_canvas.yview, style=scrollbar_style
        )
        right_canvas.configure(yscrollcommand=right_scrollbar.set)
        right_canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        right_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        right = ttk.Frame(right_canvas)
        right_window = right_canvas.create_window((0, 0), window=right, anchor="nw")

        def _on_right_inner_configure(_event=None):
            right_canvas.configure(scrollregion=right_canvas.bbox("all"))

        def _on_right_canvas_configure(event):
            right_canvas.itemconfigure(right_window, width=event.width)

        right.bind("<Configure>", _on_right_inner_configure)
        right_canvas.bind("<Configure>", _on_right_canvas_configure)

        def _on_right_mousewheel(event):
            right_canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")

        def _bind_right_mousewheel(_event):
            right_canvas.bind_all("<MouseWheel>", _on_right_mousewheel)

        def _unbind_right_mousewheel(_event):
            right_canvas.unbind_all("<MouseWheel>")

        right_canvas.bind("<Enter>", _bind_right_mousewheel)
        right_canvas.bind("<Leave>", _unbind_right_mousewheel)

        model_box = RoundedCard(right, title="검출 모델", icon=_icon_target)
        model_box.pack(fill=tk.X, pady=(0, 10))
        self.model_var = tk.StringVar()
        self.model_combo = ttk.Combobox(model_box.body, textvariable=self.model_var, state="readonly")
        self.model_combo.pack(fill=tk.X)
        self.model_combo.bind(
            "<<ComboboxSelected>>", lambda e: (self._update_model_info(), self._load_selected_model())
        )

        # 선택한 모델이 어떤 건지(성능 수치) 바로 보여주는 안내 문구.
        # "여러 버전 중 뭐가 나은지 설명이 없다"는 문제를 해결하기 위해 추가함.
        self.model_info_var = tk.StringVar(value="")
        ttk.Label(
            model_box.body, textvariable=self.model_info_var, foreground=FG_MUTED,
            style="Card.TLabel", font=("Segoe UI", 8), wraplength=250, justify="left",
        ).pack(fill=tk.X, pady=(4, 0))

        conf_row = tk.Frame(model_box.body, bg=BG_PANEL)
        conf_row.pack(fill=tk.X, pady=(8, 0))
        ttk.Label(conf_row, text="conf", style="Card.TLabel").pack(side=tk.LEFT)
        self.conf_var = tk.DoubleVar(value=0.9)  # 기본 conf 0.9 - 확신도 낮은 오탐을 걸러내는 쪽을 기본으로
        ttk.Spinbox(conf_row, from_=0.05, to=0.95, increment=0.05, textvariable=self.conf_var, width=6).pack(
            side=tk.RIGHT
        )

        load_box = RoundedCard(right, title="불러오기", icon=_icon_folder)
        load_box.pack(fill=tk.X, pady=(0, 10))
        grid = tk.Frame(load_box.body, bg=BG_PANEL)
        grid.pack(fill=tk.X)
        RoundButton(grid, text="이미지 열기", icon=_icon_photo, command=self.open_image, bg=BG_PANEL).grid(
            row=0, column=0, sticky="ew", padx=(0, 4)
        )
        RoundButton(grid, text="영상 열기", icon=_icon_video, command=self.open_video, bg=BG_PANEL).grid(
            row=0, column=1, sticky="ew", padx=(4, 0)
        )
        grid.columnconfigure(0, weight=1)
        grid.columnconfigure(1, weight=1)

        detect_box = RoundedCard(right, title="검출 · 인식", icon=_icon_scan)
        detect_box.pack(fill=tk.X, pady=(0, 10))
        RoundButton(
            detect_box.body, text="전체 다시 인식", icon=_icon_scan, accent=True, height=44,
            command=self.run_detection_on_current, bg=BG_PANEL,
        ).pack(fill=tk.X)
        RoundButton(
            detect_box.body, text="선택한 박스만 다시 인식", icon=_icon_target,
            command=self.rerun_ocr_selected, bg=BG_PANEL,
        ).pack(fill=tk.X, pady=(8, 0))
        RoundButton(
            detect_box.body, text="선택 박스 삭제", icon=_icon_trash,
            command=self.delete_selected_box, bg=BG_PANEL,
        ).pack(fill=tk.X, pady=(8, 0))
        # 박스 편집 방법 자체는 캔버스 위 안내문(hint)에 이미 나와 있으므로 여기서
        # 똑같은 걸 다시 반복하지 않고, 그 다음에 뭘 해야 하는지만 짧게 알려줌.
        ttk.Label(
            detect_box.body,
            text="박스를 번호판에 맞춘 뒤 위 버튼으로 다시 인식하세요.",
            style="Muted.TLabel",
            wraplength=230,
            justify="left",
        ).pack(fill=tk.X, pady=(10, 0))

        save_box = RoundedCard(right, title="결과", icon=_icon_save)
        save_box.pack(fill=tk.X, pady=(0, 10))
        RoundButton(
            save_box.body, text="결과 이미지 저장", icon=_icon_save,
            command=self.save_current_result, bg=BG_PANEL,
        ).pack(fill=tk.X)
        self.result_var = tk.StringVar(value="검출 결과: -")
        # ttk.Label 대신 일반 tk.Label을 씀 - 검출 결과가 전부 정상인지, 경고가 섞였는지,
        # 아직 아무것도 없는지에 따라 글자색을 즉시 바꿔주고 싶은데(_update_result_summary
        # 참고), ttk 위젯은 인스턴스별로 전경색을 그때그때 바꾸려면 스타일을 매번 새로
        # 만들어야 해서 번거로움 - 일반 tk.Label은 .configure(fg=...)로 바로 되어 더 간단함.
        self.result_label = tk.Label(
            save_box.body, textvariable=self.result_var, bg=BG_PANEL, fg=FG_MUTED,
            wraplength=230, justify="left", font=("Segoe UI", 9), anchor="w",
        )
        self.result_label.pack(fill=tk.X, pady=(8, 0))

    def _report_callback_exception(self, exc_type, exc_value, exc_tb):
        _log_crash(exc_type, exc_value, exc_tb)
        try:
            self.log(f"오류 발생(자세한 내용은 crash_log.txt 참고): {exc_value}")
        except Exception:
            pass

    def log(self, msg: str):
        ts = datetime.now().strftime("%H:%M:%S")
        self.log_text.configure(state="normal")
        self.log_text.insert(tk.END, f"[{ts}] {msg}\n")
        self.log_text.see(tk.END)
        self.log_text.configure(state="disabled")

    def _set_status(self, key, state):
        """헤더 상태 표시등 갱신. state: 'loading'(주황) | 'ok'(초록) | 'error'(빨강).
        엔진이 실패해도(예: CRNN 모델 없음) 앱은 계속 EasyOCR 등으로 동작하지만,
        지금 어떤 경로로 동작 중인지 로그를 안 뒤져도 한눈에 보이게 하기 위함."""
        color = {"loading": STATUS_WARN, "ok": STATUS_OK, "error": STATUS_ERR}.get(state, FG_MUTED)
        lbl = self._status_labels.get(key)
        if lbl is not None:
            lbl.configure(fg=color)

    # ---------- 모델 / OCR 로딩 ----------
    def _load_model_list(self):
        raw = find_models()
        if not raw:
            messagebox.showerror("오류", f"{RUNS_DIR} 에서 학습된 모델(best.pt)을 찾지 못했습니다.")
            self.log(f"모델을 찾지 못함: {RUNS_DIR}")
            return

        # mAP50-95(정확도 지표) 기준 내림차순 정렬 - 성능 기록이 없는 모델은 맨 뒤로 보냄.
        # 이렇게 하면 드롭다운 순서만 봐도 위에 있는 게 더 좋은 모델이라는 걸 알 수 있음.
        #
        # 단, mAP50-95 차이가 아주 작으면(예: 0.87396 vs 0.87361 - 소수점 3자리까지
        # 반올림하면 둘 다 "0.874") 그 차이는 학습 노이즈 수준이라 어느 쪽이 진짜 더
        # 나은 모델인지 이 숫자만으론 판단할 수 없음. 실제로 저조도(야간) augmentation을
        # 강화해서 재학습한 모델은 mAP50-95 평균은 그대로였지만, 야간 사진 확신도가
        # 0.06 -> 0.90+ 로 극적으로 좋아진 사례가 있었음 - mAP 평균에는 거의 안 잡히는
        # 종류의 실질적 개선이었던 것. 그래서 근소한 차이는 동점으로 보고, 그 안에서는
        # 더 최근에 학습된 모델을 우선함 - 보통 나중 실험이 이전 실험을 의도적으로
        # 개선하려는 시도이기 때문.
        # (0.002 -> 0.01로 상향: B안 데이터 보강 실험이 mAP50-95는 0.874->0.868로 0.006
        #  낮아졌지만, 실제 평균 confidence는 0.916->0.929로 더 좋아진 사례가 실측으로
        #  확인됨(check_confidence.py) - 0.002 기준으론 이 케이스를 "동점"으로 못 잡고
        #  더 나은 모델을 자동선택에서 놓치고 있었음.)
        TIE_TOLERANCE = 0.01

        def _compare_models(a, b):
            m_a, m_b = a[1]["metrics"], b[1]["metrics"]
            if (m_a is None) != (m_b is None):
                return -1 if m_a is not None else 1
            if m_a is None and m_b is None:
                return 0
            diff = m_a["map5095"] - m_b["map5095"]
            if abs(diff) > TIE_TOLERANCE:
                return -1 if diff > 0 else 1
            mtime_diff = a[1].get("mtime", 0) - b[1].get("mtime", 0)
            if mtime_diff != 0:
                return -1 if mtime_diff > 0 else 1
            return 0

        ordered = sorted(raw.items(), key=functools.cmp_to_key(_compare_models))
        scored_names = [name for name, info in ordered if info["metrics"]]

        self.models = {}          # 드롭다운 표시 문구 -> {"path":경로, "imgsz":학습 해상도 or None}
        self.model_metrics = {}   # 드롭다운 표시 문구 -> 성능 정보(dict) or None
        display_names = []
        for rank, (run_name, info) in enumerate(ordered, start=1):
            m = info["metrics"]
            if m is not None:
                is_best = run_name == scored_names[0]
                tag = "최고" if is_best else f"{rank}위/{len(scored_names)}개"
                label = f"{'★ ' if is_best else ''}{run_name}  (mAP50-95 {m['map5095']:.3f}, {tag})"
                self.model_metrics[label] = m
            else:
                label = f"{run_name}  (성능 기록 없음)"
                self.model_metrics[label] = None
            self.models[label] = {"path": info["path"], "imgsz": info.get("imgsz")}
            display_names.append(label)

        self.model_combo["values"] = display_names
        if scored_names:
            # 성능 기록이 있는 모델 중 최고 성능을 기본으로 선택.
            default = display_names[0]
        else:
            # 성능 기록이 하나도 없는 예외적인 경우에만 예전 방식(DEFAULT_RUN 이름)으로 대체.
            fallback_path = raw.get(DEFAULT_RUN, {}).get("path")
            default = next(
                (label for label, info in self.models.items() if info["path"] == fallback_path),
                display_names[-1],
            )
        self.model_var.set(default)
        self.log(f"모델 {len(display_names)}개 발견 - 성능순 정렬, 기본 선택: {default}")
        self._update_model_info()
        self._load_selected_model()

    def _update_model_info(self):
        """선택된 모델의 성능 수치를 MODEL 박스 아래 안내 문구로 보여줌."""
        m = getattr(self, "model_metrics", {}).get(self.model_var.get())
        if not m:
            self.model_info_var.set("이 모델은 성능 기록(results.csv)이 없습니다.")
            return
        # 이 수치들(정밀도/재현율/mAP)은 YOLO 검출 모델이 "번호판 위치를 박스로 잘
        # 찾는지"에 대한 지표이지, 그 안의 글자를 제대로 읽는지(OCR 인식률)와는 완전히
        # 별개임 - 실제로 이 둘을 같은 "인식률"로 오해하는 경우가 있어서(위치는 잘
        # 찾아도 글자를 틀리게 읽을 수 있음) 오해 없도록 문구에 명시함.
        self.model_info_var.set(
            f"[번호판 위치 검출 성능] 정밀도 {m['precision']:.3f}  재현율 {m['recall']:.3f}  "
            f"mAP50 {m['map50']:.3f}  mAP50-95 {m['map5095']:.3f}  ({m['epochs']} epoch 학습)\n"
            "※ 박스 위치 정확도이며, 글자(번호판 텍스트) 인식률과는 다른 수치입니다."
        )

    def _load_selected_model(self):
        name = self.model_var.get()
        info = self.models.get(name)
        if not info:
            return
        path = info["path"]
        # 학습 때 쓴 해상도로 추론해야 확신도/정확도가 학습 검증 때와 비슷하게 나옴.
        # args.yaml에 기록이 없는 경우(구버전 모델 등)엔 이 프로젝트의 기본값인 960 사용.
        self.model_imgsz = info.get("imgsz") or 960
        self.log(f"모델 로딩 중: {name} ...")
        self._set_status("detect", "loading")
        self.root.update_idletasks()
        self.model = YOLO(path)
        self.model_path = path
        self.log(f"모델 로드 완료: {name} (추론 해상도 imgsz={self.model_imgsz})")
        self._set_status("detect", "ok")

    def _load_crnn(self):
        """직접 학습시킨 CRNN(BiLSTM+self-attention) 인식 모델을 불러옴 (검증 CER
        0.066 / 완전 일치율 78.5%, beam+TTA 기준 80.5% - 2026-09-26 기존 BiLSTM
        단독판 0.074/74.9%를 self-attention 보정층 추가로 갱신함. EasyOCR 최종
        검증치 54.1%보다도 확실히 우수해서 1차 인식기로 씀).
        모델 파일이 없거나(구버전 배포 폴더 등) 로드 중 오류가 나면, self.crnn을
        None으로 남겨두고 조용히 EasyOCR만으로 계속 동작하게 함 - CRNN은 어디까지나
        더 나은 결과를 위한 추가 경로이지, 없다고 앱이 멈추면 안 됨."""
        if CRNNRecognizer is None:
            self.root.after(0, lambda: (
                self.log("CRNN 모듈(plate_ocr_crnn_attn.py)을 찾지 못해 EasyOCR만 사용합니다."),
                self._set_status("ocr_crnn", "error"),
            ))
            return
        if not CRNN_MODEL_PATH.exists() or not CRNN_CHARS_PATH.exists():
            msg = f"CRNN 인식 모델을 찾지 못해({OCR_MODEL_DIR}) EasyOCR만 사용합니다."
            self.root.after(0, lambda: (
                self.log(msg),
                self._set_status("ocr_crnn", "error"),
            ))
            # 배포판에서 ocr_model_attn 폴더를 빠뜨리고 exe만 복사하면, 로그 한 줄과
            # 상태등만으로는 놓치기 쉬움(실제로 이 문제가 한 번 발생했었음) - 이 경우
            # 정확도가 크게 떨어지는(EasyOCR 단독 약 54% vs 정상 78.5%) 심각한 상태라
            # 팝업으로 확실히 알림.
            self.root.after(0, lambda: messagebox.showwarning(
                "인식 모델 누락",
                "직접 학습시킨 번호판 인식 모델(ocr_model_attn 폴더)을 찾지 못했습니다.\n"
                "정확도가 훨씬 낮은 EasyOCR만으로 동작합니다.\n\n"
                f"찾은 위치: {OCR_MODEL_DIR}\n"
                "exe/스크립트와 같은 폴더에 ocr_model_attn 폴더가 있는지 확인하세요.",
            ))
            return
        try:
            recognizer = CRNNRecognizer()
            recognizer.load(CRNN_MODEL_PATH, CRNN_CHARS_PATH)
            self.crnn = recognizer
            self.root.after(0, lambda: (
                self.log("CRNN 인식 모델 로드 완료 (BiLSTM+self-attention, 자체 학습 모델 - 1차 인식기로 사용)"),
                self._set_status("ocr_crnn", "ok"),
            ))
        except Exception as e:
            msg = f"CRNN 인식 모델 로드 실패: {e} (EasyOCR만 사용합니다)"
            self.root.after(0, lambda: (self.log(msg), self._set_status("ocr_crnn", "error")))

    def _load_ocr(self):
        try:
            self.root.after(0, lambda: (
                self.log("OCR 엔진 로딩 중 (최초 1회, 시간이 걸릴 수 있습니다)..."),
                self._set_status("ocr_easy", "loading"),
            ))
            import easyocr
            import torch

            # 예전엔 "GPU는 학습에 쓰이고 있을 수 있어" 무조건 CPU로 돌렸는데, 실제로 학습을
            # 돌리고 있지 않을 때도 계속 CPU만 써서 EasyOCR 폴백(확신도 낮을 때마다 호출됨)이
            # 느려지는 원인이 됐음(2026-09-27: 36226프레임짜리 긴 영상 처리 속도 문의).
            # CRNN은 이미 torch.cuda.is_available()로 GPU를 자동으로 쓰고 있으므로, EasyOCR도
            # 같은 기준으로 GPU가 있으면 자동으로 쓰도록 맞춤 - 지금처럼 학습을 안 돌리고 있을
            # 때는 그만큼 빨라짐(다른 프로그램이 GPU를 이미 쓰고 있으면 효과가 없을 수 있음).
            use_gpu = torch.cuda.is_available()
            reader = easyocr.Reader(["ko", "en"], gpu=use_gpu)
            self.ocr_reader = reader
            self.root.after(0, self._on_ocr_ready)
        except Exception as e:
            msg = f"OCR 엔진 로드 실패: {e} (박스만 표시됩니다. pip install easyocr 필요할 수 있음)"
            self.root.after(0, lambda: (self.log(msg), self._set_status("ocr_easy", "error")))

    def _on_ocr_ready(self):
        self.log("OCR 엔진 로드 완료 (한글+영문)")
        self._set_status("ocr_easy", "ok")
        # OCR이 로딩되기 전에 이미 검출해서 텍스트가 비어있던 박스가 있으면 자동으로 다시 읽어봄.
        # 단, conf 기준 미달 참고용 후보 박스(low_conf)는 경계가 부정확할 수 있어 일부러
        # 자동 인식을 꺼둔 상태이므로, 여기서도 건드리지 않고 사람이 박스를 확인/보정한
        # 뒤 직접 재인식하게 둠.
        if self.boxes and self.current_cv_image is not None:
            changed = False
            for b in self.boxes:
                if not b["text"] and not b.get("low_conf"):
                    x1, y1, x2, y2 = int(b["x1"]), int(b["y1"]), int(b["x2"]), int(b["y2"])
                    crop = self._padded_crop(self.current_cv_image, x1, y1, x2, y2)
                    text, ocr_conf = self._ocr_plate(crop, pad_ratio=0.18, pad_bottom_ratio=0.18)
                    # 영상 모드(v3.4/v3.5)와 동일하게, 번호판을 읽으려던 시도로도 안 보이는
                    # 완전히 엉뚱한 결과(한글이 아예 없는 등)는 화면에 그대로 보여주지 않고
                    # "판독 불가"로 처리함 - 자신있게 틀린 답을 보여주는 것보다 나음.
                    if text and not _is_plausible_plate_text(text):
                        text = ""
                    if text:
                        b["text"] = text
                        b["ocr_conf"] = ocr_conf
                        changed = True
            if changed:
                self._redraw_boxes()
                self._update_result_summary()
                self.log("OCR 준비 완료 - 기존 박스를 자동으로 다시 인식했습니다.")

    def _padded_crop(self, cv_img, x1, y1, x2, y2, pad_ratio=0.18, pad_bottom_ratio=None):
        """OCR용 크롭은 YOLO 박스보다 살짝 여유를 두고 잘라냄.
        YOLO 박스는 번호판 테두리에 거의 딱 맞게(혹은 살짝 안쪽으로) 잡히는 경우가 많고,
        수동으로 그리거나 보정한 박스도 마지막 글자 쪽을 살짝 덜 덮는 경우가 있어서,
        그대로 크롭하면 첫/마지막 글자나 위쪽 지역명 글자 일부가 잘려서 오독의 흔한
        원인이 됨. 화면에 표시되는 박스 좌표(x1~y2)는 그대로 두고, OCR/CRNN에 넘기는
        이미지만 가로/세로 각각 18%(최소 5px) 여유를 둬서 글자가 안 잘리게 함
        (CRNN 통합 후 수동 보정 박스에서 뒷자리 숫자가 잘리는 사례가 보여 기존
        12%(최소 3px)에서 다시 상향 - CRNN도 학습 crop 자체에 여백이 포함돼 있어서
        약간 더 여유를 둬도 인식에 문제 없음).

        pad_bottom_ratio: 지정하면 아래쪽 여백만 이 비율로 따로 줌(위/좌우는 pad_ratio
        그대로). 2026-09-27: 검출확신도 낮은 박스 중 일부가 실제 번호판보다 구조적으로
        위쪽(그릴/범퍼 위)에 치우쳐 잡혀서, 대칭 여백만으로는 아래쪽에 남은 번호판을
        다 못 담는 사례가 실제 영상에서 확인됨 - 이런 박스는 아래쪽만 더 크게 열어줌."""
        h_img, w_img = cv_img.shape[:2]
        bw, bh = x2 - x1, y2 - y1
        pad_x = max(5.0, bw * pad_ratio)
        pad_y_top = max(5.0, bh * pad_ratio)
        pad_y_bottom = max(5.0, bh * (pad_bottom_ratio if pad_bottom_ratio is not None else pad_ratio))
        px1 = max(0, int(x1 - pad_x))
        py1 = max(0, int(y1 - pad_y_top))
        px2 = min(w_img, int(x2 + pad_x))
        py2 = min(h_img, int(y2 + pad_y_bottom))
        # .copy(): 슬라이스만 반환하면 원본 프레임 전체를 참조로 붙들고 있게 되는데,
        # 영상 처리에서는 이 crop을 트랙별로 여러 프레임 동안 보관해뒀다가 나중에
        # (트랙 종료 시) 재사용하므로, 매 프레임 원본 이미지 전체가 메모리에 계속
        # 쌓이는 걸 막기 위해 작은 crop만 독립적으로 복사해둠.
        return cv_img[py1:py2, px1:px2].copy()

    @staticmethod
    def _fmt_duration(seconds):
        """초 단위 값을 "약 3분", "약 1시간 20분" 같은 사람이 읽기 편한 문구로 변환.
        영상 처리 진행률에 남은 예상 시간을 같이 보여주기 위해 추가함(2026-09-27 - 처리
        시간이 얼마나 걸리는지 계속 궁금해했던 것에 대응)."""
        seconds = max(0, int(seconds))
        m, s = divmod(seconds, 60)
        h, m = divmod(m, 60)
        if h > 0:
            return f"약 {h}시간 {m}분"
        if m > 0:
            return f"약 {m}분"
        return f"약 {s}초"

    @staticmethod
    def _enhance_low_conf_crop(crop_bgr):
        """검출확신도 낮은(멀리 있거나 흐릿한) 박스 크롭 전용 화질 보정.
        CLAHE(적응형 히스토그램 평활화)로 대비를 살리고, 약한 언샵 마스크로 글자 경계를
        또렷하게 함 - 원본에 없는 디테일을 만들어내진 못하지만(해상도 자체는 그대로),
        있는 대비/경계를 더 잘 드러내면 CRNN/EasyOCR 인식에 도움이 될 수 있음.
        2026-09-27: 박스 위치를 고쳐도(pad_bottom_ratio) 여전히 판독 불가인 저확신도
        사례들에 대응해 시도 - 확신도 높은 박스는 이미 잘 되고 있으므로 절대 건드리지
        않고(호출부에서 low_conf_box일 때만 사용), 이 대상에만 국한함."""
        gray = cv2.cvtColor(crop_bgr, cv2.COLOR_BGR2GRAY)
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        enhanced = clahe.apply(gray)
        blurred = cv2.GaussianBlur(enhanced, (0, 0), sigmaX=1.0)
        sharpened = cv2.addWeighted(enhanced, 1.5, blurred, -0.5, 0)
        return cv2.cvtColor(sharpened, cv2.COLOR_GRAY2BGR)

    @staticmethod
    def _order_ocr_fragments(raw):
        """EasyOCR이 돌려준 (bbox, text, conf) 조각들을 실제 읽는 순서(위->아래 줄 단위,
        각 줄 안에서는 왼쪽->오른쪽)로 재배열하고, 글자 인식률(번호판 안 글자 자체를 얼마나
        확신하고 읽었는지)도 같이 계산해서 (텍스트, 글자인식률) 튜플로 돌려줌.
        글자인식률은 실제로 채택된 조각들의 EasyOCR 확신도 평균이며, 번호판 위치를 찾은
        확신도(YOLO conf, 검출 인식률)와는 다른 별개의 값 - UI에 "검출 인식률"과 "글자
        인식률" 두 가지를 따로 보여주기 위해 구분함.
        EasyOCR은 감지된 순서대로 돌려주는데, 이게 실제 읽는 순서와 다른 경우가 있어서
        "충북"(위쪽 지역명)처럼 2줄로 된 번호판에서 글자 순서가 뒤섞여 나오는 원인이 됨.
        confidence가 너무 낮은 조각(나사 자국, 얼룩, 배경 잡음 등을 글자로 오인)은 걸러내되,
        전부 걸러지면 아예 결과가 없는 것보다는 원본을 그대로 쓰는 게 나음."""
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
                # 같은 줄이면 세로 중심이 글자 평균 높이의 60% 이내로 비슷함
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

    def _ocr_once(self, crop, alt=False):
        """번호판 crop 이미지 한 장에서 글자를 한 번 읽어봄. 흑백 변환 + 대비 보정 + 확대로
        인식률을 높임.
        (예전엔 인식 가능한 문자를 카테고리 글자로만 제한했는데, "경기"/"서울" 같은 지역명이 붙는
        구형 번호판을 오히려 이상하게 읽는 부작용이 있어 제거함 - 모든 한글/숫자를 그대로 인식)
        "충북"처럼 2줄 번호판 위쪽에 작게 붙는 지역명이 통째로 누락되는 경우가 있어서,
        확대 기준 높이를 올리고(120->160) EasyOCR 텍스트 감지 민감도를 낮춰서
        (text_threshold/low_text) 작고 흐린 글자도 감지 단계에서 놓치지 않게 함.
        detail=1로 바꿔서 각 조각의 위치/확신도까지 받은 뒤, _order_ocr_fragments로 실제
        읽는 순서에 맞게 재배열하고 잡음(낮은 확신도) 조각은 걸러냄 (예전엔 detail=0으로
        EasyOCR이 감지한 순서 그대로 이어붙여서, 2줄 번호판에서 순서가 뒤섞이는 경우가 있었음).
        alt=True면 저조도(야간/역광) 크롭을 겨냥한 보정을 추가로 적용함 - 학습 단계에서
        hsv_v를 올려 저조도 대응력을 키운 것과 같은 맥락으로, 추론 단계에서도 어두운 crop을
        감마 보정으로 먼저 밝힌 뒤 CLAHE를 더 강하게(clipLimit 2.0->4.0) 걸어서 어두운
        번호판의 글자 대비를 최대한 끌어올림. 일반 조건 crop에는 오히려 노이즈를 키울 수
        있어서, 확신도가 낮을 때 재시도 용도로만 씀 (기본 경로는 그대로 alt=False).
        반환값은 (텍스트, 글자인식률) 튜플 - 글자인식률은 못 구하면 None."""
        if self.ocr_reader is None or crop is None or crop.size == 0:
            return "", None
        try:
            gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
            h, w = gray.shape[:2]
            # 160 -> 200: 글자 획이 가는 국내 번호판 폰트 특성상, 조금 더 크게 확대할수록
            # EasyOCR이 획 경계를 더 잘 잡아냄 (너무 키우면 오히려 블러만 커지므로 200이 절충점).
            target_h = 200
            if h < target_h:
                scale = target_h / max(h, 1)
                gray = cv2.resize(
                    gray, (max(1, int(w * scale)), target_h), interpolation=cv2.INTER_CUBIC
                )
            if alt and float(gray.mean()) < 100.0:
                # 어두운 crop만 감마 보정으로 밝힘 (이미 밝은 crop에 걸면 오히려 날아감)
                gamma = 1.8
                inv_gamma = 1.0 / gamma
                lut = np.array(
                    [((i / 255.0) ** inv_gamma) * 255 for i in range(256)]
                ).astype("uint8")
                gray = cv2.LUT(gray, lut)
            clip_limit = 4.0 if alt else 2.0
            clahe = cv2.createCLAHE(clipLimit=clip_limit, tileGridSize=(8, 8))
            gray = clahe.apply(gray)
            # 확대 과정에서 살짝 번지는 글자 경계를 언샤프 마스킹으로 다시 또렷하게 함
            # (블러한 버전을 원본에서 빼는 대신 더해서 대비를 끌어올리는 표준 기법).
            blurred = cv2.GaussianBlur(gray, (0, 0), sigmaX=1.2)
            gray = cv2.addWeighted(gray, 1.5, blurred, -0.5, 0)
            if alt:
                # 1차 시도에서 확신도가 낮았던 crop에서만 beamsearch 디코더를 써서 조금 더
                # 정확하게(그러나 느리게) 다시 읽음 - 매 프레임 돌리는 영상 경로에는 이 alt
                # 재시도 자체가 안 걸리므로(retry_if_low_conf=False) 속도에 영향 없음.
                raw = self.ocr_reader.readtext(
                    gray, detail=1, paragraph=False, text_threshold=0.4, low_text=0.3,
                    link_threshold=0.3, decoder="beamsearch", beamWidth=5,
                )
            else:
                raw = self.ocr_reader.readtext(
                    gray, detail=1, paragraph=False, text_threshold=0.4, low_text=0.3, link_threshold=0.3
                )
            return self._order_ocr_fragments(raw)
        except Exception as e:
            self.log(f"OCR 처리 오류: {e}")
            return "", None

    def _ocr_plate(self, crop, retry_if_low_conf=True, box_ratio=None,
                   pad_ratio=None, pad_bottom_ratio=None):
        """_ocr_plate_impl()의 결과에 _clean_plate_text()(괄호 제거+지역명 교정,
        2026-09-27)를 한 번 더 거쳐서 돌려줌 - 이미지/영상 모드의 모든 호출부가
        하나도 안 바뀌어도 자동으로 이 보정을 받게 하려고 얇은 래퍼로 둠(내부
        인식 로직 자체는 v3.10에서 손 안 댐).
        box_ratio: 2026-09-28 추가 - YOLO 박스 자체(여백 더하기 전)의 가로/세로
        비율. 확신도 낮은 박스에 비대칭 여백(위/좌우 30%+아래 65%)을 준 뒤의 crop
        비율로 1줄/2줄을 판단하면 여백 때문에 비율이 왜곡돼 오분류가 남 - 아래
        plate_ocr_crnn_attn.guess_line_count 참고.
        pad_ratio/pad_bottom_ratio: 2026-09-28 추가 - 2줄 번호판을 위/아래 줄로
        나눌 때도 같은 이유로 원본 박스 범위가 필요함(plate_ocr_crnn_attn.
        unwrap_two_line 참고) - _padded_crop에 준 여백 비율을 그대로 넘김."""
        text, conf, engine = self._ocr_plate_impl(
            crop, retry_if_low_conf=retry_if_low_conf, box_ratio=box_ratio,
            pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio,
        )
        if text:
            text = _clean_plate_text(text)
        # 2026-09-28: EasyOCR 폴백 결과는 CRNN과 달리 신뢰도가 낮으므로(767장 실측,
        # batch_test_result.csv) 최소한 번호판 문법(_is_valid_plate_format - 지역명+
        # 2자리+글자+4자리, 또는 3자리+글자+4자리, 또는 2자리+글자+4자리)을 통과한
        # 것만 채택함. 실측 결과 EasyOCR 정답 20건은 전부(100%) 이 문법을 통과하고
        # 오답 45건 중 34건(76%)은 애초에 문법부터 안 맞았음 - 즉 이 검사만으로
        # 정답은 하나도 안 잃으면서 오답의 대부분을 걸러낼 수 있음(EasyOCR을 아예
        # 안 쓰는 것보다 나은 절충안 - 사용자 확인, 2026-09-28). CRNN 결과는 그대로 둠.
        if text and engine != "crnn" and not _is_valid_plate_format(text):
            text = ""
        # 2026-09-29 추가: 위 문법 검사는 통과하는데도 여전히 오답인 패턴을 767장
        # 재실측(batch_test_result.csv)으로 찾음 - EasyOCR이 앞의 지역명(2글자)을
        # 통째로 놓쳐서 나머지만 읽으면 우연히 "지역명 없는 8자리"(예: "298사2482",
        # "983아5677") 또는 "7자리"(예: "88자5803", "80아4475") 형식과 우연히 맞아떨어져
        # 문법 검사를 통과해버림 - 이런 케이스가 이번 실측에서 9건 나왔는데 9건 전부
        # (100%) 오답이었음. 반면 이 프로젝트의 진짜 지역명 없는 8자리 번호판은
        # labels.csv 기준 전부 "006"으로 시작하는 특정 트럭 계열이고(예: 006너9792),
        # 진짜 7자리(버스/렌터카용)는 전체 데이터 중 4장(0.2%)뿐이며 이번 실측에서
        # 유일한 사례(36국6798)도 CRNN이 직접 읽어서 맞혔음(EasyOCR 경유 안 함) -
        # 즉 EasyOCR이 이 두 형식을 내놓을 때는 진짜 지역명 없는 번호판이 아니라
        # 지역명을 놓친 경우일 확률이 매우 높음. CRNN 결과는 이미 이 형식들을 정확히
        # 읽어내고 있으므로 이 추가 제약 대상이 아님(engine=="crnn"이면 건드리지 않음).
        if text and engine != "crnn":
            if len(text) == 8 and not text.startswith("006"):
                text = ""
            elif len(text) == 7:
                text = ""
        return text, conf

    def _ocr_plate_impl(self, crop, retry_if_low_conf=True, box_ratio=None,
                         pad_ratio=None, pad_bottom_ratio=None):
        """crop에서 글자를 읽음.
        1차로 직접 학습시킨 CRNN(BiLSTM+self-attention) 인식 모델(라벨 정제 +
        weight_decay=0 오버샘플링 조합에, BiLSTM 출력을 self-attention으로 한 번 더
        보정하는 층을 추가한 뒤 검증 CER 0.066 / 완전 일치율 78.5%, beam+TTA 기준
        80.5% - 이전 BiLSTM 단독 기록 0.074/74.9%, 최초 기록 0.085/73.5%를 순서대로
        갱신함)을 먼저 시도함 - EasyOCR(실사용 검증치 54.1%)보다 우수하고 속도도
        훨씬 빠름(모델이 작고 beamsearch 재시도 같은 게 없음).
        확신도 판단은 greedy decode 대신 CTC beam search decode의 confidence를 씀 -
        verify_integration.py로 (기존 BiLSTM 단독판) 195장 검증셋에서 직접 비교해보니,
        맞은 예측과 틀린 예측을 beam conf가 greedy conf보다 훨씬 뚜렷하게 갈라놓음
        (틀린 예측은 beam conf가 거의 0으로 떨어지는 반면 greedy conf는 여전히
        0.5~0.9대로 높게 나와 폴백 판단을 왜곡시켰었음). beam 디코딩은 텍스트 자체는
        greedy와 항상 같았고 confidence 계산 방식만 다르므로, 인식 결과 텍스트는
        그대로 두고 신뢰도만 더 정확해진 셈. CRNN 결과가 있고 이 beam confidence가
        self.crnn_conf_threshold(현재 0.5 - self-attention 모델 자체의 confidence
        분포로 verify_integration_attn.py를 다시 돌려 재조정한 값. EasyOCR 폴백
        정확도(약 54~60%대)를 가중한 전체 기대 정확도가 0.5~0.6 구간에서 가장 높고,
        통과율을 크게 희생하지 않는 0.5를 선택함) 이상이면 그
        결과를 그대로 채택하고 EasyOCR은 아예 돌리지 않음. CRNN이 아직 로드되지
        않았거나(로딩 중) 결과가 비었거나 확신도가 낮으면, 기존 EasyOCR 경로로
        폴백함(아래는 예전과 동일한 로직).
        반환값은 (텍스트, 글자인식률) 튜플 - 글자인식률은 못 구하면 None.

        v3.8에서 "CRNN이 확신도는 넘겼는데 번호판 문법에 안 맞으면 EasyOCR과
        경쟁시킨다"는 로직을 넣었다가 v3.10에서 되돌림(2026-09-27) - 실제 영상
        전체로 테스트해보니 "대체적으로 글자 개판"이라는 광범위한 악화가
        보고됨. 원인으로 추정되는 것: 문법 검사(_is_valid_plate_format)는
        "글자 배치가 그럴듯한가"만 볼 뿐 "내용이 맞는가"는 전혀 보장 못 하는데,
        CRNN이 EasyOCR보다 실제 정확도가 훨씬 높다(80%대 vs 54~60%대, 위
        docstring 참고)는 사실과 무관하게 "문법에 맞는 쪽"을 우선시하다 보니,
        CRNN이 맞았지만 문법 검사만 어쩌다 실패한 경우까지 EasyOCR의(문법은
        맞지만 실제로는 틀릴 수 있는) 답으로 자꾸 바꿔치기했을 가능성이 큼 -
        패딩 0.9 실패(v3.1) 때와 같은 교훈: 소수 사례를 노린 조건이 실제로는
        너무 자주 걸려서 전체를 오히려 깎아먹음. "확신도 높은 CRNN 답을 문법
        때문에 의심하지 않는다"는 예전 방식으로 되돌림 - Daong류의 극단적으로
        틀린 답 방지는 track.finalize()의 문법 검사(v3.4/v3.5)가 영상 모드에서
        이미 담당하고 있어 이 되돌림으로 다시 뚫리지 않음.

        2026-09-28 추가: 위 v3.8 실패 사례와 헷갈리면 안 되는 게, 그건 "확신도
        높은 CRNN 답도 문법 검사로 의심해서 EasyOCR과 경쟁시킨" 것이었고 이번
        추가는 전혀 다름 - CRNN이 통과했으면 절대 건드리지 않고(위 return이 그대로
        유지됨), EasyOCR 폴백으로 넘어간 경우에 한해서만 그 결과가 문법에 맞는지
        _ocr_plate()에서 검사함. 767장 실측으로 확인: EasyOCR 폴백 정답 20건은
        전부 문법을 통과했고 오답 45건 중 34건은 애초에 문법부터 안 맞았음."""
        if self.crnn is not None:
            # 정지 이미지(단일 인식/수동 보정)에서는 TTA(여러 변형으로 반복 인식 후
            # 다수결)를 켜서 정확도를 조금 더 끌어올리고, 영상 프레임 처리처럼
            # retry_if_low_conf=False로 호출되는 속도 우선 경로에서는 TTA 없이
            # 한 번만 인식함(TTA는 5배 가까이 느려져서 프레임마다 돌리기엔 부담됨).
            # decode="beam": 텍스트는 greedy와 사실상 동일하게 나오지만 confidence
            # 분리력이 훨씬 좋아서(위 docstring 참고) 폴백 판단용으로 이걸 씀.
            crnn_text, crnn_conf = self.crnn.recognize(
                crop, tta=retry_if_low_conf, decode="beam", box_ratio=box_ratio,
                pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio,
            )
            if crnn_text and crnn_conf is not None and crnn_conf >= self.crnn_conf_threshold:
                return crnn_text, crnn_conf, "crnn"

        # CRNN 확신도 미달(또는 crnn 자체가 없음) - EasyOCR로 폴백. 이 결과를
        # 최종 채택할지는 호출부인 _ocr_plate()에서 문법 검사(_is_valid_plate_format)로
        # 한 번 더 거름 - 2026-09-28 참고.
        text, ocr_conf = self._ocr_once(crop, alt=False)
        if not retry_if_low_conf:
            return text, ocr_conf, "easyocr"
        if text and ocr_conf is not None and ocr_conf >= 0.65:
            return text, ocr_conf, "easyocr"
        alt_text, alt_conf = self._ocr_once(crop, alt=True)
        if not alt_text:
            return text, ocr_conf, "easyocr"
        if not text or ocr_conf is None or (alt_conf is not None and alt_conf > ocr_conf):
            return alt_text, alt_conf, "easyocr_alt"
        return text, ocr_conf, "easyocr"

    # ---------- 이미지 로드 + 최초 검출 ----------
    def open_image(self):
        path = filedialog.askopenfilename(
            title="이미지 선택",
            filetypes=[("이미지 파일", "*.jpg *.jpeg *.png *.bmp"), ("모든 파일", "*.*")],
        )
        if not path:
            return
        self.current_image_path = path
        self.current_cv_image = cv2.imread(path)
        if self.current_cv_image is None:
            messagebox.showerror("오류", "이미지를 열 수 없습니다.")
            self.log(f"이미지 열기 실패: {path}")
            return
        self.log(f"이미지 불러오기 성공: {path}")
        self.run_detection_on_current()

    def run_detection_on_current(self):
        if self.model is None:
            messagebox.showwarning("알림", "모델이 아직 로드되지 않았습니다.")
            return
        if self.current_cv_image is None:
            messagebox.showwarning("알림", "먼저 이미지를 열어주세요.")
            return
        if self.ocr_reader is None and not self._ocr_wait_logged:
            self.log(
                "OCR 엔진이 아직 로딩 중입니다 (처음 실행 시 최대 1분 정도 걸릴 수 있어요) - "
                "로딩이 끝나면 자동으로 다시 인식됩니다. 다시 누르지 않고 기다려도 됩니다."
            )
            self._ocr_wait_logged = True

        conf = float(self.conf_var.get())
        # imgsz: 학습 때 쓴 해상도(모델별로 다를 수 있어 로드 시점에 args.yaml에서 읽어둠)와
        # 다르게 추론하면 확신도가 떨어짐 - 예전엔 기본값(640)으로 돌아가서 실제 학습
        # 해상도(대부분 960)와 안 맞았던 게 conf=0.9에서 검출이 자주 실패하던 원인 중 하나.
        # augment=True(TTA): 이미지를 좌우반전/여러 배율로 나눠 여러 번 추론한 뒤 평균을
        # 내서 확신도/정확도를 높임 - 그만큼 느려지지만, 이미지 1장 단위 인터랙티브
        # 작업이라 감수할 만함(영상 처리처럼 프레임을 연속으로 처리하는 곳에는 안 씀).
        results = self.model.predict(
            source=self.current_cv_image, conf=conf, imgsz=self.model_imgsz, augment=True, verbose=False
        )
        r = results[0]
        boxes_xyxy = r.boxes.xyxy.cpu().numpy()
        boxes_conf = r.boxes.conf.cpu().numpy()

        # conf 기준(예: 0.9)은 엄격하게 유지하되, 그 기준으로 하나도 못 찾았을 때
        # 아예 빈 화면에서 손으로 그리게 하면 크롭이 부정확해서 OCR까지 같이 나빠짐.
        # 그래서 기준 미달이라도 후보가 있으면 참고용으로 1개만 보여주고, 직접
        # 확인/보정하게 함 - 자동으로 "확정"하는 게 아니라 어디까지나 참고용이라
        # conf를 낮춘 게 아니라 "엄격한 기준은 그대로, 후보만 보여줌"에 해당함.
        low_conf_fallback = False
        if len(boxes_xyxy) == 0:
            fallback = self.model.predict(
                source=self.current_cv_image, conf=0.1, imgsz=self.model_imgsz, augment=True, verbose=False
            )[0]
            fb_xyxy = fallback.boxes.xyxy.cpu().numpy()
            fb_conf = fallback.boxes.conf.cpu().numpy()
            if len(fb_xyxy) > 0:
                best_i = int(fb_conf.argmax())
                boxes_xyxy = fb_xyxy[best_i : best_i + 1]
                boxes_conf = fb_conf[best_i : best_i + 1]
                low_conf_fallback = True

        self.boxes = []
        h_img, w_img = self.current_cv_image.shape[:2]
        # r.boxes.xyxy 와 r.boxes.conf 는 같은 순서로 대응됨 - conf는 YOLO가 이 박스를
        # 번호판이라고 판단한 확신도(인식률)로, 박스마다 저장해서 화면에 같이 보여줌.
        for box, det_conf in zip(boxes_xyxy, boxes_conf):
            x1, y1, x2, y2 = [float(v) for v in box]
            x1, y1 = max(0.0, x1), max(0.0, y1)
            x2, y2 = min(float(w_img), x2), min(float(h_img), y2)
            if low_conf_fallback:
                # conf 기준 미달 참고용 후보 박스는 번호판 경계가 부정확할 가능성이 커서,
                # 이 상태로 바로 글자 인식을 돌리면 번호판 주변 글자(컨테이너 표기 등)까지
                # 같이 읽혀 노이즈 섞인 오독이 나오기 쉬움(예: "Noos ..." 같은 결과).
                # 그래서 여기서는 아예 인식을 시도하지 않고, 사람이 박스를 확인/보정한
                # 뒤 "선택한 박스만 다시 인식"을 눌렀을 때만 읽게 함.
                text, ocr_conf = "", None
            else:
                # v3.9: 영상 모드(2026-09-27)와 동일하게, 검출확신도가 낮은(<0.7) 박스는
                # 그릴/범퍼 위쪽에 치우쳐 잡히는 경우가 많아 아래쪽 여백을 더 크게 주고
                # CLAHE+샤프닝을 적용함 - 이미지 모드만 이 보정이 빠져있던 것을 맞춤.
                # (아래쪽 여백 비율은 영상 모드와 항상 동일하게 유지 - v3.10에서 0.65로
                # 되돌린 것도 여기 똑같이 반영됨)
                low_conf_box = det_conf < 0.7
                pad_ratio = 0.30 if low_conf_box else 0.18
                pad_bottom_ratio = 0.65 if low_conf_box else pad_ratio
                # 2026-09-28: 1줄/2줄 판단은 여백을 더하기 전 YOLO 박스 자체의 비율로
                # 해야 함 - 아래쪽만 비대칭으로 65% 여백을 주면 비율이 왜곡돼 명백한
                # 1줄 번호판이 확신도 낮을 때만 2줄로 잘못 분류되는 버그가 있었음.
                box_ratio = (x2 - x1) / (y2 - y1) if (y2 - y1) > 0 else None
                crop = self._padded_crop(
                    self.current_cv_image, x1, y1, x2, y2,
                    pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio,
                )
                if low_conf_box:
                    crop = self._enhance_low_conf_crop(crop)
                text, ocr_conf = self._ocr_plate(
                    crop, box_ratio=box_ratio,
                    pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio,
                )
                # 위 low_conf_fallback 케이스와 같은 이유("Noos ..." 등 노이즈 오독) -
                # 한글이 아예 없는 등 번호판을 읽으려던 시도로도 안 보이면 판독 불가로 처리.
                if text and not _is_plausible_plate_text(text):
                    text = ""
            self.boxes.append(
                {
                    "x1": x1, "y1": y1, "x2": x2, "y2": y2, "text": text,
                    "conf": float(det_conf), "low_conf": low_conf_fallback, "ocr_conf": ocr_conf,
                }
            )

        self.selected_box_idx = 0 if self.boxes else None
        self._show_cv_image(self.current_cv_image)
        self._update_result_summary()
        if low_conf_fallback:
            self.log(
                f"conf={conf} 기준으로는 검출 안 됨 - 참고용으로 확신도 {boxes_conf[0] * 100:.0f}%인 "
                f"후보 1개를 빨간 점선으로 표시했습니다 (박스 경계가 부정확할 수 있어 글자 인식은 "
                f"아직 돌리지 않았습니다 - 박스를 번호판에 맞게 확인/보정한 뒤 "
                f"'선택한 박스만 다시 인식'을 눌러주세요)"
            )
        else:
            self.log(
                f"검출+인식 완료 - 번호판 {len(self.boxes)}개 (모델: {self.model_var.get()}, conf={conf})"
            )

    def _box_label(self, b):
        """캔버스/결과 요약/저장 이미지에서 공통으로 쓰는 박스 표시 문구.
        인식된 번호판 글자 + 검출 인식률(YOLO가 "이게 번호판이다"라고 확신하는 정도) +
        글자 인식률(EasyOCR이 "이 글자를 이렇게 읽었다"라고 확신하는 정도)을 각각 따로 보여줌
        - 두 인식률은 서로 다른 단계에서 나오는 별개의 값이라 하나로 뭉뚱그리면 어느 쪽이
        문제인지(번호판 자체를 못 찾은 건지 vs 글자를 잘못 읽은 건지) 구분이 안 돼서 나눔.
        직접 그린 박스(검출 인식률 없음)는 글자 인식률만 표시.
        conf 기준 미달 참고용 후보(low_conf)는 앞에 경고 표시를 붙임.
        low_conf 박스는 경계가 부정확할 수 있어 일부러 자동 글자 인식을 하지 않으므로,
        아직 글자가 없으면 "판독 불가"가 아니라 무엇을 해야 하는지 알려주는 문구를 보여줌."""
        if b["text"]:
            text = b["text"]
        elif b.get("low_conf"):
            text = "(박스 확인 후 재인식 필요)"
        else:
            text = "(판독 불가)"
        conf = b.get("conf")
        ocr_conf = b.get("ocr_conf")
        parts = []
        if conf is not None:
            parts.append(f"검출 {conf * 100:.0f}%")
        if ocr_conf is not None:
            parts.append(f"글자인식 {ocr_conf * 100:.0f}%")
        prefix = "⚠ " if b.get("low_conf") else ""
        if parts:
            return f"{prefix}{text} ({' · '.join(parts)})"
        return f"{prefix}{text}" if prefix else text

    def _update_result_summary(self):
        if not self.boxes:
            self.result_var.set("검출 결과: 없음")
            self.result_label.configure(fg=FG_MUTED)
            return
        parts = [self._box_label(b) for b in self.boxes]
        self.result_var.set(f"검출 결과: 번호판 {len(self.boxes)}개\n" + "\n".join(parts))
        # 박스 중 하나라도 글자를 못 읽었거나(text 없음) 확신도 미달 참고용(low_conf)이면
        # 주황(주의)으로, 전부 정상적으로 읽혔으면 초록(정상)으로 표시 - 목록을 한 줄 한
        # 줄 읽지 않아도 "이번 결과 믿을 만한지"를 색으로 바로 알 수 있게 함.
        needs_attention = any(b.get("low_conf") or not b["text"] for b in self.boxes)
        self.result_label.configure(fg=STATUS_WARN if needs_attention else STATUS_OK)

    def _on_canvas_resize(self, _event=None):
        # 창 크기를 바꾸면 캔버스 크기도 바뀌므로, 마지막으로 보여준 화면(정지 이미지든
        # 영상 처리 중 미리보기/결과 프레임이든)을 새 캔버스 크기에 맞춰 다시 그려야 함.
        # 예전엔 current_cv_image(정지 이미지 모드에서만 채워지는 상태)만 기준으로 판단해서,
        # 영상을 불러와 처리하는 중에 창 크기를 바꾸면 "이미지 없음"으로 잘못 판단해 화면이
        # "이미지 또는 영상을 불러오세요" 안내 문구로 지워지는 버그가 있었음.
        if self._last_shown_cv is not None:
            self._show_cv_image(self._last_shown_cv)
        else:
            self._draw_canvas_placeholder()

    def _draw_canvas_placeholder(self):
        """빈 캔버스에 "사진 프레임" 모양 아이콘 + 안내 문구를 가운데에 그림 -
        버튼 아이콘용 _icon_photo는 16px 안팎용이라 이 크기로 확대하면 선이 뭉개지므로,
        이 자리 전용으로 비율에 맞게 새로 그림."""
        self.canvas.delete("all")
        w = self.canvas.winfo_width() or 760
        h = self.canvas.winfo_height() or 620
        cx, cy = w / 2, h / 2 - 30
        fw, fh = 88, 62
        x1, y1, x2, y2 = cx - fw / 2, cy - fh / 2, cx + fw / 2, cy + fh / 2
        self.canvas.create_polygon(
            _round_rect_points(x1, y1, x2, y2, 8), smooth=True, outline=BORDER, fill="", width=2,
        )
        self.canvas.create_oval(x1 + 14, y1 + 12, x1 + 26, y1 + 24, outline=BORDER, width=2)
        self.canvas.create_line(
            x1 + 8, y2 - 10, x1 + 32, y1 + 36, x1 + 52, y2 - 18, x2 - 8, y1 + 30,
            fill=BORDER, width=2, joinstyle="round",
        )
        self.canvas.create_text(
            cx, cy + fh / 2 + 26, text="이미지 또는 영상을 불러오세요",
            fill=FG_MUTED, font=("Segoe UI", 11),
        )
        self.canvas.create_text(
            cx, cy + fh / 2 + 50, text="오른쪽 '불러오기'에서 파일을 선택할 수 있습니다",
            fill=FG_MUTED, font=("Segoe UI", 9),
        )

    # ---------- 캔버스에 이미지 + 박스 그리기 ----------
    def _show_cv_image(self, cv_img):
        self._last_shown_cv = cv_img  # 창 크기 조절 시 다시 그리기용(_on_canvas_resize 참고)
        self.canvas.delete("all")
        rgb = cv2.cvtColor(cv_img, cv2.COLOR_BGR2RGB)
        img = Image.fromarray(rgb)
        orig_w, orig_h = img.size

        cw = self.canvas.winfo_width()
        ch = self.canvas.winfo_height()
        if cw <= 1:
            cw = 760
        if ch <= 1:
            ch = 620

        scale = min(cw / orig_w, ch / orig_h, 1.0)
        disp_w, disp_h = max(1, int(orig_w * scale)), max(1, int(orig_h * scale))
        img_disp = img.resize((disp_w, disp_h))
        self.display_image = ImageTk.PhotoImage(img_disp)
        self.disp_scale = scale
        self.canvas.create_image(0, 0, anchor="nw", image=self.display_image, tags=("bg",))
        self._redraw_boxes()

    def _redraw_boxes(self):
        self.canvas.delete("box")
        for i, b in enumerate(self.boxes):
            x1, y1, x2, y2 = (b["x1"] * self.disp_scale, b["y1"] * self.disp_scale,
                               b["x2"] * self.disp_scale, b["y2"] * self.disp_scale)
            selected = i == self.selected_box_idx
            color = "#00ff66" if selected else "#ffaa00"
            rect_kwargs = {"outline": color, "width": 2, "tags": ("box",)}
            if b.get("low_conf"):
                # conf 기준 미달 참고용 후보는 빨간 점선으로 확실히 구분되게 표시
                color = "#ff5252"
                rect_kwargs["outline"] = color
                rect_kwargs["dash"] = (5, 3)
            self.canvas.create_rectangle(x1, y1, x2, y2, **rect_kwargs)
            label = self._box_label(b)
            # 라벨은 보통 박스 위쪽 바깥에 표시하는데, 박스가 캔버스 맨 위쪽에 거의 붙어있으면
            # (예: 번호판 사진 자체를 꽉 채운 이미지를 열었거나, 위쪽 여백 없이 박스를 그린 경우)
            # 캔버스 바깥(위쪽)으로 텍스트가 밀려나가 아예 안 보이는 문제가 있었음.
            # 위쪽에 라벨 한 줄(약 18px)이 들어갈 여백이 없으면, 박스 바깥 위쪽 대신
            # 박스 안쪽 위쪽에 붙여서 항상 보이게 함.
            if y1 >= 18:
                label_x, label_y, anchor = x1 + 2, y1 - 10, "sw"
            else:
                label_x, label_y, anchor = x1 + 4, y1 + 2, "nw"
            self.canvas.create_text(
                label_x, label_y, text=label, fill=color, anchor=anchor, tags=("box",),
                font=("맑은 고딕", 11, "bold"),
            )
            if selected:
                for hx, hy in [(x1, y1), (x2, y1), (x1, y2), (x2, y2)]:
                    self.canvas.create_rectangle(
                        hx - HANDLE, hy - HANDLE, hx + HANDLE, hy + HANDLE,
                        fill="white", outline=color, tags=("box",),
                    )

    # ---------- 박스 드래그(마우스): 모서리 크기조정 / 박스 이동 / 빈 곳에 새 박스 그리기 ----------
    def _on_canvas_press(self, event):
        x, y = event.x, event.y
        self._drag_moved = False  # 이번 클릭에서 실제로 마우스를 움직였는지 새로 추적 시작

        # 1) 선택된 박스의 모서리 핸들 위인지 먼저 확인 -> 크기 조정
        if self.selected_box_idx is not None and self.boxes:
            b = self.boxes[self.selected_box_idx]
            x1, y1, x2, y2 = (b["x1"] * self.disp_scale, b["y1"] * self.disp_scale,
                               b["x2"] * self.disp_scale, b["y2"] * self.disp_scale)
            for name, (hx, hy) in {"tl": (x1, y1), "tr": (x2, y1), "bl": (x1, y2), "br": (x2, y2)}.items():
                if abs(x - hx) <= HANDLE + 3 and abs(y - hy) <= HANDLE + 3:
                    self.drag_mode = "resize"
                    self.drag_handle = name
                    return

        # 2) 기존 박스 안을 클릭했으면 선택하고 이동 준비
        for i, b in enumerate(self.boxes):
            x1, y1, x2, y2 = (b["x1"] * self.disp_scale, b["y1"] * self.disp_scale,
                               b["x2"] * self.disp_scale, b["y2"] * self.disp_scale)
            if x1 <= x <= x2 and y1 <= y <= y2:
                self.selected_box_idx = i
                self.drag_mode = "move"
                self._move_start = (x, y)
                self._move_orig = dict(b)
                self._redraw_boxes()
                return

        # 3) 빈 곳을 클릭했으면 새 박스를 그리기 시작 (YOLO가 놓친 번호판을 직접 지정할 때 사용)
        if self.current_cv_image is None:
            return
        ox, oy = x / self.disp_scale, y / self.disp_scale
        self._new_box_origin = (ox, oy)
        # 사람이 직접 그린 박스라 YOLO 인식률이 없음 -> conf: None (화면에 %는 안 붙음)
        self.boxes.append({"x1": ox, "y1": oy, "x2": ox, "y2": oy, "text": "", "conf": None})
        self.selected_box_idx = len(self.boxes) - 1
        self.drag_mode = "new"
        self._redraw_boxes()

    def _on_canvas_drag(self, event):
        if self.drag_mode is None or self.selected_box_idx is None or not self.boxes:
            return
        self._drag_moved = True  # 마우스를 눌렀다 뗀 게 아니라 실제로 끌었음 -> release에서 자동 재인식 트리거
        b = self.boxes[self.selected_box_idx]
        img_h, img_w = self.current_cv_image.shape[:2]

        if self.drag_mode == "resize":
            ox = max(0, event.x) / self.disp_scale
            oy = max(0, event.y) / self.disp_scale
            if "l" in self.drag_handle:
                b["x1"] = min(ox, b["x2"] - 5)
            if "r" in self.drag_handle:
                b["x2"] = max(ox, b["x1"] + 5)
            if "t" in self.drag_handle:
                b["y1"] = min(oy, b["y2"] - 5)
            if "b" in self.drag_handle:
                b["y2"] = max(oy, b["y1"] + 5)

        elif self.drag_mode == "move":
            dx = (event.x - self._move_start[0]) / self.disp_scale
            dy = (event.y - self._move_start[1]) / self.disp_scale
            w = self._move_orig["x2"] - self._move_orig["x1"]
            h = self._move_orig["y2"] - self._move_orig["y1"]
            new_x1 = max(0, min(self._move_orig["x1"] + dx, img_w - w))
            new_y1 = max(0, min(self._move_orig["y1"] + dy, img_h - h))
            b["x1"], b["y1"] = new_x1, new_y1
            b["x2"], b["y2"] = new_x1 + w, new_y1 + h

        elif self.drag_mode == "new":
            ox = max(0, min(event.x / self.disp_scale, img_w))
            oy = max(0, min(event.y / self.disp_scale, img_h))
            ox0, oy0 = self._new_box_origin
            b["x1"], b["x2"] = (ox0, ox) if ox0 <= ox else (ox, ox0)
            b["y1"], b["y2"] = (oy0, oy) if oy0 <= oy else (oy, oy0)

        self._redraw_boxes()

    def _on_canvas_release(self, event):
        # 새로 그리던 박스가 너무 작으면(그냥 클릭만 한 경우) 실수로 생긴 빈 박스이니 제거
        deleted = False
        if self.drag_mode == "new" and self.selected_box_idx is not None and self.boxes:
            b = self.boxes[self.selected_box_idx]
            if (b["x2"] - b["x1"]) < 5 or (b["y2"] - b["y1"]) < 5:
                del self.boxes[self.selected_box_idx]
                self.selected_box_idx = len(self.boxes) - 1 if self.boxes else None
                self._redraw_boxes()
                deleted = True

        # 박스를 새로 그렸거나(new) 모서리로 크기를 조정했거나(resize) 위치를 옮겼으면(move),
        # YOLO가 자동으로 찾은 번호판과 똑같이 곧바로 글자 인식을 돌려서 "검출/글자인식 %"가
        # 바로 뜨게 함 - 예전엔 "선택한 박스만 다시 인식" 버튼을 따로 눌러야만 반영돼서
        # 불편했음. 실제로 드래그가 일어난 경우에만 돌리고(단순히 박스를 클릭해서
        # 선택만 한 경우는 크롭이 그대로라 다시 돌릴 필요 없음 - self._drag_moved로 구분).
        if not deleted and self._drag_moved and self.drag_mode in ("new", "resize", "move"):
            if self.selected_box_idx is not None and self.boxes:
                self.rerun_ocr_selected()

        self.drag_mode = None
        self.drag_handle = None

    def rerun_ocr_selected(self):
        if self.selected_box_idx is None or not self.boxes:
            messagebox.showwarning("알림", "선택된 박스가 없습니다. 박스를 클릭해서 선택하세요.")
            return
        if self.current_cv_image is None:
            return
        b = self.boxes[self.selected_box_idx]
        x1, y1, x2, y2 = int(b["x1"]), int(b["y1"]), int(b["x2"]), int(b["y2"])
        crop = self._padded_crop(self.current_cv_image, x1, y1, x2, y2)
        if b.get("low_conf"):
            # 영상 모드(v3.6)와 동일하게, 참고용 후보(경계 부정확 가능성 있음) 박스는
            # 화질 보정을 한 번 거쳐 인식을 시도함 - 확신도 높은 박스는 건드리지 않음.
            crop = self._enhance_low_conf_crop(crop)
        text, ocr_conf = self._ocr_plate(crop, pad_ratio=0.18, pad_bottom_ratio=0.18)
        if text and not _is_plausible_plate_text(text):
            text = ""
        b["text"] = text
        b["ocr_conf"] = ocr_conf
        self._redraw_boxes()
        self._update_result_summary()
        self.log(f"수정한 영역으로 재인식: {text if text else '(판독 불가)'}")

    def delete_selected_box(self):
        if self.selected_box_idx is None or not self.boxes:
            messagebox.showwarning("알림", "삭제할 박스가 없습니다. 박스를 먼저 클릭해서 선택하세요.")
            return
        del self.boxes[self.selected_box_idx]
        self.selected_box_idx = 0 if self.boxes else None
        self._redraw_boxes()
        self._update_result_summary()
        self.log("선택한 박스를 삭제했습니다.")

    # ---------- 저장 ----------
    def _bake_annotated(self, cv_img, boxes):
        """현재 박스+OCR 텍스트를 이미지에 실제로 그려서 반환 (저장/영상용)"""
        annotated = cv_img.copy()
        for b in boxes:
            x1, y1, x2, y2 = int(b["x1"]), int(b["y1"]), int(b["x2"]), int(b["y2"])
            cv2.rectangle(annotated, (x1, y1), (x2, y2), (0, 200, 0), 2)
        if boxes:
            rgb = cv2.cvtColor(annotated, cv2.COLOR_BGR2RGB)
            pil_img = Image.fromarray(rgb)
            draw = ImageDraw.Draw(pil_img)
            for b in boxes:
                x1, y1 = int(b["x1"]), int(b["y1"])
                label = self._box_label(b)
                ty = max(0, y1 - 30)
                tw = 12 * len(label) + 10
                draw.rectangle([x1, ty, x1 + tw, ty + 28], fill=(0, 200, 0))
                draw.text((x1 + 5, ty + 2), label, font=self.korean_font, fill=(0, 0, 0))
            annotated = cv2.cvtColor(np.array(pil_img), cv2.COLOR_RGB2BGR)
        return annotated

    def save_current_result(self):
        if self.current_cv_image is None or not self.boxes:
            messagebox.showwarning("알림", "저장할 검출 결과가 없습니다.")
            return
        annotated = self._bake_annotated(self.current_cv_image, self.boxes)
        default_name = (
            Path(self.current_image_path).stem + "_detected.jpg" if self.current_image_path else "detected.jpg"
        )
        path = filedialog.asksaveasfilename(
            defaultextension=".jpg", initialfile=default_name, filetypes=[("JPEG", "*.jpg")]
        )
        if not path:
            return
        cv2.imwrite(path, annotated)
        self.log(f"결과 이미지 저장됨(수정 반영): {path}")
        messagebox.showinfo("저장 완료", f"저장됨: {path}")

    # ---------- 영상 (자동 처리, 박스 수동 보정은 이미지에서만 지원) ----------
    def open_video(self):
        if self.model is None:
            messagebox.showwarning("알림", "모델이 아직 로드되지 않았습니다.")
            return
        path = filedialog.askopenfilename(
            title="영상 선택",
            filetypes=[("영상 파일", "*.mp4 *.avi *.mov *.mkv"), ("모든 파일", "*.*")],
        )
        if not path:
            return
        out_path = str(Path(path).with_name(Path(path).stem + "_detected.mp4"))
        self.log(
            f"영상 처리 시작: {path} (1단계: 프레임별 검출+추적 -> 2단계: 번호판별 최종 확정 "
            "결과로 저장 - 이미지 한 장보다 훨씬 느릴 수 있습니다)"
        )
        threading.Thread(target=self._process_video, args=(path, out_path), daemon=True).start()

    def _process_video(self, in_path, out_path):
        """_process_video_impl을 감싸는 얇은 래퍼. 이 함수는 threading.Thread의
        target으로 백그라운드 스레드에서 돌아가는데, Python은 메인 스레드가 아닌
        스레드에서 발생한 예외를 sys.excepthook(위에서 crash_log.txt로 연결해둔 것)으로
        잡아주지 않음 - 그냥 조용히 스레드가 죽어버려서, 창은 멀쩡한데(응답 없음이 아님)
        진행률만 영원히 멈춘 것처럼 보이는 버그가 있었음(1499프레임짜리 실제 영상에서
        실제로 발생 확인함). 그래서 여기서 직접 try/except로 감싸 crash_log.txt에 기록하고,
        화면 로그에도 눈에 띄게 남김."""
        try:
            self._process_video_impl(in_path, out_path)
        except Exception:
            _log_crash(*sys.exc_info())
            self.root.after(0, lambda: self.result_var.set("영상 처리 중 오류로 중단됨"))
            self.root.after(0, lambda: self.log(
                "영상 처리 중 예상 못한 오류로 중단됐습니다. 자세한 내용은 exe와 같은 폴더의 "
                "crash_log.txt 에 기록돼 있어요."
            ))

    def _process_video_impl(self, in_path, out_path):
        """영상 처리: 같은 번호판이 여러 프레임에 걸쳐 나오는 걸 활용하기 위해 2단계로
        처리함.
        1단계(추적): 프레임마다 빠른 설정(TTA/저조도 재시도 끔, 이미지 경로와 동일하게
        속도 우선)으로 검출+인식하되, 같은 번호판을 IOU 기반으로 프레임 간에 하나의
        트랙으로 묶어서 각 트랙별로 확신도 상위 후보들만 보관함. 트럭이 화면을 벗어나
        트랙이 끝나면(TRACK_MAX_MISS 프레임 이상 안 보이면) 그 상위 후보만 TTA+beam으로
        정밀 재인식해서 번호판 문법 검증까지 거친 최종 답 하나로 확정함(_PlateTrack.finalize).
        2단계(저장): 영상을 처음부터 다시 읽으면서, 이번엔 프레임별 결과 대신 그 프레임의
        번호판이 속한 트랙의 "최종 확정 결과"를 그려서 저장함 - 그래서 같은 트럭이 화면에
        머무는 동안 자막이 프레임마다 흔들리지 않고 하나로 안정적으로 표시됨."""
        conf = float(self.conf_var.get())
        video_conf = min(conf, VIDEO_DETECT_CONF_CAP)
        cap = cv2.VideoCapture(in_path)
        if not cap.isOpened():
            self.root.after(0, lambda: self.log("영상을 열 수 없습니다."))
            return
        fps = cap.get(cv2.CAP_PROP_FPS) or 30
        w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        # 미리보기 화면 갱신(_bake_annotated+_show_cv_image, 매번 cv2->PIL->ImageTk 변환+
        # 캔버스 다시 그리기)이 생각보다 무거워서, 프레임이 많은 긴 영상에서는 이게
        # 너무 자주 일어나 검출 스레드랑 자원을 다투며 화면이 버벅여 보이는 원인이 됨.
        # 짧은 영상은 30프레임마다(기존과 동일)로 충분히 부드럽게, 긴 영상은 전체적으로
        # 대략 15번 정도만 갱신되도록 간격을 늘림(진행률 텍스트는 별도로 더 자주 갱신되므로
        # 진행 상황을 못 보는 건 아님).
        preview_interval = max(30, total // 15) if total else 30

        start = time.time()
        conf_note = (
            f" (설정된 conf {conf:.2f} 대신 {video_conf:.2f}로 낮춰서 검출 - 영상은 트랙 "
            "종료 시 문법검증+다수결로 한 번 더 걸러지므로 프레임별로는 덜 엄격해도 됨)"
            if conf > VIDEO_DETECT_CONF_CAP else ""
        )
        self.root.after(0, lambda: self.log(
            "영상 처리 1/2단계: 프레임별 검출 + 번호판 추적 중 (같은 번호판이 여러 프레임에 "
            "걸쳐 나온 걸 하나로 묶어, 화면에 머무는 동안 나온 결과 중 최선을 골라 최종 답을 "
            f"정합니다){conf_note}..."
        ))

        frame_records = []       # index -> [{"x1","y1","x2","y2","conf","text","ocr_conf","track_id"}, ...]
        tracks = {}              # track_id -> _PlateTrack (아직 화면에 있는/최근까지 있던 트랙)
        finished_tracks = {}     # track_id -> _PlateTrack (최종 확정됨)
        next_track_id = 1

        frame_idx = 0
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            # 1단계는 이미지 경로와 동일하게 속도 우선(augment/TTA, 저조도 재시도 끔) -
            # 정밀 재인식은 트랙이 끝난 뒤 상위 후보에만 적용함(아래 finalize에서).
            results = self.model.predict(source=frame, conf=video_conf, imgsz=self.model_imgsz, verbose=False)
            boxes_xyxy = results[0].boxes.xyxy.cpu().numpy()
            boxes_conf = results[0].boxes.conf.cpu().numpy()
            detections = [
                {"box": tuple(float(v) for v in box), "det_conf": float(det_conf)}
                for box, det_conf in zip(boxes_xyxy, boxes_conf)
            ]
            # 이번 프레임 시작 시점에 살아있던 트랙 목록(아래 깜빡임 방지용 - 이 프레임에서
            # 새로 생기는 트랙과 구분하기 위해 미리 스냅샷 떠둠)
            active_tids_before = list(tracks.keys())

            # ---- 이번 프레임 검출을 기존 활성 트랙에 IOU로 매칭 (없으면 새 트랙 시작) ----
            unmatched = list(range(len(detections)))
            assigned = {}
            for tid, tr in tracks.items():
                best_j, best_iou = None, IOU_MATCH_THRESHOLD
                for j in unmatched:
                    v = _iou(tr.last_box, detections[j]["box"])
                    if v > best_iou:
                        best_iou, best_j = v, j
                if best_j is not None:
                    assigned[best_j] = tid
                    unmatched.remove(best_j)

            frame_record = []
            matched_tids_this_frame = set()
            for j, det in enumerate(detections):
                x1, y1, x2, y2 = det["box"]

                if j in assigned:
                    tid = assigned[j]
                    tr = tracks[tid]
                    should_ocr = (
                        len(tr.candidates) < TRACK_TOPK
                        or tr.last_ocr_frame_idx is None
                        or (frame_idx - tr.last_ocr_frame_idx) >= OCR_FRAME_STRIDE
                    )
                else:
                    tid = next_track_id
                    next_track_id += 1
                    tracks[tid] = _PlateTrack(tid, det["box"], frame_idx, det["det_conf"])
                    should_ocr = True  # 새로 보는 트랙은 첫 프레임부터 반드시 OCR

                if should_ocr:
                    # 검출 확신도가 낮은 박스는 YOLO가 번호판 테두리를 살짝 부정확하게 잡았을
                    # 가능성이 높음(2026-09-27: 인천항 실제 영상에서 검출확신도 49%짜리 박스의
                    # 번호판이, 인식 모델을 재학습해서 가중치를 통째로 바꿔도 "7 D"로 완전히
                    # 똑같이 오인식되는 사례를 발견 - 모델이 아무리 바뀌어도 결과가 그대로라는
                    # 건 학습 부족이 아니라 CRNN에 넘어가는 크롭 자체가 부족(글자 일부 잘림)할
                    # 가능성을 시사함). 기준을 0.7 -> 0.9로 올려봤다가(v3.1), 대부분의 박스에
                    # 30% 여백이 적용되면서 오히려 배경/옆 글자가 크롭에 끌려들어와 전반적으로
                    # 글자 인식이 나빠지는 게 실제로 확인되어(2026-09-27) 0.7로 되돌림 - 이
                    # 기준은 "명백히 애매한 소수 박스"에만 넓은 여백을 주려는 의도였는데, 너무
                    # 많은 박스가 여기 걸리면 역효과가 남. 0.7이 지금까지 검증된 값.
                    low_conf_box = det["det_conf"] < 0.7
                    pad_ratio = 0.30 if low_conf_box else 0.18
                    # 확신도 낮은 박스는 실제 번호판보다 위쪽(그릴/범퍼 위)에 치우쳐 잡히는
                    # 경우가 실제 영상에서 확인되어(2026-09-27), 이런 박스만 아래쪽 여백을
                    # 훨씬 더 크게 줘서 박스 아래에 남아있을 번호판을 크롭에 포함시킴(위/좌우는
                    # 기존과 동일 - 위쪽으로 어긋난다는 증거만 있고 다른 방향 증거는 없어서
                    # 그 방향만 넓힘). 2026-09-27 추가 확인: 검출확신도 47%짜리 박스 한 건을
                    # 픽셀 단위로 직접 측정해보니 박스가 그릴 위에 잡혀있고, 0.65 비율로는
                    # 번호판 아래쪽 끝에 약 3~4px 못 미쳤음 - 이 한 건만 보고 1.0으로
                    # 올렸다가(v3.9) 실제 영상 전체로는 "대체적으로 글자 개판"이라는 광범위한
                    # 악화가 나와 바로 0.65로 되돌림(v3.10) - 아래쪽 여백을 넓히면 많은 저확신도
                    # 박스에서 번호판 아래의 배경/범퍼까지 크롭에 끌려들어와 역효과가 더 컸던
                    # 것으로 추정. 그 47% 박스 하나는 다시 판독 불가로 돌아가더라도, 훨씬 많은
                    # 다른 저확신도 박스들을 지키는 쪽이 이득이라 판단.
                    pad_bottom_ratio = 0.65 if low_conf_box else pad_ratio
                    # 2026-09-28: 이미지 모드와 동일하게, 1줄/2줄 판단은 비대칭 여백을
                    # 더하기 전 박스 자체 비율로 해야 왜곡이 없음.
                    box_ratio = (x2 - x1) / (y2 - y1) if (y2 - y1) > 0 else None
                    crop = self._padded_crop(
                        frame, x1, y1, x2, y2, pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio
                    )
                    if low_conf_box:
                        # 확신도 낮은 박스는 대부분 멀리 있거나 흐릿해서 대비가 낮은 경우가
                        # 많음(2026-09-27: 박스 위치를 고쳐도 여전히 판독 불가인 사례들에서
                        # 확인) - 없는 디테일을 만들어낼 순 없지만, 있는 대비/경계를 더 뚜렷하게
                        # 만들면 도움이 될 수 있어 CLAHE+약한 샤프닝을 시도해봄. 이미 확신도
                        # 높은 박스는 잘 되고 있으니 건드리지 않음(0.9 패딩 실패 교훈 - 잘 되는
                        # 것까지 건드리면 역효과가 날 수 있어 문제 있는 대상에만 국한함).
                        crop = self._enhance_low_conf_crop(crop)
                    text, ocr_conf = self._ocr_plate(
                        crop, retry_if_low_conf=False, box_ratio=box_ratio,
                        pad_ratio=pad_ratio, pad_bottom_ratio=pad_bottom_ratio,
                    )
                    tracks[tid].last_ocr_frame_idx = frame_idx
                else:
                    crop, text, ocr_conf = None, "", None

                tracks[tid].add(det["box"], frame_idx, text, ocr_conf, crop, det["det_conf"])
                matched_tids_this_frame.add(tid)

                # 화면(저장 영상)에는 raw 검출 좌표 대신 스무딩된 좌표를 그림 - 번호판 자체는
                # 안 움직여도 프레임마다 YOLO 검출 박스가 몇 픽셀씩 미세하게 흔들리는 것 때문에
                # 결과 영상에서 박스가 "떨리는" 것처럼 보이는 문제(2026-09-27 확인)를 줄이기
                # 위함. OCR 크롭은 그대로 이번 프레임의 raw 좌표(x1,y1,x2,y2)를 씀 - 인식
                # 정확도에는 영향 없고, 순전히 화면 표시만 부드럽게 함.
                sx1, sy1, sx2, sy2 = tracks[tid].smooth_box
                frame_record.append({
                    "x1": sx1, "y1": sy1, "x2": sx2, "y2": sy2,
                    "conf": det["det_conf"], "text": text, "ocr_conf": ocr_conf, "track_id": tid,
                })

            # ---- 결과 영상 박스 깜빡임 방지 (2026-09-27 실제 영상에서 확인된 문제) ----
            # 트랙의 최종 텍스트는 finalize에서 안정적으로 고정되지만, 박스를 "그릴지 말지"는
            # 이 프레임에서 검출이 실제로 잡혔는지에 좌우되고 있었음 - 순간적인 블러/각도 변화로
            # 검출이 한두 프레임 놓쳐도 트랙 자체는 TRACK_MAX_MISS 안에서 계속 살아있는데(화면
            # 이탈로 보지 않음), 그 프레임엔 frame_records에 기록이 아예 없어 2단계 저장 영상에서
            # 박스가 껐다 켜졌다 하는 것처럼 보였음. 트랙이 아직 안 끝났으면(곧 종료될 프레임
            # 제외) 마지막으로 확인된 위치를 그대로 유지해서 박스를 계속 그려 깜빡임을 없앰
            # (텍스트는 어차피 최종 확정값으로 덮어써지므로 여기선 빈 텍스트로 둠).
            for tid in active_tids_before:
                if tid in matched_tids_this_frame:
                    continue
                tr = tracks[tid]
                if tr.miss + 1 > TRACK_MAX_MISS:
                    continue  # 이번에 화면 이탈로 종료될 트랙 - 마지막 프레임까지 억지로 안 그림
                bx1, by1, bx2, by2 = tr.smooth_box
                frame_record.append({
                    "x1": bx1, "y1": by1, "x2": bx2, "y2": by2,
                    "conf": tr.last_det_conf, "text": "", "ocr_conf": None, "track_id": tid,
                })

            frame_records.append(frame_record)

            # ---- 이번 프레임에서 못 본 트랙은 미스 카운트 증가, 오래 안 보이면 종료(화면 이탈) ----
            for tid in list(tracks.keys()):
                if tid in matched_tids_this_frame:
                    continue
                tracks[tid].miss += 1
                if tracks[tid].miss > TRACK_MAX_MISS:
                    tr = tracks.pop(tid)
                    tr.finalize(self.crnn, self.crnn_conf_threshold, self._ocr_once)
                    finished_tracks[tid] = tr

            frame_idx += 1
            if frame_idx % 10 == 0 or frame_idx == total:
                pct = (frame_idx / total * 100 / 2) if total else 0  # 전체 작업의 절반(1단계)
                elapsed1 = time.time() - start
                eta_note = ""
                if total and frame_idx > 0:
                    remaining1 = elapsed1 / frame_idx * (total - frame_idx)
                    eta_note = f" - 1단계 남은 시간 {self._fmt_duration(remaining1)}"
                self.root.after(0, lambda i=frame_idx, p=pct, e=eta_note: self.result_var.set(
                    f"영상 처리 1/2단계 (검출+추적)... {i}/{total} ({p:.0f}%){e}"
                ))
            if frame_idx % preview_interval == 0:
                # 1단계 미리보기는 속도 우선으로 대충 읽은 글자라 자주 틀림(트랙이 끝나야
                # TTA+beam+다수결로 정밀 재인식함) - 그걸 그대로 보여주면 마치 최종 인식
                # 결과가 틀린 것처럼 오해하기 쉬워서("글자 인식을 못하네"), 미리보기임을
                # 표시해 아직 확정 전이라는 걸 명확히 함. 실제 frame_records/최종 저장
                # 영상에는 영향 없음(이건 화면 표시용 복사본만 건드림).
                preview_boxes = [
                    {**bx, "text": f"(확인중) {bx['text']}" if bx["text"] else bx["text"]}
                    for bx in frame_record
                ]
                self.root.after(
                    0, lambda f=frame, b=preview_boxes: self._show_cv_image(self._bake_annotated(f, b))
                )

        cap.release()
        # 영상 끝까지 화면에 남아있던(마지막까지 안 사라진) 트랙들도 마무리 확정
        for tid, tr in tracks.items():
            tr.finalize(self.crnn, self.crnn_conf_threshold, self._ocr_once)
            finished_tracks[tid] = tr

        n_tracks = len(finished_tracks)
        if n_tracks == 0:
            self.root.after(0, lambda: self.log(
                f"영상 전체에서 번호판을 하나도 못 찾았습니다 (사용한 conf={video_conf:.2f}). "
                "번호판이 화면에서 너무 작거나(먼 거리/광각), 이 각도가 학습 데이터와 많이 "
                "다를 수 있습니다 - conf를 더 낮추거나 다른 모델로 시도해보세요."
            ))
        self.root.after(0, lambda: self.log(
            f"영상 처리 2/2단계: 추적된 번호판 {n_tracks}개의 최종 확정 결과를 영상에 표시하며 저장 중..."
        ))
        stage2_start = time.time()  # 2단계 자체 남은 시간 추정용(1단계와 속도가 달라 따로 잼)

        final_text = {tid: tr.final_text for tid, tr in finished_tracks.items() if tr.final_text}
        final_conf = {tid: tr.final_conf for tid, tr in finished_tracks.items() if tr.final_text}
        # finished_tracks에는 있지만 final_text가 없는 트랙 = finalize()가 "번호판 문법에
        # 맞는 답이 하나도 없어 판독 불가로 처리하기로 확정한" 트랙(위 finalize 참고). 이런
        # 트랙은 프레임별 raw 추측(b["text"], 대부분 똑같이 신뢰 못 할 값)을 보여주지 않고
        # "(판독 불가)"로 일관되게 표시해야 하므로, 아래 draw 루프에서 finished_tracks 소속
        # 여부로 구분함(finished_tracks에 없는 트랙은 이론상 없어야 하지만 안전망으로 남겨둠).

        cap2 = cv2.VideoCapture(in_path)
        # mp4v(MPEG-4 Part2)로 쓴 mp4가 윈도우 기본 재생 앱 등 일부 플레이어에서 뚝뚝
        # 끊겨 재생되는 문제가 실제로 확인됨(VLC 등은 대개 괜찮지만 재생기마다 codec
        # 지원이 달라서 생기는 흔한 문제). h264(avc1)가 훨씬 더 널리/매끄럽게 재생되므로
        # 먼저 시도하고, 이 컴퓨터의 OpenCV/ffmpeg 빌드가 h264 인코더를 지원 안 해서
        # 열기 자체가 실패하면(isOpened()==False) 예전 방식(mp4v)으로 되돌아감 - 어느
        # 쪽이든 최소한 지금보다 나빠지진 않음.
        fourcc_h264 = cv2.VideoWriter_fourcc(*"avc1")
        writer = cv2.VideoWriter(out_path, fourcc_h264, fps, (w, h))
        if not writer.isOpened():
            writer.release()
            self.root.after(0, lambda: self.log(
                "이 컴퓨터에서 h264 인코더를 못 써서 예전 방식(mp4v)으로 저장합니다 - "
                "재생 플레이어에 따라 끊길 수 있어요(VLC 권장)."
            ))
            fourcc = cv2.VideoWriter_fourcc(*"mp4v")
            writer = cv2.VideoWriter(out_path, fourcc, fps, (w, h))
        frame_idx = 0
        while True:
            ok, frame = cap2.read()
            if not ok:
                break
            src_boxes = frame_records[frame_idx] if frame_idx < len(frame_records) else []
            boxes_for_draw = [
                {
                    "x1": b["x1"], "y1": b["y1"], "x2": b["x2"], "y2": b["y2"],
                    "conf": b["conf"],
                    "text": (
                        final_text.get(b["track_id"], "")
                        if b["track_id"] in finished_tracks else b["text"]
                    ),
                    "ocr_conf": final_conf.get(b["track_id"], b["ocr_conf"]),
                }
                for b in src_boxes
            ]
            annotated = self._bake_annotated(frame, boxes_for_draw)
            writer.write(annotated)
            frame_idx += 1
            if frame_idx % 10 == 0 or frame_idx == total:
                pct = 50 + (frame_idx / total * 100 / 2) if total else 50  # 전체 작업의 나머지 절반(2단계)
                elapsed2 = time.time() - stage2_start
                eta_note = ""
                if total and frame_idx > 0:
                    remaining2 = elapsed2 / frame_idx * (total - frame_idx)
                    eta_note = f" - 남은 시간 {self._fmt_duration(remaining2)}"
                self.root.after(0, lambda i=frame_idx, p=pct, e=eta_note: self.result_var.set(
                    f"영상 처리 2/2단계 (최종본 저장)... {i}/{total} ({p:.0f}%){e}"
                ))
            if frame_idx % 30 == 0:
                self.root.after(0, lambda a=annotated: self._show_cv_image(a))

        cap2.release()
        writer.release()
        elapsed = time.time() - start
        self.root.after(0, lambda: self.result_var.set(f"영상 처리 완료 ({elapsed:.1f}초)"))
        self.root.after(0, lambda: self.log(
            f"영상 처리 완료 ({elapsed:.1f}초, 번호판 트랙 {n_tracks}개) - 저장됨: {out_path}"
        ))
        self.root.after(0, lambda: messagebox.showinfo("완료", f"검출 영상이 저장되었습니다:\n{out_path}"))


def main():
    try:
        if sys.platform == "win32":
            # Windows가 DPI 배율(125%, 150% 등)을 이 앱 모르게 화면에 입혀서 보여주면
            # (DPI 가상화) 실제 창이 코드에서 지정한 크기보다 훨씬 크게 렌더링되어
            # 화면 아래로 넘쳐서 SYSTEM LOG/버튼이 잘려 보임. 이 앱이 고해상도를
            # 직접 처리한다고 미리 알려주면 창 크기가 지정한 값 그대로 정확히 렌더링됨.
            try:
                import ctypes
                ctypes.windll.shcore.SetProcessDpiAwareness(1)
            except Exception:
                try:
                    import ctypes
                    ctypes.windll.user32.SetProcessDPIAware()
                except Exception:
                    pass
        root = tk.Tk()
        PlateDetectorApp(root)
        root.mainloop()
    except Exception:
        _log_crash(*sys.exc_info())
        raise


if __name__ == "__main__":
    main()
