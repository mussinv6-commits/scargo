# YOLO로 번호판 위치를 먼저 찾고, 그 부분만 잘라서 easyocr로 글자를 읽는 스크립트
# 사용법: python predict_and_read.py 사진경로.jpg

import sys

import cv2
import easyocr
from ultralytics import YOLO

WEIGHTS_PATH = "runs/detect/runs/plate_detect/weights/best.pt"

_reader = None  # 매번 새로 불러오면 느리니까 한 번만 로드해서 재사용


def get_reader():
    global _reader
    if _reader is None:
        _reader = easyocr.Reader(["ko", "en"], gpu=False)
    return _reader


def main():
    if len(sys.argv) < 2:
        print("사용법: python predict_and_read.py 사진경로.jpg")
        return

    image_path = sys.argv[1]
    model = YOLO(WEIGHTS_PATH)
    image = cv2.imread(image_path)

    if image is None:
        print("이미지를 열 수 없음 (경로 확인)")
        return

    results = model.predict(source=image_path, conf=0.4, save=False)
    reader = get_reader()
    h, w = image.shape[:2]
    found_any = False

    for r in results:
        for box in r.boxes:
            x1, y1, x2, y2 = [int(v) for v in box.xyxy[0].tolist()]
            det_conf = float(box.conf[0])

            # 박스 딱 맞춰 자르면 글자 끝부분이 잘릴 수 있어서 여유를 좀 둠
            pad = 5
            x1p, y1p = max(0, x1 - pad), max(0, y1 - pad)
            x2p, y2p = min(w, x2 + pad), min(h, y2 + pad)
            crop = image[y1p:y2p, x1p:x2p]

            # 번호판 부분만 잘라내면 원본보다 훨씬 작아서, 확대해줘야 OCR이 글자를 더 잘 읽음
            crop = cv2.resize(crop, None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC)

            ocr_results = reader.readtext(crop)
            found_any = True

            print(f"[번호판 위치] ({x1},{y1}) ~ ({x2},{y2}) / 위치 신뢰도: {det_conf:.2f}")
            if not ocr_results:
                print("  -> 글자를 읽지 못함 (판독불가)")
            for _, text, ocr_conf in ocr_results:
                print(f"  -> 읽은 글자: {text} (글자 신뢰도: {ocr_conf:.2f})")

    if not found_any:
        print("번호판 위치를 찾지 못함")


if __name__ == "__main__":
    main()
