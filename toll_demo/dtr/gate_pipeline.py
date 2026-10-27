"""
차량 출입 게이트용 번호판 파이프라인 (YOLO 탐지 + ByteTrack 추적 + OCR 확정)

동작 규칙
- 차량(번호판)마다 ID를 붙여 추적하고, 번호판이 '읽을 수 있는 크기'(MIN_PLATE_W)가 된 순간부터 시간을 잰다.
- OCR은 차량당 필요한 만큼만 실행한다. 번호판 형식에 맞는 같은 결과가 CONFIRM_AGREE번 나오고
  신뢰도가 CONFIRM_MIN_CONF 이상이면 확정(CONFIRMED)한다.
- DEADLINE_MS 안에 확정하지 못하면 임의로 통과시키지 않고 TIMEOUT / NO_VALID_READ 로 따로 기록한다.
- 결과는 콘솔과 CSV로 남긴다.

실행: python gate_pipeline.py   (창에서 q 또는 ESC 로 종료)
"""
import os
import sys
import time
import csv
import re

def _env(name, default):
    """환경변수로 경로를 덮어쓸 수 있게 한다 (다른 노트북에서 코드 수정 없이 실행하려는 용도). 없으면 아래 기본값 사용."""
    return os.environ.get(name) or default


sys.path.append(_env('GATE_DTR_DIR', r'C:\plate_dataset\deep-text-recognition-benchmark'))  # model.py, utils.py 위치

import cv2
import numpy as np
import torch
import torch.nn.functional as F
from PIL import Image, ImageDraw, ImageFont
from ultralytics import YOLO
from model import Model
from utils import CTCLabelConverter

# ============================ 설정 (실제 경로로 수정) ============================
VIDEO_PATH = _env('GATE_VIDEO', r'C:\plate_dataset\test_front_2.mp4')   # 영상 파일 경로. 숫자(0,1..)면 웹캠, rtsp://... 면 IP카메라
YOLO_PATH = _env('GATE_YOLO', r'C:\plate_dataset\runs\detect\runs\lp_detect\yolo26n_v2\weights\best.pt')
OCR_PATH = _env('GATE_OCR', r'C:\plate_dataset\deep-text-recognition-benchmark\saved_models\TPS-ResNet-BiLSTM-CTC-v6\best_accuracy.pth')
CHARACTER = "0123456789가나다라마바사아자거너더러머버서어저고노도로모보소오조구누두루무부수우주하허호배충육기북합남울강원경산전대국해광천종세인제"  # OCR 학습 때와 동일해야 함

OUTPUT_CSV = _env('GATE_CSV', r'C:\plate_dataset\gate_results.csv')
SAVE_VIDEO = ''            # 결과 영상 저장 경로 (예: r'C:\plate_dataset\gate_demo_out.mp4'). 빈 문자열이면 저장 안 함
SHOW = True                # 화면에 표시
DISPLAY_MAX_W = 1280      # 화면 표시 창의 최대 가로(px). 이보다 큰 영상은 비율을 유지해 줄여서 보여줌 (저장 영상/처리에는 영향 없음)
DISPLAY_MAX_H = 720        # 화면 표시 창의 최대 세로(px). 모니터에 안 맞으면 값을 더 줄일 것
REALTIME_PACING = True     # True면 원본 FPS(실제 시간)에 맞춰 재생 (실시간 시연). False면 가능한 한 빨리 처리
REALTIME_DROP = True       # True면 처리가 늦을 때 프레임을 건너뛰어 '지금 시각'의 프레임을 처리 -> 영상이 느려 보이거나 지연이 쌓이지 않음 (실제 카메라와 같은 동작)
FONT_PATH = _env('GATE_FONT', r'C:\Windows\Fonts\malgun.ttf')

YOLO_IMGSZ = 960
YOLO_CONF = 0.5
YOLO_HALF = False          # True면 FP16 (RTX 3060에서 YOLO가 대략 1.3~2배 빨라짐. 켠 뒤 확정 결과가 그대로인지 확인할 것)
YOLO_CROP_TO_ROI = True    # True면 ROI(+여백) 부분만 잘라서 YOLO에 넣음 -> YOLO 시간이 크게 줄어듦. ROI 밖 차량은 어차피 읽지 않으므로 영향 없음
DET_MARGIN = 0.10          # ROI 둘레에 더 포함할 여백(화면 크기 비율). 번호판이 ROI 경계에서 잘리지 않고 추적이 이어지게 함
VIDEO_STRIDE = 1           # 2면 영상 프레임을 하나 건너뛰며 처리 (처리량 절반). 60FPS 영상이면 2~3으로 해도 충분. 읽기 간격/ROI 판정 등은 원본 프레임 수 기준 그대로

ROI_NORM = (0.25, 0.72, 0.85, 0.98)   # 읽기 구역(ROI) = (왼쪽, 위, 오른쪽, 아래), 화면 크기에 대한 비율 0~1.
                           # 번호판 박스 중심이 이 구역에 들어온 순간부터 OCR/시간 측정. 번호판이 충분히 커 보이는(대략 90px 이상) 아래쪽 구역으로 잡을 것
ROI_SELECT = False         # True면 실행할 때 첫 프레임에서 마우스로 ROI를 직접 그림 (드래그 후 Enter/Space, 취소는 c)
SAVE_CROPS_DIR = _env('GATE_CROPS_DIR', r'C:\plate_dataset\gate_crops')   # 차량별 기록용 번호판 크롭 저장 폴더 (신뢰도가 가장 높았던 크롭). 빈 문자열이면 저장 안 함
SAVE_READ_CROPS = True     # True면 확정 못 한 차량(TIMEOUT 등)의 읽기에 쓴 크롭을 전부 SAVE_CROPS_DIR\reads 에 저장 (eval_enhance.py로 화질 개선 효과를 따로 실험할 때 사용)
DEADLINE_MS = 1500         # '읽을 수 있는 크기가 된 순간'부터 확정까지 허용 시간 (멀리서 다가오는 영상은 넉넉히)
MIN_PLATE_W = 85           # ROI 안에서도 이 너비(px) 이상일 때만 OCR (ROI가 위치로 먼 차를 걸러주므로 안전장치 용도)
                           # (이전 영상: 확정된 건 대부분 96px 이상, 56~86px에서는 읽기가 흔들림). 영상에 맞게 조정
MIN_CROP_BRIGHTNESS = 0    # 크롭 평균 밝기(0~255)가 이 값 미만인 프레임은 OCR을 건너뜀. 0이면 사용 안 함
MIN_CROP_CONTRAST = 0      # 크롭 밝기의 표준편차가 이 값 미만인 프레임은 OCR을 건너뜀. 0이면 사용 안 함
                           # -> 먼저 0으로 두고 실행해서 콘솔 요약의 밝기/대비 분포를 본 뒤 정할 것
MAX_TRIES = 30             # 차량(ID)당 OCR 최대 횟수
READ_INTERVAL_FRAMES = 2   # 같은 차량은 이 프레임 간격으로만 OCR (같은 흐린 장면을 연달아 읽어 시도 횟수를 낭비하지 않게)
CONFIRM_READ_CONF = 0.5    # 확정에 쓰는 '믿을 만한 읽기'의 최소 신뢰도. 이 이상인 읽기만 일치 횟수로 센다
STRICT_CONFIRM = True      # True면 1등 번호의 '믿을 만한 읽기' 횟수가 CONFIRM_AGREE 이상이고, 2등 번호보다 CONFIRM_MARGIN번 이상 많을 때만 확정
CONFIRM_MARGIN = 4         # 1등과 2등의 '믿을 만한 읽기' 횟수 차이. 클수록 확실해지지만 확정이 늦어짐 (글자가 안 보이는 번호판은 끝내 확정 안 됨 = 의도)
AMBIG_RATIO = 0.35         # 1등 번호와 1~2글자만 다른 번호(예: 37루3763 / 37무3763)의 가중치가 1등의 이 비율 이상이면 '헷갈리는 번호판'으로 보고 확정하지 않음
SLOW_CONFIRM = True        # True면 위 규칙으로 확정이 안 될 때, 읽기가 충분히 쌓인 뒤 '글자 위치별 가중 투표'로 확정을 한 번 더 시도
SLOW_MIN_READS = 12        # 이만큼 읽은 뒤에야 위치별 투표 확정을 허용 (너무 일찍 확정해 틀리는 것을 방지)
SLOW_POS_RATIO = 2.5       # 각 글자 위치에서 1등 글자의 가중치가 2등의 몇 배 이상이어야 하는지. 못 넘으면(예: 모/보/로가 비슷하게 갈림) 확정 안 함
SLOW_LONGER_RATIO = 0.5    # 글자 수가 더 긴 읽기(예: 822가5340)의 가중치가 짧은 읽기(82가5340)의 이 비율 이상이면 긴 쪽을 택함 (OCR은 글자를 빠뜨리는 오류가 더 흔함)
ROI_MIN_FRAMES = 8         # ROI 안에 이 프레임 수 미만으로 머문 차량이 확정에 실패하면 실패가 아니라 OUT_OF_ROI(무시)로 기록
ROI_LEAVE_FRAMES = 6       # 박스가 이 프레임 수 연속으로 ROI 밖에 있어야 '벗어남'으로 처리 (경계에서 박스가 흔들려 바로 끝나는 것을 방지)
MAX_OCR_PER_FRAME = 2      # 한 프레임에서 OCR을 돌릴 최대 번호판 수 (큰 번호판 우선). 지연 폭증 방지
CONFIRM_AGREE = 2          # 같은 결과가 이 횟수 이상 나와야 확정
CONFIRM_MIN_CONF = 0.6     # 확정에 필요한 최소 신뢰도 (학습 로그의 신뢰도와 계산 방식이 다름 -> 실제 영상 보고 조정)
LOST_FRAMES = 12           # 이 프레임 수 넘게 안 보이면 사라진 것으로 처리
DUP_COOLDOWN_S = 5.0       # 같은 번호가 이 시간 안에 다시 확정되면 중복(DUPLICATE) 처리 (ID가 바뀌어도 게이트가 두 번 열리지 않게)
# ==============================================================================

# 번호판 형식: [지역명] + 앞자리 2~3자리 숫자 + 용도 글자 + 뒷자리 4자리 숫자
REGIONS = "서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주"
LETTERS = "가나다라마바사아자거너더러머버서어저고노도로모보소오조구누두루무부수우주하허호배"
PLATE_RE = re.compile(rf'^(?:{REGIONS})?\d{{2,3}}[{LETTERS}]\d{{4}}$')


class Opt:
    Transformation = 'TPS'
    FeatureExtraction = 'ResNet'
    SequenceModeling = 'BiLSTM'
    Prediction = 'CTC'
    character = CHARACTER
    imgH = 32
    imgW = 100
    batch_max_length = 25
    num_fiducial = 20
    input_channel = 1
    output_channel = 512
    hidden_size = 256
    rgb = False
    num_gpu = 1


opt = Opt()
use_cuda = torch.cuda.is_available()
device = torch.device('cuda' if use_cuda else 'cpu')
yolo_device = 0 if use_cuda else 'cpu'
half_flag = bool(YOLO_HALF and use_cuda)

converter = CTCLabelConverter(opt.character)
opt.num_class = len(converter.character)
ocr_model = Model(opt)
ocr_model = torch.nn.DataParallel(ocr_model).to(device)
ocr_model.load_state_dict(torch.load(OCR_PATH, map_location=device))
ocr_model.eval()
torch.backends.cudnn.benchmark = True

try:
    FONT = ImageFont.truetype(FONT_PATH, 24)
except OSError:
    print(f"[경고] 한글 폰트를 찾을 수 없어 기본 폰트를 씁니다 (화면의 한글이 깨져 보일 수 있음): {FONT_PATH}")
    FONT = ImageFont.load_default()


@torch.no_grad()
def run_ocr(crop_bgr):
    """크롭된 번호판(BGR) -> (텍스트, 신뢰도). 학습 때와 같은 전처리(그레이 + BICUBIC 리사이즈 + 정규화)."""
    gray = cv2.cvtColor(crop_bgr, cv2.COLOR_BGR2GRAY)
    pil = Image.fromarray(gray).resize((opt.imgW, opt.imgH), Image.BICUBIC)
    arr = (np.asarray(pil, dtype=np.float32) / 255.0 - 0.5) / 0.5
    x = torch.from_numpy(arr).unsqueeze(0).unsqueeze(0).to(device)

    text_for_pred = torch.zeros(1, opt.batch_max_length + 1, dtype=torch.long, device=device)
    preds = ocr_model(x, text_for_pred)
    probs = F.softmax(preds, dim=2)
    max_prob, idx = probs.max(2)
    text = converter.decode(idx, torch.IntTensor([preds.size(1)]))[0]

    # 신뢰도: 실제로 출력된(블랭크/중복 제외) 글자들의 확률을 곱함
    conf, prev, emitted = 1.0, 0, False
    for i, p in zip(idx[0].tolist(), max_prob[0].tolist()):
        if i != 0 and i != prev:
            conf *= p
            emitted = True
        prev = i
    return text, (conf if emitted else 0.0)


def crop_stats(crop_bgr):
    """크롭의 평균 밝기(0~255)와 대비(밝기의 표준편차). 어두운 프레임을 거르는 데 쓴다."""
    c = crop_bgr.astype(np.float32)
    gray = c[..., 2] * 0.299 + c[..., 1] * 0.587 + c[..., 0] * 0.114
    return float(gray.mean()), float(gray.std())


def too_dark(mean_v, std_v):
    return (MIN_CROP_BRIGHTNESS > 0 and mean_v < MIN_CROP_BRIGHTNESS) or \
           (MIN_CROP_CONTRAST > 0 and std_v < MIN_CROP_CONTRAST)


def new_state(frame_idx):
    return dict(seen=frame_idx, ready=None, last=frame_idx, w=0, reads=[], tries=0,
                text='', conf=0.0, done=False, status='',
                stats=[], skipped=0, skip_stats=[], last_read=-10**9, inside=False, out_count=0, in_frames=0, read_crops=[], best_crop=None, best_crop_conf=-1.0, crop_file='')   # stats: OCR을 돌린 크롭의 (밝기, 대비), skip_stats: 건너뛴 크롭


def no_read_status(s):
    """형식에 맞는 읽기 결과가 하나도 없을 때의 상태. 어두워서 건너뛴 프레임만 있었다면 LOW_QUALITY"""
    return 'LOW_QUALITY' if (not s['reads'] and s['skipped'] > 0) else 'NO_VALID_READ'


def update_state(s, text, conf):
    """OCR 결과를 반영하고, 확정 조건을 만족하면 True. 형식에 맞는 결과만 투표에 참여한다."""
    valid = bool(PLATE_RE.match(text))
    s['reads'].append((text, conf, valid))
    s['tries'] += 1

    score, best_conf, count = {}, {}, {}
    for t, c, v in s['reads']:
        if not v:
            continue
        score[t] = score.get(t, 0.0) + c
        best_conf[t] = max(best_conf.get(t, 0.0), c)
        count[t] = count.get(t, 0) + 1

    if score:
        s['text'] = max(score, key=score.get)       # 신뢰도 가중 다수결
        s['conf'] = best_conf[s['text']]
        if not STRICT_CONFIRM:
            return count[s['text']] >= CONFIRM_AGREE and s['conf'] >= CONFIRM_MIN_CONF
        strong = {}
        for t, c, v in s['reads']:
            if v and c >= CONFIRM_READ_CONF:
                strong[t] = strong.get(t, 0) + 1
        lead = max(strong, key=strong.get) if strong else None
        if lead is not None and strong[lead] >= CONFIRM_AGREE and best_conf.get(lead, 0.0) >= CONFIRM_MIN_CONF:
            runner = max((n for t, n in strong.items() if t != lead), default=0)
            ambiguous = any(t != lead and len(t) == len(lead) and sum(a != b for a, b in zip(t, lead)) <= 2
                            and w >= AMBIG_RATIO * score[lead] for t, w in score.items())
            if strong[lead] - runner >= CONFIRM_MARGIN and not ambiguous:   # 비슷하게 읽히는 다른 번호가 있으면 더 기다린다
                s['text'], s['conf'] = lead, best_conf[lead]
                return True
        if SLOW_CONFIRM:
            sc = slow_consensus(s)
            if sc is not None:
                s['text'], s['conf'] = sc
                return True
        return False

    # 형식에 맞는 결과가 아직 없으면 화면 표시용으로 가장 신뢰도 높은 원본 결과만 보관
    t, c, _ = max(s['reads'], key=lambda r: r[1])
    s['text'], s['conf'] = t, c
    return False


def slow_consensus(s):
    """형식에 맞는 읽기들을 글자 위치별로 신뢰도 가중 투표해서 번호를 만든다. 확실하지 않으면 None."""
    if len(s['reads']) < SLOW_MIN_READS:
        return None
    valid = [(t, c) for t, c, v in s['reads'] if v]
    if not valid:
        return None
    wlen = {}
    for t, c in valid:
        wlen[len(t)] = wlen.get(len(t), 0.0) + c
    top_len = max(wlen, key=wlen.get)
    for L in sorted(wlen, reverse=True):                 # 더 긴 쪽이 충분히 받쳐주면 긴 쪽 (글자 누락 오류 보정)
        if L > top_len and wlen[L] >= SLOW_LONGER_RATIO * wlen[top_len]:
            top_len = L
            break
    group = [(t, c) for t, c in valid if len(t) == top_len]
    chars, min_ratio = [], 99.0
    for i in range(top_len):
        w = {}
        for t, c in group:
            w[t[i]] = w.get(t[i], 0.0) + c
        order = sorted(w.items(), key=lambda kv: -kv[1])
        chars.append(order[0][0])
        if len(order) > 1:
            min_ratio = min(min_ratio, order[0][1] / max(order[1][1], 1e-9))
    text = ''.join(chars)
    if not PLATE_RE.match(text) or min_ratio < SLOW_POS_RATIO:
        return None
    strong_same = sum(1 for t, c in group if t == text and c >= CONFIRM_READ_CONF)
    if strong_same < max(CONFIRM_AGREE, 3):
        return None
    return text, max(c for t, c in group if t == text)


EVENT_HOOK = None      # 웹 서버 등이 등록: 차량 1대의 결과가 나올 때마다 EVENT_HOOK(event_dict, best_crop_bgr) 호출


def finish(events, last_confirmed, tid, s, status, frame_idx, fps, proc_ms):
    """차량 1대의 결과를 확정/기록한다."""
    if status != 'CONFIRMED' and s['in_frames'] < ROI_MIN_FRAMES:
        status = 'OUT_OF_ROI'                    # ROI를 스치기만 한 차량 = 읽기 실패로 세지 않음
    lat_ready = (frame_idx - s['ready']) * 1000.0 / fps + proc_ms
    lat_seen = (frame_idx - s['seen']) * 1000.0 / fps + proc_ms

    if status == 'CONFIRMED':
        if lat_ready > DEADLINE_MS:
            status = 'CONFIRMED_LATE'
        prev = last_confirmed.get(s['text'])
        if prev is not None and (frame_idx - prev) / fps < DUP_COOLDOWN_S:
            status = 'DUPLICATE'
        else:
            last_confirmed[s['text']] = frame_idx

    s['done'] = True
    s['status'] = status
    if SAVE_CROPS_DIR and s['best_crop'] is not None:
        try:
            os.makedirs(SAVE_CROPS_DIR, exist_ok=True)
            name = f"id{tid:04d}_{status}_{s['text'] or 'none'}_f{frame_idx}.jpg"
            ok, buf = cv2.imencode('.jpg', s['best_crop'])          # 한글 경로에서도 저장되도록 imencode 사용
            if ok:
                buf.tofile(os.path.join(SAVE_CROPS_DIR, name))
                s['crop_file'] = name
        except Exception as e:
            print(f"  크롭 저장 실패: {e}")
    if SAVE_READ_CROPS and SAVE_CROPS_DIR and status in ('TIMEOUT', 'LOST', 'NO_VALID_READ', 'LOW_QUALITY'):
        try:
            rd = os.path.join(SAVE_CROPS_DIR, 'reads')
            os.makedirs(rd, exist_ok=True)
            for fi, cr in s['read_crops']:
                ok, buf = cv2.imencode('.jpg', cr, [cv2.IMWRITE_JPEG_QUALITY, 98])
                if ok:
                    buf.tofile(os.path.join(rd, f"id{tid:04d}_f{fi:05d}.jpg"))
        except Exception as e:
            print(f"  읽기 크롭 저장 실패: {e}")
    s['read_crops'] = []
    parts = []
    for i, (t, c, v) in enumerate(s['reads']):
        q = f" [밝기{s['stats'][i][0]:.0f}/대비{s['stats'][i][1]:.0f}/폭{s['stats'][i][2]}]" if i < len(s['stats']) else ''
        parts.append(f"{t}:{c:.2f}{'' if v else '(형식X)'}{q}")
    reads = ' | '.join(parts)
    events.append(dict(
        id=tid, plate=s['text'], conf=round(s['conf'], 3), status=status, reads=len(s['reads']),
        box_w=s['w'], seen_frame=s['seen'], ready_frame=s['ready'], end_frame=frame_idx,
        lat_from_ready_ms=round(lat_ready, 1), lat_from_seen_ms=round(lat_seen, 1),
        skipped_low_quality=s['skipped'], crop_file=s['crop_file'], read_log=reads))
    if EVENT_HOOK is not None:
        try:
            EVENT_HOOK(events[-1], s['best_crop'])
        except Exception as e:                       # 웹/DB 쪽 오류가 게이트 처리를 멈추면 안 된다
            print(f"  이벤트 전달 실패: {e}")
    print(f"[{status}] ID {tid}  {s['text'] or '-'}  conf {s['conf']:.2f}  읽기 {len(s['reads'])}회  "
          f"박스 {s['w']}px  지연 {lat_ready:.0f}ms (읽을 수 있는 크기가 된 뒤 기준, 허용 {DEADLINE_MS}ms)")


_patch_cache = {}


def text_patch(label, bg_rgb, fg_rgb=(0, 0, 0)):
    """글씨만 작은 이미지로 그려서 (BGR) 반환. 같은 글씨/색은 캐시. 프레임 전체를 PIL로 변환하는 것보다 훨씬 빠르다."""
    key = (label, bg_rgb, fg_rgb)
    p = _patch_cache.get(key)
    if p is None:
        w = int(FONT.getlength(label)) + 10
        im = Image.new('RGB', (max(w, 4), 28), bg_rgb)
        ImageDraw.Draw(im).text((5, 0), label, font=FONT, fill=fg_rgb)
        p = cv2.cvtColor(np.asarray(im), cv2.COLOR_RGB2BGR)
        if len(_patch_cache) > 500:
            _patch_cache.clear()
        _patch_cache[key] = p
    return p


def paste_patch(img, patch, x, y):
    h, w = patch.shape[:2]
    H_, W_ = img.shape[:2]
    x, y = max(0, min(x, W_ - 1)), max(0, min(y, H_ - 1))
    w2, h2 = min(w, W_ - x), min(h, H_ - y)
    if w2 > 0 and h2 > 0:
        img[y:y + h2, x:x + w2] = patch[:h2, :w2]


def draw_overlay(frame, items, hud):
    """박스는 cv2로, 글씨(한글)는 작은 패치로 그려서 붙인다. items 색은 (R,G,B)."""
    for x1, y1, x2, y2, label, color in items:
        cv2.rectangle(frame, (x1, y1), (x2, y2), (color[2], color[1], color[0]), 3)
        paste_patch(frame, text_patch(label, color), x1, max(0, y1 - 30))
    for i, line in enumerate(hud):
        paste_patch(frame, text_patch(line, (30, 30, 30), (255, 230, 0)), 10, 10 + 30 * i)
    return frame


def open_source(path):
    """영상 파일 / 웹캠 번호 / RTSP 주소를 모두 열 수 있게 한다."""
    return cv2.VideoCapture(int(path)) if str(path).isdigit() else cv2.VideoCapture(path)


def main(on_frame=None, on_stats=None, should_stop=None):
    """on_frame(view_bgr): 주석이 그려진 화면을 받는 콜백 (웹 송출용). on_stats(dict): 약 1초마다 처리 현황.
    should_stop(): True를 돌려주면 종료. 셋 다 없으면 예전처럼 창을 띄워 실행한다."""
    cap = open_source(VIDEO_PATH)
    if not cap.isOpened():
        print(f"영상을 열 수 없습니다: {VIDEO_PATH}")
        return
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    budget_ms = 1000.0 / fps * VIDEO_STRIDE      # 처리한 프레임 1장당 허용 시간
    W = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    H = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    print(f"장치: {torch.cuda.get_device_name(0) if use_cuda else 'CPU (GPU를 못 찾음!)'}")
    print(f"영상 {W}x{H}, {fps:.1f}FPS (프레임당 허용 {budget_ms:.1f}ms), 확정 허용 {DEADLINE_MS}ms")

    ok, first = cap.read()
    if not ok:
        print("첫 프레임을 읽지 못했습니다.")
        return

    # 읽기 구역(ROI) 설정
    if ROI_SELECT:
        sc = min(1.0, DISPLAY_MAX_W / first.shape[1], DISPLAY_MAX_H / first.shape[0])
        pick = cv2.resize(first, None, fx=sc, fy=sc, interpolation=cv2.INTER_AREA) if sc < 1.0 else first
        r = cv2.selectROI('ROI 선택 (드래그 후 Enter)', pick, showCrosshair=False)
        cv2.destroyWindow('ROI 선택 (드래그 후 Enter)')
        roi = (int(r[0] / sc), int(r[1] / sc), int((r[0] + r[2]) / sc), int((r[1] + r[3]) / sc)) if r[2] > 0 and r[3] > 0 else None
    else:
        roi = None
    if roi is None:
        fh, fw = first.shape[:2]
        roi = (int(ROI_NORM[0] * fw), int(ROI_NORM[1] * fh), int(ROI_NORM[2] * fw), int(ROI_NORM[3] * fh))
    print(f"ROI(읽기 구역): x {roi[0]}~{roi[2]}, y {roi[1]}~{roi[3]}  (코드에 고정하려면 ROI_NORM에 비율로 입력)")

    # YOLO 입력 영역 (ROI + 여백). 화면 전체와 같은 배율이 되도록 imgsz도 같이 줄인다
    if YOLO_CROP_TO_ROI:
        mx, my = int(DET_MARGIN * W), int(DET_MARGIN * H)
        det = (max(0, roi[0] - mx), max(0, roi[1] - my), min(W, roi[2] + mx), min(H, roi[3] + my))
        imgsz_eff = max(32, int(round(YOLO_IMGSZ * (det[2] - det[0]) / W / 32.0)) * 32)
    else:
        det, imgsz_eff = (0, 0, W, H), YOLO_IMGSZ
    print(f"YOLO 입력 영역 x {det[0]}~{det[2]}, y {det[1]}~{det[3]}, imgsz {imgsz_eff}")

    # 워밍업 (첫 호출의 초기화 비용이 측정에 섞이지 않게). 추적 상태가 남지 않도록 별도 인스턴스로 수행
    warm = YOLO(YOLO_PATH)
    for _ in range(4):
        warm.predict(first[det[1]:det[3], det[0]:det[2]], conf=YOLO_CONF, imgsz=imgsz_eff, verbose=False,
                     device=yolo_device, half=half_flag)
        run_ocr(np.zeros((40, 120, 3), dtype=np.uint8))
    if use_cuda:
        torch.cuda.synchronize()
    del warm

    yolo = YOLO(YOLO_PATH)
    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)

    writer = None
    if SAVE_VIDEO:
        writer = cv2.VideoWriter(SAVE_VIDEO, cv2.VideoWriter_fourcc(*'mp4v'), fps, (W, H))

    states, last_confirmed, events, proc_times = {}, {}, [], []
    last_msg = '최근 확정: -'
    frame_idx = -1
    t_start, dropped = None, 0

    tm = dict(yolo=0.0, ocr=0.0, draw=0.0, n=0, pre=0.0, inf=0.0, post=0.0)       # 단계별 소요 시간 합계(ms)
    while True:
        if frame_idx >= 0:
            for _ in range(VIDEO_STRIDE - 1):             # 프레임 건너뛰기 (디코딩만 하고 처리는 안 함)
                if not cap.grab():
                    break
                frame_idx += 1
        if REALTIME_PACING and REALTIME_DROP and t_start is not None:
            # 처리 때문에 실제 시간보다 뒤처졌으면, 못 따라간 프레임은 버리고 '지금'에 해당하는 프레임으로 건너뛴다
            target = int((time.perf_counter() - t_start) * fps)
            while frame_idx + 1 < target:
                if not cap.grab():
                    break
                frame_idx += 1
                dropped += 1
        ret, frame = cap.read()
        if not ret:
            break
        frame_idx += 1
        t0 = time.perf_counter()
        if t_start is None:
            t_start = t0

        res = yolo.track(frame[det[1]:det[3], det[0]:det[2]], persist=True, tracker='bytetrack.yaml', conf=YOLO_CONF,
                         imgsz=imgsz_eff, verbose=False, device=yolo_device, half=half_flag)
        boxes = res[0].boxes
        xyxy = boxes.xyxy.cpu().numpy().astype(int) if len(boxes) else np.zeros((0, 4), dtype=int)
        if len(xyxy):
            xyxy = xyxy + np.array([det[0], det[1], det[0], det[1]])      # 잘라낸 영역 좌표 -> 원본 프레임 좌표
        ids = boxes.id.int().tolist() if boxes.id is not None else [None] * len(xyxy)
        tm['yolo'] += (time.perf_counter() - t0) * 1000.0
        tm['n'] += 1
        sp = getattr(res[0], 'speed', None) or {}
        tm['pre'] += sp.get('preprocess', 0.0)
        tm['inf'] += sp.get('inference', 0.0)
        tm['post'] += sp.get('postprocess', 0.0)

        # 1) 상태 갱신, OCR 대상 수집
        candidates, boxes_now = [], []
        for tid, (x1, y1, x2, y2) in zip(ids, xyxy):
            if tid is None:
                continue
            s = states.setdefault(tid, new_state(frame_idx))
            if s['done'] and s['status'] in ('NEVER_READABLE', 'OUT_OF_ROI', 'LOST') \
                    and (frame_idx - s['last']) > LOST_FRAMES:
                # 추적기가 가려졌던 차량에 같은 ID를 다시 붙인 경우: 예전의 '실패' 기록을 이어받으면 ROI 안에 들어와도 영영 못 읽는다 -> 새로 시작
                s = states[tid] = new_state(frame_idx)
            s['last'] = frame_idx
            s['w'] = int(x2 - x1)
            cx, cy = (x1 + x2) / 2.0, (y1 + y2) / 2.0
            s['inside'] = roi[0] <= cx <= roi[2] and roi[1] <= cy <= roi[3]
            s['out_count'] = 0 if s['inside'] else s['out_count'] + 1
            s['in_frames'] += 1 if s['inside'] else 0
            if s['ready'] is None and s['inside'] and s['w'] >= MIN_PLATE_W:
                s['ready'] = frame_idx                       # 읽을 수 있는 크기가 된 순간 = 시간 측정 시작
            boxes_now.append((tid, x1, y1, x2, y2))
            if (s['ready'] is not None and s['inside'] and not s['done'] and s['tries'] < MAX_TRIES
                    and frame_idx - s['last_read'] >= READ_INTERVAL_FRAMES):
                candidates.append((s['w'], tid, x1, y1, x2, y2))

        # 2) 큰 번호판부터 OCR (프레임당 최대 MAX_OCR_PER_FRAME개)
        candidates.sort(reverse=True)
        for _, tid, x1, y1, x2, y2 in candidates[:MAX_OCR_PER_FRAME]:
            crop = frame[max(0, y1):y2, max(0, x1):x2]
            if crop.size == 0:
                continue
            s = states[tid]
            s['last_read'] = frame_idx
            mean_v, std_v = crop_stats(crop)
            if too_dark(mean_v, std_v):
                s['skipped'] += 1                      # 어두운 프레임은 OCR을 건너뛰고 다음 프레임을 기다린다 (시도 횟수는 쓰지 않음)
                s['skip_stats'].append((mean_v, std_v, s['w']))
                continue
            t_o = time.perf_counter()
            text, conf = run_ocr(crop)
            tm['ocr'] += (time.perf_counter() - t_o) * 1000.0
            if SAVE_READ_CROPS:
                s['read_crops'].append((frame_idx, crop.copy()))
            if conf > s['best_crop_conf']:
                s['best_crop'], s['best_crop_conf'] = crop.copy(), conf
            s['stats'].append((mean_v, std_v, s['w']))      # (밝기, 대비, 이 읽기 때의 박스 너비)
            if update_state(s, text, conf):
                finish(events, last_confirmed, tid, s, 'CONFIRMED', frame_idx, fps,
                       (time.perf_counter() - t0) * 1000.0)
                if s['status'] in ('CONFIRMED', 'CONFIRMED_LATE'):
                    last_msg = f"최근 확정: {s['text']}"

        # 3) 시간 초과 / 사라짐 처리
        for tid, s in states.items():
            if s['done']:
                continue
            gone = (frame_idx - s['last']) > LOST_FRAMES
            if s['ready'] is None:
                continue      # 아직 ROI에 못 들어온(또는 너무 작은) 차량은 실패가 아니라 그냥 대기. 같은 ID가 다시 나타나도 이어서 읽을 수 있게 완료 처리하지 않음
            waited_ms = (frame_idx - s['ready']) * 1000.0 / fps
            left_roi = (frame_idx - s['last']) == 0 and s['out_count'] >= ROI_LEAVE_FRAMES   # 보이긴 하는데 ROI 밖에 연속으로 머묾
            if waited_ms >= DEADLINE_MS or gone or left_roi:
                has_valid = any(v for _, _, v in s['reads'])
                status = ('TIMEOUT' if (waited_ms >= DEADLINE_MS or left_roi) else 'LOST') if has_valid else no_read_status(s)
                finish(events, last_confirmed, tid, s, status, frame_idx, fps,
                       (time.perf_counter() - t0) * 1000.0)

        proc_ms = (time.perf_counter() - t0) * 1000.0        # 화면 표시 시간은 제외한 순수 처리 시간
        proc_times.append(proc_ms)
        if should_stop is not None and should_stop():
            break
        if on_stats is not None and len(proc_times) % 30 == 0:
            recent = proc_times[-30:]
            on_stats(dict(frame=frame_idx, processed=len(proc_times), dropped=dropped, video_fps=fps,
                          proc_ms=float(np.mean(recent)), budget_ms=budget_ms, size=(W, H)))

        # 4) 표시 / 저장
        if SHOW or writer is not None or on_frame is not None:
            items = []
            for tid, x1, y1, x2, y2 in boxes_now:
                s = states[tid]
                if s['done'] and s['status'] == 'OUT_OF_ROI' or (not s['done'] and not s['inside']):
                    color, label = (150, 150, 150), 'ROI 밖'              # ROI 밖 차량은 실패가 아니라 대기/무시 표시
                elif not s['done'] and s['ready'] is None:
                    color, label = (150, 150, 150), '대기(작음)'
                elif s['done'] and s['status'] in ('CONFIRMED', 'CONFIRMED_LATE', 'DUPLICATE'):
                    color, label = (0, 220, 0), f"{s['text']} ({s['conf']:.2f})"
                elif s['done']:
                    color, label = (255, 70, 70), f"{s['text'] or '읽기 실패'} [{s['status']}]"
                else:
                    color, label = (255, 200, 0), (f"{s['text']} ({s['conf']:.2f})" if s['text'] else '읽는 중')
                items.append((x1, y1, x2, y2, label, color))
            hud = [f"처리 {proc_ms:.0f}ms / 허용 {budget_ms:.0f}ms   프레임 {frame_idx}  (건너뜀 {dropped})", last_msg]
            t_d = time.perf_counter()
            if writer is None:
                # 저장을 안 할 때는 먼저 표시 크기로 줄인 뒤 그려서 (한글 글씨 렌더링 비용이 큰 PIL 작업이) 훨씬 가볍다
                scale = min(1.0, DISPLAY_MAX_W / frame.shape[1], DISPLAY_MAX_H / frame.shape[0])
                small = cv2.resize(frame, None, fx=scale, fy=scale, interpolation=cv2.INTER_LINEAR) if scale < 1.0 else frame.copy()
                items_s = [(int(a * scale), int(b * scale), int(c * scale), int(d * scale), lb, co)
                           for a, b, c, d, lb, co in items]
                cv2.rectangle(small, (int(roi[0] * scale), int(roi[1] * scale)),
                              (int(roi[2] * scale), int(roi[3] * scale)), (0, 255, 255), 2)
                view = draw_overlay(small, items_s, hud)
            else:
                cv2.rectangle(frame, (roi[0], roi[1]), (roi[2], roi[3]), (0, 255, 255), 2)   # ROI 표시
                shown = draw_overlay(frame, items, hud)
                writer.write(shown)
                scale = min(1.0, DISPLAY_MAX_W / shown.shape[1], DISPLAY_MAX_H / shown.shape[0])
                view = cv2.resize(shown, None, fx=scale, fy=scale, interpolation=cv2.INTER_LINEAR) if scale < 1.0 else shown
            tm['draw'] += (time.perf_counter() - t_d) * 1000.0
            if on_frame is not None:
                on_frame(view)
            due = t_start + (frame_idx + VIDEO_STRIDE) / fps                  # 다음 프레임이 나와야 하는 실제 시각
            if SHOW:
                cv2.imshow('gate', view)
                wait = max(1, int((due - time.perf_counter()) * 1000)) if REALTIME_PACING else 1
                key = cv2.waitKey(wait) & 0xFF
                if key in (27, ord('q')):
                    break
            elif REALTIME_PACING:
                time.sleep(max(0.0, due - time.perf_counter()))               # 창이 없을 때(웹 모드)의 실시간 속도 맞추기

    cap.release()
    print(f"건너뛴 프레임 {dropped}장 / 전체 {frame_idx + 1}장 ({dropped / max(frame_idx + 1, 1) * 100:.0f}%) "
          f"-> 실시간 속도 유지를 위해 처리하지 않은 프레임 (REALTIME_DROP)")
    if writer is not None:
        writer.release()
    try:
        cv2.destroyAllWindows()
    except cv2.error:
        pass                                                  # headless OpenCV 에는 창 기능이 없음

    # 영상이 끝났는데 아직 결과가 안 난 차량 정리
    for tid, s in states.items():
        if not s['done'] and s['ready'] is not None:
            has_valid = any(v for _, _, v in s['reads'])
            finish(events, last_confirmed, tid, s, 'LOST' if has_valid else no_read_status(s),
                   frame_idx, fps, 0.0)

    # CSV 저장
    if events:
        with open(OUTPUT_CSV, 'w', newline='', encoding='utf-8-sig') as f:
            w = csv.DictWriter(f, fieldnames=list(events[0].keys()))
            w.writeheader()
            w.writerows(events)

    # 요약
    print("\n" + "=" * 70)
    n = len(proc_times)
    if n:
        pt = np.array(proc_times)
        print(f"처리 프레임 {n}장 | 평균 {pt.mean():.1f}ms, 95% {np.percentile(pt, 95):.1f}ms, 최대 {pt.max():.1f}ms "
              f"| 허용({budget_ms:.1f}ms) 초과 프레임 {float((pt > budget_ms).mean() * 100):.1f}%")
    if tm['n']:
        print(f"단계별 평균(ms/프레임): YOLO+추적 {tm['yolo'] / tm['n']:.1f} | OCR(실행한 프레임 포함 평균) {tm['ocr'] / tm['n']:.1f} "
              f"| 화면 그리기 {tm['draw'] / tm['n']:.1f}  ← 가장 큰 항목부터 줄이세요")
    if tm['n']:
        n_ = tm['n']
        other = tm['yolo'] / n_ - (tm['pre'] + tm['inf'] + tm['post']) / n_
        print(f"YOLO 내부(ms/프레임): 전처리 {tm['pre'] / n_:.1f} | 추론 {tm['inf'] / n_:.1f} | 후처리 {tm['post'] / n_:.1f} "
              f"| 추적(ByteTrack)·결과변환 등 나머지 {other:.1f}")
    counts = {}
    for e in events:
        counts[e['status']] = counts.get(e['status'], 0) + 1
    print("결과 집계:", counts if counts else "번호판 결과 없음")
    ok_q = [x for st in states.values() if st['status'] in ('CONFIRMED', 'CONFIRMED_LATE') for x in st['stats']]
    bad_q = [x for st in states.values() if st['status'] in ('TIMEOUT', 'LOST', 'NO_VALID_READ', 'LOW_QUALITY')
             for x in st['stats'] + st['skip_stats']]
    for name, q in (('확정된 차량', ok_q), ('확정 못 한 차량', bad_q)):
        if q:
            qa = np.array(q)
            print(f"크롭 밝기/대비 [{name}] {len(q)}회 | 밝기 중앙값 {np.median(qa[:, 0]):.0f}, 대비 중앙값 {np.median(qa[:, 1]):.0f}"
                  f" | 대비 10 미만 {float(np.mean(qa[:, 1] < 10) * 100):.0f}%")
    ok_lat = [e['lat_from_ready_ms'] for e in events if e['status'] == 'CONFIRMED']
    if ok_lat:
        within = sum(1 for v in ok_lat if v <= DEADLINE_MS)
        print(f"확정(CONFIRMED) {len(ok_lat)}건 | 지연 평균 {np.mean(ok_lat):.0f}ms, 최대 {np.max(ok_lat):.0f}ms "
              f"| {DEADLINE_MS}ms 이내 {within}/{len(ok_lat)}")
    if events:
        print(f"상세 결과: {OUTPUT_CSV}")
    print("※ 이 스크립트는 읽은 번호가 실제와 맞는지는 검증하지 않습니다. 영상 속 실제 번호와 직접 대조하세요.")


if __name__ == '__main__':
    main()