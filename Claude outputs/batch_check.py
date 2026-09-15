# 폴더 안 사진 전부를 크롭 방식으로 한 번에 검사 (predict_and_read.py와 같은 로직)
# 사용법: python batch_check.py train/images

import glob
import os
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


def extract_plate_candidates(text):
    cleaned = text.replace(" ", "")
    candidates = []
    for pattern in PLATE_PATTERNS:
        candidates.extend(pattern.findall(cleaned))
    return candidates


def main():
    folder = sys.argv[1] if len(sys.argv) > 1 else "train/images"
    image_paths = sorted(glob.glob(os.path.join(folder, "*.jpg")))

    if not image_paths:
        print(f"{folder} 안에 jpg 사진이 없음")
        return

    model = YOLO(WEIGHTS_PATH)
    reader = easyocr.Reader(["ko", "en"], gpu=False)

    detected_count = 0
    read_count = 0

    for path in image_paths:
        name = os.path.basename(path)
        image = cv2.imread(path)
        if image is None:
            print(f"[{name}] 이미지를 열 수 없음")
            continue

        h, w = image.shape[:2]
        results = model.predict(source=path, conf=0.4, save=False, verbose=False)
        boxes = results[0].boxes

        if len(boxes) == 0:
            print(f"[{name}] 위치 못 찾음")
            continue

        detected_count += 1
        best_candidates = []
        best_texts = []

        for box in boxes:
            x1, y1, x2, y2 = [int(v) for v in box.xyxy[0].tolist()]
            pad_x = int((x2 - x1) * 0.15) + 5
            pad_y = int((y2 - y1) * 0.15) + 5
            x1p, y1p = max(0, x1 - pad_x), max(0, y1 - pad_y)
            x2p, y2p = min(w, x2 + pad_x), min(h, y2 + pad_y)
            crop = image[y1p:y2p, x1p:x2p]

            target_width = 400
            scale = max(1.0, target_width / crop.shape[1])
            if scale > 1.0:
                crop = cv2.resize(crop, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)

            ocr_results = reader.readtext(crop)
            for _, text, conf in ocr_results:
                best_texts.append(f"{text}({conf:.2f})")
                if conf >= CONF_THRESHOLD:
                    best_candidates.extend(extract_plate_candidates(text))

        best_candidates = list(dict.fromkeys(best_candidates))
        if best_candidates:
            read_count += 1

        raw = ", ".join(best_texts) if best_texts else "인식된 글자 없음"
        plate_status = f"번호판 후보: {best_candidates}" if best_candidates else f"형식 불일치 (원본 인식: {raw})"
        print(f"[{name}] 위치 검출됨 / {plate_status}")

    print("\n===== 전체 결과 =====")
    print(f"총 사진: {len(image_paths)}장")
    print(f"YOLO 위치 검출 성공: {detected_count}장")
    print(f"번호판 형식까지 맞게 인식 성공: {read_count}장")


if __name__ == "__main__":
    main()
