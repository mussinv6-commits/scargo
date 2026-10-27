import sys
sys.path.append(r'C:\plate_dataset\deep-text-recognition-benchmark')  # model.py, utils.py 위치

import time
import cv2
import torch
import torch.nn.functional as F
import numpy as np
from ultralytics import YOLO

from model import Model
from utils import CTCLabelConverter

# ===== 설정값 (opt.txt 기준 정확히 일치) =====
class Opt:
    Transformation = 'TPS'
    FeatureExtraction = 'ResNet'
    SequenceModeling = 'BiLSTM'
    Prediction = 'CTC'
    character = '0123456789가나다라마바사아자거너더러머버서어저고노도로모보소오조구누두루무부수우주하허호배충육기북합남울강원경산전대국해광천종세인제'
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
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

# ===== OCR 모델 로드 =====
converter = CTCLabelConverter(opt.character)
opt.num_class = len(converter.character)

ocr_model = Model(opt)
ocr_model = torch.nn.DataParallel(ocr_model).to(device)
ocr_model.load_state_dict(torch.load(
    r'C:\plate_dataset\deep-text-recognition-benchmark\saved_models\TPS-ResNet-BiLSTM-CTC-v6\best_accuracy.pth',
    map_location=device
))
ocr_model.eval()

# ===== YOLO 모델 로드 =====
yolo_model = YOLO(r'C:\plate_dataset\runs\detect\runs\lp_detect\yolo26n_v2\weights\best.pt')

def run_ocr(cropped_bgr):
    img = cv2.cvtColor(cropped_bgr, cv2.COLOR_BGR2GRAY)
    img = cv2.resize(img, (opt.imgW, opt.imgH))
    img = img.astype(np.float32) / 255.0
    img = (img - 0.5) / 0.5
    img_tensor = torch.from_numpy(img).unsqueeze(0).unsqueeze(0).to(device)

    batch_size = 1
    text_for_pred = torch.LongTensor(batch_size, opt.batch_max_length + 1).fill_(0).to(device)

    with torch.no_grad():
        preds = ocr_model(img_tensor, text_for_pred)
        preds_size = torch.IntTensor([preds.size(1)] * batch_size)
        _, preds_index = preds.max(2)
        preds_str = converter.decode(preds_index, preds_size)

        preds_prob = F.softmax(preds, dim=2)
        preds_max_prob, _ = preds_prob.max(dim=2)
        confidence = preds_max_prob[0].cumprod(dim=0)[-1].item()

    return preds_str[0], confidence




# ===== 동영상 파일 처리 =====
VIDEO_PATH = r'C:\plate_dataset\test_back.mp4'   # ← 테스트할 영상 경로로 수정
OUTPUT_PATH = r'C:\plate_dataset\test_back_result.mp4'  # 결과 저장 경로 (선택)
SAVE_OUTPUT = True   # 결과를 영상 파일로 저장할지 여부

cap = cv2.VideoCapture(VIDEO_PATH)

if not cap.isOpened():
    print(f"영상을 열 수 없습니다: {VIDEO_PATH}")
    exit()

fps = cap.get(cv2.CAP_PROP_FPS)
width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))

print(f"영상 정보: {width}x{height}, {fps:.1f}FPS, 총 {total_frames}프레임")

writer = None
if SAVE_OUTPUT:
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    writer = cv2.VideoWriter(OUTPUT_PATH, fourcc, fps, (width, height))

frame_idx = 0
print("처리 시작. 화면 창에서 'q' 키로 중단 가능합니다.")





while True:
    ret, frame = cap.read()
    if not ret:
        break

    frame_idx += 1

    results = yolo_model(frame, conf=0.4, verbose=False)

    for box in results[0].boxes:
        x1, y1, x2, y2 = map(int, box.xyxy[0])
        yolo_conf = box.conf.item()

        x1, y1 = max(0, x1), max(0, y1)
        x2, y2 = min(frame.shape[1], x2), min(frame.shape[0], y2)
        cropped = frame[y1:y2, x1:x2]

        if cropped.size == 0:
            continue

        text, ocr_conf = run_ocr(cropped)

        cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 255, 0), 2)
        label = f"{text} (Y:{yolo_conf:.2f} O:{ocr_conf:.2f})"
        cv2.putText(frame, label, (x1, max(y1 - 10, 0)),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)

    cv2.imshow('Video Plate Recognition', frame)

    if writer:
        writer.write(frame)

    if frame_idx % 30 == 0:
        print(f"진행: {frame_idx}/{total_frames} 프레임")

    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
if writer:
    writer.release()
cv2.destroyAllWindows()

print(f"\n완료! 총 {frame_idx}프레임 처리")
if SAVE_OUTPUT:
    print(f"결과 영상 저장됨: {OUTPUT_PATH}")