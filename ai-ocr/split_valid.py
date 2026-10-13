# train 폴더에 새로 넣은 이미지 중 일부를 무작위로 valid 폴더로 옮기는 스크립트
# 이미지 파일과 짝인 라벨(.txt) 파일을 함께 이동시킴
#
# 실행 전 확인:
#   1. 아래 DATASET_ROOT를 실제 project.v4i.yolov8 경로로 수정
#   2. VALID_RATIO로 얼마나 옮길지 조절 (기본 0.15 = 15%)

import random
import shutil
from pathlib import Path

DATASET_ROOT = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
VALID_RATIO = 0.15  # train 이미지 중 15%를 valid로 이동

TRAIN_IMAGES = DATASET_ROOT / "train" / "images"
TRAIN_LABELS = DATASET_ROOT / "train" / "labels"
VALID_IMAGES = DATASET_ROOT / "valid" / "images"
VALID_LABELS = DATASET_ROOT / "valid" / "labels"

IMAGE_EXTS = {".jpg", ".jpeg", ".png"}


def main():
    VALID_IMAGES.mkdir(parents=True, exist_ok=True)
    VALID_LABELS.mkdir(parents=True, exist_ok=True)

    image_files = [
        f for f in TRAIN_IMAGES.iterdir()
        if f.suffix.lower() in IMAGE_EXTS
    ]

    if not image_files:
        print("train/images 에 이미지가 없습니다. 경로를 확인하세요.")
        return

    n_move = max(1, int(len(image_files) * VALID_RATIO))
    to_move = random.sample(image_files, n_move)

    moved = 0
    skipped = []

    for img_path in to_move:
        label_path = TRAIN_LABELS / (img_path.stem + ".txt")

        if not label_path.exists():
            skipped.append(img_path.name)
            continue

        shutil.move(str(img_path), str(VALID_IMAGES / img_path.name))
        shutil.move(str(label_path), str(VALID_LABELS / label_path.name))
        moved += 1

    print(f"전체 train 이미지: {len(image_files)}장")
    print(f"valid로 이동: {moved}장")
    if skipped:
        print(f"라벨 없어서 건너뜀: {len(skipped)}장 -> {skipped}")


if __name__ == "__main__":
    main()
