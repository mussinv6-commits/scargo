# -*- coding: utf-8 -*-
"""
화물차_merged (1961장, 이미지+라벨)를 기존 project.v4i.yolov8의
train/valid/test에 7:2:1 비율로 무작위 분할해서 합친다.
- 이미지 단위로 섞은 뒤 나누기 때문에 train/valid/test 간 중복(데이터 누수) 없음
- 기존 train/valid/test 안의 파일은 절대 건드리지 않고, 새 파일만 추가
- 파일명이 기존과 겹치면 충돌 방지를 위해 _truck2 접미사 붙여서 저장
"""
import os
import random
import shutil

MERGED_DIR = r"C:\Users\user\Desktop\화물차_merged"
PROJECT_DIR = r"C:\Users\user\Desktop\project.v4i.yolov8"

SPLIT_RATIO = {"train": 0.7, "valid": 0.2, "test": 0.1}
SEED = 42


def main():
    images_dir = os.path.join(MERGED_DIR, "images")
    labels_dir = os.path.join(MERGED_DIR, "labels")

    stems = sorted(os.path.splitext(f)[0] for f in os.listdir(labels_dir) if f.endswith(".txt"))
    random.seed(SEED)
    random.shuffle(stems)

    n = len(stems)
    n_train = int(n * SPLIT_RATIO["train"])
    n_valid = int(n * SPLIT_RATIO["valid"])

    split_map = {}
    for i, stem in enumerate(stems):
        if i < n_train:
            split_map[stem] = "train"
        elif i < n_train + n_valid:
            split_map[stem] = "valid"
        else:
            split_map[stem] = "test"

    counts = {"train": 0, "valid": 0, "test": 0}
    skipped = []
    renamed = []

    for stem, split in split_map.items():
        src_img = None
        for ext in (".jpeg", ".jpg", ".png"):
            candidate = os.path.join(images_dir, stem + ext)
            if os.path.exists(candidate):
                src_img = candidate
                break
        src_label = os.path.join(labels_dir, stem + ".txt")

        if src_img is None or not os.path.exists(src_label):
            skipped.append(stem)
            continue

        dst_img_dir = os.path.join(PROJECT_DIR, split, "images")
        dst_label_dir = os.path.join(PROJECT_DIR, split, "labels")

        img_ext = os.path.splitext(src_img)[1]
        out_stem = stem
        dst_img = os.path.join(dst_img_dir, out_stem + img_ext)
        dst_label = os.path.join(dst_label_dir, out_stem + ".txt")

        # 기존 데이터셋과 파일명 충돌 방지
        if os.path.exists(dst_img) or os.path.exists(dst_label):
            out_stem = stem + "_truck2"
            dst_img = os.path.join(dst_img_dir, out_stem + img_ext)
            dst_label = os.path.join(dst_label_dir, out_stem + ".txt")
            renamed.append(stem)

        shutil.copy2(src_img, dst_img)
        shutil.copy2(src_label, dst_label)
        counts[split] += 1

    print("\n=== 병합 완료 ===")
    for k, v in counts.items():
        print(f"{k}: {v}장 추가")
    print(f"건너뜀(원본/라벨 못 찾음): {len(skipped)}장")
    if skipped:
        print("  ", skipped[:20])
    print(f"이름 충돌로 접미사 붙인 파일: {len(renamed)}장")
    if renamed:
        print("  ", renamed[:20])

    print("\n*** 중요 ***")
    print("다음에 학습 돌리기 전에 아래 labels.cache 파일들을 꼭 삭제하세요:")
    print(r"  train\labels.cache")
    print(r"  valid\labels.cache")
    print("(안 지우면 YOLO가 새로 추가된 파일을 못 읽고 예전 캐시를 그대로 씁니다)")


if __name__ == "__main__":
    main()
