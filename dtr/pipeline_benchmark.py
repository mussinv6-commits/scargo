import sys, time
sys.path.append(r'C:\plate_dataset\deep-text-recognition-benchmark')

import cv2
import numpy as np
import torch
import torch.nn.functional as F
from PIL import Image
from ultralytics import YOLO
from model import Model
from utils import CTCLabelConverter

# ===================== 설정 (경로는 실제 위치로 수정) =====================
VIDEO_PATH = r'C:\plate_dataset\test_video.mp4'
YOLO_PATH  = r'C:\plate_dataset\runs\detect\runs\lp_detect\yolo26n_v2\weights\best.pt'
OCR_PATH   = r'C:\plate_dataset\deep-text-recognition-benchmark\saved_models\TPS-ResNet-BiLSTM-CTC-v6\best_accuracy.pth'
CHARACTER  = "0123456789가나다라마바사아자거너더러머버서어저고노도로모보소오조구누두루무부수우주하허호배충육기북합남울강원경산전대국해광천종세인제"

YOLO_CONF = 0.5
YOLO_HALF = False      # True면 YOLO만 FP16 (정확도 다시 확인 필요)
MAX_FRAMES = 0         # 0이면 영상 전체

MAX_TRIES = 4          # 같은 번호판(ID)당 OCR 최대 횟수
DONE_CONF = 0.8        # 이 신뢰도 이상이면 그 ID는 OCR 중단

CONFIGS = [
    dict(name='A 기준(추적 없음, 960)',        tracker=None,        imgsz=960, skip=1),
    dict(name='B ByteTrack, 960',              tracker='bytetrack', imgsz=960, skip=1),
    dict(name='C 자체 IoU 추적, 960',           tracker='iou',       imgsz=960, skip=1),
    dict(name='D 자체 IoU 추적, 640',           tracker='iou',       imgsz=640, skip=1),
    dict(name='E IoU 추적, 640, 2프레임마다',    tracker='iou',       imgsz=640, skip=2),
]
# =======================================================================


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
print(f"장치: {torch.cuda.get_device_name(0) if use_cuda else 'CPU (GPU를 못 찾음!)'}")

converter = CTCLabelConverter(opt.character)
opt.num_class = len(converter.character)
ocr_model = Model(opt)
ocr_model = torch.nn.DataParallel(ocr_model).to(device)
ocr_model.load_state_dict(torch.load(OCR_PATH, map_location=device))
ocr_model.eval()
torch.backends.cudnn.benchmark = True


def sync():
    if use_cuda:
        torch.cuda.synchronize()


@torch.no_grad()
def run_ocr(crop_bgr):
    gray = cv2.cvtColor(crop_bgr, cv2.COLOR_BGR2GRAY)
    pil = Image.fromarray(gray).resize((opt.imgW, opt.imgH), Image.BICUBIC)
    arr = (np.asarray(pil, dtype=np.float32) / 255.0 - 0.5) / 0.5
    x = torch.from_numpy(arr).unsqueeze(0).unsqueeze(0).to(device)

    text_for_pred = torch.zeros(1, opt.batch_max_length + 1, dtype=torch.long, device=device)
    preds = ocr_model(x, text_for_pred)
    probs = F.softmax(preds, dim=2)
    max_prob, idx = probs.max(2)
    text = converter.decode(idx, torch.IntTensor([preds.size(1)]))[0]

    conf, prev, emitted = 1.0, 0, False
    for i, p in zip(idx[0].tolist(), max_prob[0].tolist()):
        if i != 0 and i != prev:
            conf *= p
            emitted = True
        prev = i
    return text, (conf if emitted else 0.0)


def iou(a, b):
    ix1, iy1 = max(a[0], b[0]), max(a[1], b[1])
    ix2, iy2 = min(a[2], b[2]), min(a[3], b[3])
    inter = max(0, ix2 - ix1) * max(0, iy2 - iy1)
    ua = (a[2]-a[0])*(a[3]-a[1]) + (b[2]-b[0])*(b[3]-b[1]) - inter
    return inter / ua if ua > 0 else 0.0


class SimpleTracker:
    """YOLO 박스끼리 IoU로 이어붙이는 아주 가벼운 추적기 (게이트처럼 차량이 적은 환경용)"""
    def __init__(self, iou_thr=0.3, max_missed=10):
        self.iou_thr, self.max_missed = iou_thr, max_missed
        self.tracks, self.next_id = {}, 1

    def update(self, boxes):
        free = set(self.tracks.keys())
        ids = []
        for b in boxes:
            best_id, best = None, 0.0
            for tid in free:
                v = iou(b, self.tracks[tid]['box'])
                if v > best:
                    best_id, best = tid, v
            if best_id is not None and best >= self.iou_thr:
                free.discard(best_id)
                self.tracks[best_id]['box'] = b
                self.tracks[best_id]['missed'] = 0
                ids.append(best_id)
            else:
                tid = self.next_id
                self.next_id += 1
                self.tracks[tid] = dict(box=b, missed=0, text='', conf=0.0, tries=0)
                ids.append(tid)
        for tid in free:
            self.tracks[tid]['missed'] += 1
        self.tracks = {k: v for k, v in self.tracks.items() if v['missed'] <= self.max_missed}
        return ids


def yolo_kwargs(cfg):
    return dict(conf=YOLO_CONF, imgsz=cfg['imgsz'], verbose=False,
                device=yolo_device, half=half_flag)


def warmup(yolo, frame, cfg):
    dummy_crop = np.zeros((40, 120, 3), dtype=np.uint8)
    for _ in range(4):
        if cfg['tracker'] == 'bytetrack':
            yolo.track(frame, persist=False, tracker='bytetrack.yaml', **yolo_kwargs(cfg))
        else:
            yolo.predict(frame, **yolo_kwargs(cfg))
        run_ocr(dummy_crop)
    sync()
    yolo.predictor = None   # 워밍업 때 생긴 추적 상태 초기화

MIN_PLATE_W = 0   # 0이면 크기 제한 없음. 아래 로그에 찍히는 박스 너비를 보고 조정

def update_track(s, text, conf):
    reads = s.setdefault('reads', [])
    reads.append((text, conf))
    s['tries'] = len(reads)
    score = {}
    for t, c in reads:
        score[t] = score.get(t, 0.0) + c
    s['text'] = max(score, key=score.get)                 # 신뢰도 가중 다수결
    s['conf'] = max(c for t, c in reads if t == s['text'])
    same = [c for t, c in reads if t == s['text']]
    if len(same) >= 2 and max(same) >= 0.6:               # 같은 결과 2번이면 확정
        s['final'] = True


def benchmark(cfg):
    yolo = YOLO(YOLO_PATH)
    cap = cv2.VideoCapture(VIDEO_PATH)
    if not cap.isOpened():
        print(f"영상을 열 수 없습니다: {VIDEO_PATH}")
        return None

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    skip = cfg['skip']
    budget = 1000.0 / fps * skip           # 처리하는 프레임 1장당 허용 시간
    W = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)); H = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    ok, first = cap.read()
    if not ok:
        return None
    warmup(yolo, first, cfg)
    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)

    tracker = SimpleTracker() if cfg['tracker'] == 'iou' else None
    bt_state = {}
    lat, t_yolo, t_ocr = [], [], []
    sp = np.zeros(3)
    n_src = n_proc = n_ocr = n_det = 0
    wall0 = time.perf_counter()

    while True:
        if skip > 1 and n_src % skip != 0:
            if not cap.grab():
                break
            n_src += 1
            continue

        t0 = time.perf_counter()
        ret, frame = cap.read()
        if not ret:
            break
        n_src += 1

        # ---- YOLO ----
        if cfg['tracker'] == 'bytetrack':
            res = yolo.track(frame, persist=True, tracker='bytetrack.yaml', **yolo_kwargs(cfg))
        else:
            res = yolo.predict(frame, **yolo_kwargs(cfg))
        sync()
        t1 = time.perf_counter()
        sp += [res[0].speed['preprocess'], res[0].speed['inference'], res[0].speed['postprocess']]

        # ---- 추적 + OCR ----
        boxes = res[0].boxes
        xyxy = boxes.xyxy.cpu().numpy().astype(int) if len(boxes) else np.zeros((0, 4), int)
        if len(boxes):
            n_det += 1

        if cfg['tracker'] == 'bytetrack':
            ids = boxes.id.int().tolist() if boxes.id is not None else [None] * len(xyxy)
            state = bt_state
        elif cfg['tracker'] == 'iou':
            ids = tracker.update([tuple(b) for b in xyxy])
            state = tracker.tracks
        else:
            ids = [None] * len(xyxy)
            state = None

        for tid, (x1, y1, x2, y2) in zip(ids, xyxy):
            s = None
            if tid is not None:
                s = state.setdefault(tid, dict(text='', conf=0.0, tries=0))
                if s.get('final') or s['tries'] >= MAX_TRIES:
                    continue
                if (x2 - x1) < MIN_PLATE_W:
                    continue
            crop = frame[max(0, y1):y2, max(0, x1):x2]
            if crop.size == 0:
                continue
            t, c = run_ocr(crop)
            n_ocr += 1
            if s is not None:
                update_track(s, t, c)
                if s.get('final') or s['tries'] >= MAX_TRIES:
                    print(f"  프레임 {n_src} ID {tid}: {s['text']} "
                        f"(conf {s['conf']:.2f}, 읽은 횟수 {s['tries']}, 박스 너비 {x2 - x1}px)")
        sync()
        t2 = time.perf_counter()

        t_yolo.append(t1 - t0); t_ocr.append(t2 - t1); lat.append(t2 - t0)
        n_proc += 1
        if MAX_FRAMES and n_src >= MAX_FRAMES:
            break

    wall = time.perf_counter() - wall0
    cap.release()
    if n_proc == 0:
        return None

    lat_ms = np.array(lat) * 1000
    print(f"  최대 지연 프레임 번호: {int(np.argmax(lat_ms))}")
    return dict(
        name=cfg['name'], res=f"{W}x{H}", fps=fps, budget=budget, n_src=n_src, n_proc=n_proc,
        yolo=np.mean(t_yolo) * 1000, ocr=np.mean(t_ocr) * 1000, total=lat_ms.mean(),
        pre=sp[0] / n_proc, inf=sp[1] / n_proc, post=sp[2] / n_proc,
        p95=np.percentile(lat_ms, 95), max=lat_ms.max(),
        over=float((lat_ms > budget).mean() * 100),
        eff_fps=n_src / wall, ocr_calls=n_ocr, det_ratio=n_det / n_proc * 100,
    )


if __name__ == '__main__':
    results = []
    for cfg in CONFIGS:
        print(f"\n===== {cfg['name']} 측정 중... =====")
        r = benchmark(cfg)
        if r:
            results.append(r)

    print("\n" + "=" * 70)
    for r in results:
        print(f"[{r['name']}]  영상 {r['res']}, 원본 {r['fps']:.1f}FPS")
        print(f"  처리 {r['n_proc']}프레임 (영상 {r['n_src']}프레임 소비), 처리 1장당 허용 {r['budget']:.1f}ms")
        print(f"  YOLO {r['yolo']:.1f}ms (전처리 {r['pre']:.1f} / 추론 {r['inf']:.1f} / 후처리 {r['post']:.1f})"
              f" | 추적+OCR {r['ocr']:.1f}ms | 합계 {r['total']:.1f}ms")
        print(f"  95% 지연 {r['p95']:.1f}ms, 최대 {r['max']:.1f}ms, 허용 초과 {r['over']:.1f}%")
        print(f"  영상 소비 속도 {r['eff_fps']:.1f}FPS ({'실시간 가능' if r['eff_fps'] >= r['fps'] else '원본보다 느림'})")
        print(f"  OCR 호출 {r['ocr_calls']}회 | 번호판이 탐지된 프레임 {r['det_ratio']:.1f}%")
        print("-" * 70)