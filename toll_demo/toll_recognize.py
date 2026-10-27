"""
사진 1장 -> 번호판 인식 (YOLO 탐지 + OCR).  gate_pipeline.py 의 모델·경로 설정을 그대로 재사용한다.

* 영상용 'ROI 잘라 읽기/추적/다수결'은 쓰지 않고, 사진 전체에서 번호판을 찾아 1번 읽는다.
* 결과는 성공/실패 + 신뢰도(0~100, gate_logs.plate_confidence 와 같은 단위).
    SUCCESS : 번호판 형식이 맞고 신뢰도가 TOLL_MIN_CONF 이상
    FAILED  : 번호판을 못 찾았거나(NO_PLATE), 형식이 틀리거나(INVALID_FORMAT), 신뢰도가 낮음(LOW_CONFIDENCE)
* YOLO 모델은 .pt 여야 한다 (OpenVINO 폴더는 영상 ROI 크기로 고정 변환돼 있어 사진에 쓸 수 없음). OCR 은 OpenVINO(GATE_OCR_OV)를 써도 된다.

환경변수:  TOLL_MIN_CONF(기본 80) · TOLL_IMGSZ(기본 960) · TOLL_DET_CONF(기본 0.25)  + gate_pipeline 의 GATE_YOLO / GATE_OCR / GATE_DTR_DIR ...
"""
import os
import sys
import time
import threading
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
if os.environ.get('GATE_PIPELINE_DIR'):
    sys.path.insert(0, os.environ['GATE_PIPELINE_DIR'])
sys.path.insert(0, os.getcwd())

MIN_CONF = float(os.environ.get('TOLL_MIN_CONF', '80'))        # 성공으로 보는 최소 신뢰도(%)
IMGSZ = int(os.environ.get('TOLL_IMGSZ', '960'))
DET_CONF = float(os.environ.get('TOLL_DET_CONF', '0.25'))

_lock = threading.Lock()
_state = {}


def load():
    """모델을 한 번만 불러온다. 실패하면 이유를 설명하는 RuntimeError."""
    if _state:
        return _state
    try:
        import gate_pipeline as gp
    except ModuleNotFoundError as e:
        if e.name != 'gate_pipeline':
            raise
        raise RuntimeError('gate_pipeline.py 를 찾지 못했습니다. 같은 폴더/상위 폴더에 두거나 GATE_PIPELINE_DIR 로 폴더를 지정하세요.')
    if os.path.isdir(gp.YOLO_PATH):
        raise RuntimeError(f'사진 인식에는 YOLO .pt 모델이 필요합니다 (지금은 OpenVINO 폴더: {gp.YOLO_PATH}). GATE_YOLO 를 .pt 파일로 지정하세요.')
    from ultralytics import YOLO
    _state.update(gp=gp, yolo=YOLO(gp.YOLO_PATH))
    return _state


def recognize(img_bgr):
    """사진(BGR ndarray) 1장 -> dict(status, reason, plate, confidence, det_conf, box, crop, ms)"""
    t0 = time.perf_counter()
    s = load()
    gp, yolo = s['gp'], s['yolo']
    out = dict(status='FAILED', reason='NO_PLATE', plate='', confidence=0.0, det_conf=0.0, box=None, crop=None, ms=0)
    kw = dict(conf=DET_CONF, imgsz=IMGSZ, device=gp.yolo_device, verbose=False)
    if gp.half_flag:
        kw['half'] = True
    with _lock:                                                   # 모델은 한 번에 하나씩만 돌린다
        res = yolo.predict(img_bgr, **kw)
        boxes = res[0].boxes
        if len(boxes):
            k = int(boxes.conf.cpu().numpy().argmax())             # 가장 확실한 번호판 1개
            x1, y1, x2, y2 = [int(v) for v in boxes.xyxy.cpu().numpy()[k]]
            h, w = img_bgr.shape[:2]
            x1, y1, x2, y2 = max(0, x1), max(0, y1), min(w, x2), min(h, y2)
            crop = img_bgr[y1:y2, x1:x2]
            if crop.size:
                out.update(det_conf=round(float(boxes.conf.cpu().numpy()[k]), 3), box=(x1, y1, x2, y2), crop=crop)
                text, conf = gp.run_ocr(crop)
                out.update(plate=text, confidence=round(float(conf) * 100, 1))
                if not gp.PLATE_RE.match(text):
                    out['reason'] = 'INVALID_FORMAT'
                elif out['confidence'] < MIN_CONF:
                    out['reason'] = 'LOW_CONFIDENCE'
                else:
                    out.update(status='SUCCESS', reason='')
    out['ms'] = int((time.perf_counter() - t0) * 1000)
    return out
