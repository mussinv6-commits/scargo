# -*- coding: utf-8 -*-
"""
add_reviewed_to_ocr_train_data.py
====================================================
추가학습_검수(2026-09-29 검수 완료, 237장)를 인식모델(OCR) 학습 데이터에도
추가하기 위한 스크립트.

왜 필요한가:
prepare_ocr_training_data.py는 ocr_train_data/crops, labels.csv를 화물차_merged
폴더에서 통째로 새로 만드는 방식(labels.csv를 "w" 모드로 덮어씀)이라, 그걸 다시
돌리면 9/26에 수동으로 고친 라벨 오류 수정본(apply_label_fixes.py 작업 내역)이
날아갈 위험이 있음. 이 스크립트는 그 위험을 완전히 피하기 위해 기존
crops/labels.csv는 절대 안 건드리고, 오늘 새로 검수한 237장만 "추가로만" 덧붙임
(append-only). 크롭 방식(12% 패딩)과 텍스트 추출 방식은 prepare_ocr_training_data.py
와 동일하게 맞춰서 기존 데이터와 이질감이 없게 함.

안전장치:
- 기존 labels.csv 줄은 절대 안 지우거나 안 바꿈 - 새 줄만 맨 끝에 추가(append)
- 같은 정답 텍스트가 이미 labels.csv에 있으면 건너뜀(중복 방지)
- 크롭 대상 파일이 이미 crops/에 있으면 건너뜀 - 두 번 실행해도 안전
- --dry-run으로 먼저 몇 장이 추가될지 미리보기 가능

사용:
    python add_reviewed_to_ocr_train_data.py --dry-run   (미리보기, 아무 것도 안 씀)
    python add_reviewed_to_ocr_train_data.py              (실제 반영)

끝나고 train_ocr_recognizer_attn.py를 돌리면 이 237장(유효한 것만)까지 포함해서
재학습됩니다.
"""
import csv
import sys
from pathlib import Path

import cv2
import numpy as np

APP_DIR = Path(__file__).resolve().parent
SRC_DIR = APP_DIR.parent / "추가학습_검수"
SRC_IMAGES = SRC_DIR / "images"
SRC_LABELS = SRC_DIR / "labels"

OUT_DIR = APP_DIR / "ocr_train_data"
CROPS_DIR = OUT_DIR / "crops"
LABELS_CSV = OUT_DIR / "labels.csv"

PAD_RATIO = 0.12  # prepare_ocr_training_data.py와 동일하게 맞춤


def imread_unicode(path: Path):
    """경로에 한글이 섞이면 cv2.imread가 조용히 실패하는 문제 우회
    (prepare_ocr_training_data.py와 동일한 이유/방식)."""
    try:
        data = np.fromfile(str(path), dtype=np.uint8)
        if data.size == 0:
            return None
        return cv2.imdecode(data, cv2.IMREAD_COLOR)
    except Exception:
        return None


def imwrite_unicode(path: Path, img) -> bool:
    ext = path.suffix if path.suffix else ".jpg"
    ok, buf = cv2.imencode(ext, img)
    if not ok:
        return False
    buf.tofile(str(path))
    return True


def extract_text_from_filename(stem: str) -> str:
    """파일명이 통째로 두 번 이어붙은 드문 예외(예: "경북98사1394경북98사1394")만
    절반으로 자름 - prepare_ocr_training_data.py와 동일한 규칙."""
    n = len(stem)
    if n % 2 == 0 and stem[: n // 2] == stem[n // 2 :]:
        return stem[: n // 2]
    return stem


def read_yolo_box(label_path: Path, img_w: int, img_h: int):
    """라벨에 박스가 1개면 그걸 쓰고, 혹시 여러 개 남아있어도(검수 때 놓친 경우)
    넓이가 제일 큰 박스를 번호판으로 간주해서 안전하게 처리
    (prepare_ocr_training_data.py는 항상 첫 줄만 썼지만, 이 스크립트가 다루는
    추가학습_검수 라벨은 원래 겹쳐 있던 후보 중 검수로 골라낸 것이라 이렇게
    해도 결과가 달라지지 않고, 혹시 모를 누락에는 더 안전함)."""
    text = label_path.read_text(encoding="utf-8").strip()
    if not text:
        return None
    best_box, best_area = None, -1.0
    for line in text.splitlines():
        parts = line.split()
        if len(parts) < 5:
            continue
        try:
            _, cx, cy, w, h = parts[:5]
            cx, cy, w, h = float(cx), float(cy), float(w), float(h)
        except ValueError:
            continue
        bw, bh = w * img_w, h * img_h
        area = bw * bh
        if area > best_area:
            best_area = area
            x1 = cx * img_w - bw / 2
            y1 = cy * img_h - bh / 2
            x2 = cx * img_w + bw / 2
            y2 = cy * img_h + bh / 2
            best_box = (x1, y1, x2, y2)
    return best_box


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
    """prepare_ocr_training_data.py와 동일한 가로/세로 비율 판별 규칙."""
    h, w = crop.shape[:2]
    if h == 0:
        return 0
    ratio = w / h
    return 1 if ratio >= 2.8 else 2


def main():
    dry_run = "--dry-run" in sys.argv

    if not SRC_IMAGES.exists() or not SRC_LABELS.exists():
        raise SystemExit(f"소스 폴더를 못 찾음: {SRC_IMAGES} / {SRC_LABELS}")
    if not LABELS_CSV.exists():
        raise SystemExit(f"기존 labels.csv를 못 찾음: {LABELS_CSV}")

    # 기존에 이미 들어간 정답 텍스트(중복 방지용) - 같은 텍스트가 이미 있으면 건너뜀.
    existing_texts = set()
    with open(LABELS_CSV, "r", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            existing_texts.add(row["text"])
    print(f"기존 labels.csv: {len(existing_texts)}개 고유 텍스트")

    CROPS_DIR.mkdir(parents=True, exist_ok=True)

    new_rows = []
    added = 0
    skipped = {"중복텍스트": 0, "라벨없음": 0, "이미지열기실패": 0, "박스없음": 0,
               "빈크롭": 0, "크롭이미존재": 0}

    for img_path in sorted(SRC_IMAGES.iterdir()):
        if img_path.suffix.lower() not in (".jpg", ".jpeg", ".png"):
            continue
        label_path = SRC_LABELS / f"{img_path.stem}.txt"
        if not label_path.exists():
            print(f"[건너뜀-라벨없음] {img_path.name}")
            skipped["라벨없음"] += 1
            continue

        text = extract_text_from_filename(img_path.stem)
        if text in existing_texts:
            print(f"[건너뜀-중복텍스트] {img_path.name} (이미 labels.csv에 '{text}' 있음)")
            skipped["중복텍스트"] += 1
            continue

        out_name = f"reviewed0929__{text}{img_path.suffix.lower()}"
        out_path = CROPS_DIR / out_name
        if out_path.exists():
            print(f"[건너뜀-크롭이미존재] {img_path.name}")
            skipped["크롭이미존재"] += 1
            continue

        img = imread_unicode(img_path)
        if img is None:
            print(f"[건너뜀-이미지열기실패] {img_path.name}")
            skipped["이미지열기실패"] += 1
            continue
        h_img, w_img = img.shape[:2]

        box = read_yolo_box(label_path, w_img, h_img)
        if box is None:
            print(f"[건너뜀-박스없음] {img_path.name}")
            skipped["박스없음"] += 1
            continue

        crop = padded_crop(img, *box)
        if crop is None or crop.size == 0:
            print(f"[건너뜀-빈크롭] {img_path.name}")
            skipped["빈크롭"] += 1
            continue

        n_lines = guess_line_count(crop)

        if not dry_run:
            if not imwrite_unicode(out_path, crop):
                print(f"[건너뜀-크롭저장실패] {img_path.name}")
                continue
        new_rows.append((out_name, text, n_lines))
        existing_texts.add(text)
        added += 1
        print(f"[추가] {img_path.name} -> {out_name} ({n_lines}줄)")

    if not dry_run and new_rows:
        with open(LABELS_CSV, "a", newline="", encoding="utf-8-sig") as f:
            writer = csv.writer(f)
            writer.writerows(new_rows)

    print("\n==================== 요약 ====================")
    print(f"추가됨: {added}")
    for k, v in skipped.items():
        print(f"건너뜀({k}): {v}")
    if dry_run:
        print("\n--dry-run 모드라 실제로는 아무 것도 안 씀. 확인 후 --dry-run 없이 다시 실행하세요.")
    else:
        print(f"\nocr_train_data/labels.csv에 {added}줄 추가됨. 기존 줄은 전혀 안 건드림.")
        print("이제 train_ocr_recognizer_attn.py를 실행하면 이 데이터까지 포함해서 재학습됩니다.")


if __name__ == "__main__":
    main()
