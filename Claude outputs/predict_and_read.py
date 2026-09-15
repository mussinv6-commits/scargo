# 원본 해상도가 살아있으니, 다시 크롭 방식으로 전환 (전체 이미지 대신 번호판 부분만 확대해서 인식)
# 전처리(흑백+블러)도 뺐음 - 사진이 이미 선명하면 오히려 디테일을 뭉갤 수 있어서
# 사용법: python predict_and_read.py 사진경로.jpg

import re
import sys

import cv2
import easyocr
from ultralytics import YOLO

WEIGHTS_PATH = "runs/detect/runs/plate_detect/weights/best.pt"

PLATE_PATTERNS = [
    re.compile(r"\d{2,3}[가-힣]\d{4}"),
    re.compile(r"[가-힣]{2}\d{2}[가-힣]\d{4}"),
]

CONF_THRESHOLD = 0.4

_reader = None


def get_reader():
    global _reader
    if _reader is None:
        _reader = easyocr.Reader(["ko", "en"], gpu=False)
    return _reader


def extract_plate_candidates(text):
    cleaned = text.replace(" ", "")
    candidates = []
    for pattern in PLATE_PATTERNS:
        candidates.extend(pattern.findall(cleaned))
    return candidates


def main():
    if len(sys.argv) < 2:
        print("사용법: python predict_and_read.py 사진경로.jpg")
        return

    image_path = sys.argv[1]
    image = cv2.imread(image_path)
    if image is None:
        print("이미지를 열 수 없음 (경로 확인)")
        return

    model = YOLO(WEIGHTS_PATH)
    results = model.predict(source=image_path, conf=0.4, save=False)
    reader = get_reader()
    h, w = image.shape[:2]
    found_any = False

    for r in results:
        for box in r.boxes:
            x1, y1, x2, y2 = [int(v) for v in box.xyxy[0].tolist()]
            det_conf = float(box.conf[0])

            # 박스 주변에 여유를 좀 둠 (번호판 테두리가 잘리면 글자도 같이 잘릴 수 있음)
            pad_x = int((x2 - x1) * 0.15) + 5
            pad_y = int((y2 - y1) * 0.15) + 5
            x1p, y1p = max(0, x1 - pad_x), max(0, y1 - pad_y)
            x2p, y2p = min(w, x2 + pad_x), min(h, y2 + pad_y)
            crop = image[y1p:y2p, x1p:x2p]

            print(f"[YOLO 검출 위치] ({x1},{y1}) ~ ({x2},{y2}) / 신뢰도: {det_conf:.2f} / 크롭 크기: {crop.shape[1]}x{crop.shape[0]}")

            # 크롭 자체가 작으면 확대 (이미 큰 경우엔 과하게 키우지 않도록 목표 크기 기준으로 배율 계산)
            target_width = 400
            scale = max(1.0, target_width / crop.shape[1])
            if scale > 1.0:
                crop = cv2.resize(crop, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)

            # 전처리 없이 원본 크롭 그대로 먼저 시도 (선명하면 흑백/블러가 오히려 방해될 수 있음)
            ocr_results = reader.readtext(crop)
            found_any = True

            if not ocr_results:
                print("  -> 글자를 읽지 못함 (판독불가)")
                continue

            plate_candidates = []
            for _, text, conf in ocr_results:
                status = "인식" if conf >= CONF_THRESHOLD else "판독불가"
                print(f"  '{text}' (신뢰도: {conf:.2f}, {status})")
                if status == "인식":
                    plate_candidates.extend(extract_plate_candidates(text))

            plate_candidates = list(dict.fromkeys(plate_candidates))
            print(f"  -> 번호판 후보: {plate_candidates if plate_candidates else '형식에 안 맞음'}")

    if not found_any:
        print("번호판 위치를 찾지 못함")


if __name__ == "__main__":
    main()
