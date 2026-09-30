# -*- coding: utf-8 -*-
"""
prepare_ocr_training_data.py
====================================================
번호판 전용 OCR(글자 인식) 모델을 새로 학습시키기 위한 1단계: 학습용 데이터셋 준비.

배경
====================================================
화물차_merged 폴더의 이미지 1,961장은 파일명 자체가 번호판 정답 텍스트로 되어 있고
(예: "006너6045.jpeg"), 같은 이름의 YOLO 라벨(.txt, 번호판 위치 박스)도 labels 폴더에
있습니다. 즉 이미 "번호판 crop 이미지 + 정답 텍스트" 학습 데이터를 만들 수 있는 재료가
갖춰져 있다는 뜻입니다 (지금까지는 검출 모델 학습에만 썼지, 글자 인식 모델 학습에는
써본 적이 없었음).

이 스크립트가 하는 일
====================================================
1) images/<파일명>.jpeg + labels/<파일명>.txt (YOLO 박스) 를 읽어서
2) plate_detector_gui.py의 _padded_crop과 동일한 규칙(12% 패딩)으로 번호판 영역만 크롭
3) 파일명에서 정답 텍스트를 추출 (확장자 제거, 혹시 파일명이 중복으로 이어붙은 경우
   -파일명이경북98사1394경북98사1394.jpeg 같은 예외 2건- 절반으로 잘라 보정)
4) 이미지 가로세로 비율로 1줄/2줄 번호판을 자동 판별해서 통계에 남김
   (2줄 번호판은 나중에 인식 모델이 줄바꿈 없이 이어붙인 텍스트를 그대로 읽도록 학습시킬
   예정이라, 지금 단계에서는 판별만 해두고 크롭 자체는 그대로 통째로 저장함)
5) 크롭 이미지들을 ocr_train_data/crops/ 에 저장하고, ocr_train_data/labels.csv 에
   "파일명,정답텍스트,줄수판별" 형식으로 기록
6) 마지막에 전체 통계(총 장수, 성공/실패, 1줄/2줄 비율, 등장한 전체 문자 종류)를 출력

====================================================
실행 방법 (Anaconda Prompt)
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    python prepare_ocr_training_data.py

끝나면 화면에 출력되는 통계를 캡처해서 보내주세요 - 그걸 보고 다음 단계(실제 인식
모델 학습 스크립트)를 준비하겠습니다.
"""
import csv
from pathlib import Path

import cv2
import numpy as np


def imread_unicode(path: Path):
    """cv2.imread는 Windows에서 경로에 한글(비-ASCII)이 섞이면 내부적으로 조용히
    실패해서 None을 돌려주는 고질적인 문제가 있음 (파일이 멀쩡해도 못 읽음).
    번호판 사진 파일명이 전부 한글 번호판 텍스트라 이 문제를 100% 겪게 되므로,
    파일을 바이트로 직접 읽어 cv2.imdecode로 디코딩하는 방식으로 우회함."""
    try:
        data = np.fromfile(str(path), dtype=np.uint8)
        if data.size == 0:
            return None
        return cv2.imdecode(data, cv2.IMREAD_COLOR)
    except Exception:
        return None


def imwrite_unicode(path: Path, img) -> bool:
    """imread_unicode와 대칭되는 이유로, 저장 파일명에도 한글(정답 텍스트)이 들어가므로
    cv2.imwrite 대신 imencode + tofile로 우회해서 저장함."""
    ext = path.suffix if path.suffix else ".jpg"
    ok, buf = cv2.imencode(ext, img)
    if not ok:
        return False
    buf.tofile(str(path))
    return True

SRC_DIR = Path(r"C:\Users\user\Desktop\화물차_merged")
IMAGES_DIR = SRC_DIR / "images"
LABELS_DIR = SRC_DIR / "labels"

OUT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8\ocr_train_data")
CROPS_DIR = OUT_DIR / "crops"
LABELS_CSV = OUT_DIR / "labels.csv"

PAD_RATIO = 0.12  # plate_detector_gui.py의 _padded_crop과 동일하게 맞춤


def extract_text_from_filename(stem: str) -> str:
    """파일명(확장자 제외)에서 정답 텍스트를 뽑음. 아주 드물게 파일명이 통째로 두 번
    이어붙은 경우(예: "경북98사1394경북98사1394")가 있어서, 그런 경우만 절반으로 자름."""
    n = len(stem)
    if n % 2 == 0 and stem[: n // 2] == stem[n // 2 :]:
        return stem[: n // 2]
    return stem


def read_yolo_box(label_path: Path, img_w: int, img_h: int):
    """YOLO 형식 라벨(class cx cy w h, 0~1 정규화) 중 첫 번째 박스를 픽셀 좌표로 변환.
    한 이미지에 번호판 박스가 이미 1개만 있다고 가정(검출 학습용 데이터라 그렇게 준비됨)."""
    with open(label_path, "r", encoding="utf-8") as f:
        line = f.readline().strip()
    if not line:
        return None
    parts = line.split()
    if len(parts) < 5:
        return None
    _, cx, cy, w, h = parts[:5]
    cx, cy, w, h = float(cx), float(cy), float(w), float(h)
    bw, bh = w * img_w, h * img_h
    x1 = cx * img_w - bw / 2
    y1 = cy * img_h - bh / 2
    x2 = cx * img_w + bw / 2
    y2 = cy * img_h + bh / 2
    return x1, y1, x2, y2


def padded_crop(img, x1, y1, x2, y2, pad_ratio=PAD_RATIO):
    h_img, w_img = img.shape[:2]
    bw, bh = x2 - x1, y2 - y1
    pad_x = max(3.0, bw * pad_ratio)
    pad_y = max(3.0, bh * pad_ratio)
    px1 = max(0, int(x1 - pad_x))
    py1 = max(0, int(y1 - pad_y))
    px2 = min(w_img, int(x2 + pad_x))
    py2 = min(h_img, int(y2 + pad_y))
    return img[py1:py2, px1:px2]


def guess_line_count(crop) -> int:
    """크롭의 가로/세로 비율로 1줄/2줄을 대략 판별 (번호판 실물 비율 기준 - 1줄은
    가로가 세로의 3배 이상으로 납작하고, 2줄은 그보다 정사각형에 가까움)."""
    h, w = crop.shape[:2]
    if h == 0:
        return 0
    ratio = w / h
    return 1 if ratio >= 2.8 else 2


def main():
    CROPS_DIR.mkdir(parents=True, exist_ok=True)

    if not IMAGES_DIR.exists():
        print(f"[오류] 이미지 폴더를 찾을 수 없습니다: {IMAGES_DIR}")
        return

    image_paths = sorted(
        p for p in IMAGES_DIR.iterdir() if p.suffix.lower() in (".jpg", ".jpeg", ".png")
    )
    print(f"총 이미지 {len(image_paths)}장 발견")

    rows = []
    fail_no_label = 0
    fail_no_box = 0
    fail_bad_image = 0
    fail_empty_crop = 0
    line1_count = 0
    line2_count = 0
    all_chars = set()

    for idx, img_path in enumerate(image_paths):
        label_path = LABELS_DIR / (img_path.stem + ".txt")
        if not label_path.exists():
            fail_no_label += 1
            continue

        img = imread_unicode(img_path)
        if img is None:
            fail_bad_image += 1
            continue
        h_img, w_img = img.shape[:2]

        box = read_yolo_box(label_path, w_img, h_img)
        if box is None:
            fail_no_box += 1
            continue

        crop = padded_crop(img, *box)
        if crop is None or crop.size == 0:
            fail_empty_crop += 1
            continue

        text = extract_text_from_filename(img_path.stem)
        if not text:
            continue

        n_lines = guess_line_count(crop)
        if n_lines == 1:
            line1_count += 1
        else:
            line2_count += 1
        all_chars.update(text)

        out_name = f"{idx:05d}__{text}{img_path.suffix.lower()}"
        if not imwrite_unicode(CROPS_DIR / out_name, crop):
            continue
        rows.append((out_name, text, n_lines))

        if (idx + 1) % 200 == 0:
            print(f"  ...{idx + 1}/{len(image_paths)} 처리됨")

    with open(LABELS_CSV, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(["filename", "text", "line_count_guess"])
        writer.writerows(rows)

    print("\n=== 완료 ===")
    print(f"성공: {len(rows)}장  ->  {CROPS_DIR}")
    print(f"라벨 파일: {LABELS_CSV}")
    print(f"실패 - 라벨 없음: {fail_no_label}, 박스 없음: {fail_no_box}, "
          f"이미지 열기 실패: {fail_bad_image}, 빈 크롭: {fail_empty_crop}")
    print(f"1줄 번호판(추정): {line1_count}장, 2줄 번호판(추정): {line2_count}장")
    print(f"등장한 전체 문자 종류: {len(all_chars)}개")
    print("문자 목록: " + "".join(sorted(all_chars)))


if __name__ == "__main__":
    main()
