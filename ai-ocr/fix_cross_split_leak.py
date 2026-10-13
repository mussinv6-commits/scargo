# -*- coding: utf-8 -*-
"""
merge_to_dataset.py가 만든 교차 스플릿 누수(221개)를 고친다.
merge_to_dataset.py와 완전히 동일한 로직(SEED=42, 같은 정렬+셔플)으로
"새로 추가된 파일이 어느 스플릿에 배정됐었는지"를 그대로 재계산한 뒤,
그 stem이 다른 스플릿에 이미 원본으로 존재하면 -> 새로 추가된 파일을
원본이 있는 스플릿으로 옮긴다(파일 이동, 복사 아님). 원본은 절대 건드리지 않음.
"""
import os
import random
import shutil

MERGED_DIR = r"C:\Users\user\Desktop\화물차_merged"
PROJECT_DIR = r"C:\Users\user\Desktop\project.v4i.yolov8"

SPLIT_RATIO = {"train": 0.7, "valid": 0.2, "test": 0.1}
SEED = 42
SPLITS = ["train", "valid", "test"]


def base_stem(fname):
    stem = os.path.splitext(fname)[0]
    if stem.endswith("_truck2"):
        stem = stem[: -len("_truck2")]
    return stem


def find_file_with_stem(folder, stem):
    """folder 안에서 base_stem이 일치하는 실제 파일명을 찾는다 (stem.ext 또는 stem_truck2.ext)."""
    for fname in os.listdir(folder):
        if base_stem(fname) == stem:
            return fname
    return None


def main():
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

    # 현재 각 스플릿에 존재하는 stem 집합 파악
    split_stems = {}
    for split in SPLITS:
        img_dir = os.path.join(PROJECT_DIR, split, "images")
        split_stems[split] = {base_stem(f) for f in os.listdir(img_dir)}

    moved = 0
    already_ok = 0
    not_found = 0

    for stem, new_split in split_map.items():
        # 이 stem이 다른 스플릿에도 존재하는지 (= 원본이 있는지)
        other_splits = [s for s in SPLITS if s != new_split and stem in split_stems[s]]
        if not other_splits:
            already_ok += 1
            continue  # 누수 아님, 그대로 둠

        original_split = other_splits[0]  # 보통 하나뿐

        src_img_dir = os.path.join(PROJECT_DIR, new_split, "images")
        src_label_dir = os.path.join(PROJECT_DIR, new_split, "labels")
        dst_img_dir = os.path.join(PROJECT_DIR, original_split, "images")
        dst_label_dir = os.path.join(PROJECT_DIR, original_split, "labels")

        img_fname = find_file_with_stem(src_img_dir, stem)
        label_fname = find_file_with_stem(src_label_dir, stem)

        if img_fname is None or label_fname is None:
            not_found += 1
            print(f"경고: {new_split}에서 {stem} 파일을 못 찾음")
            continue

        ext = os.path.splitext(img_fname)[1]
        out_stem = stem
        dst_img = os.path.join(dst_img_dir, out_stem + ext)
        dst_label = os.path.join(dst_label_dir, out_stem + ".txt")

        # 목적지(원본이 있는 스플릿)에 이미 같은 이름이 있으면 접미사 붙이기
        if os.path.exists(dst_img) or os.path.exists(dst_label):
            out_stem = stem + "_truck2"
            dst_img = os.path.join(dst_img_dir, out_stem + ext)
            dst_label = os.path.join(dst_label_dir, out_stem + ".txt")

        shutil.move(os.path.join(src_img_dir, img_fname), dst_img)
        shutil.move(os.path.join(src_label_dir, label_fname), dst_label)

        # 이동 후 split_stems 갱신 (같은 stem이 여러 스플릿 쌍에 걸쳐있는 극히 드문 경우 대비)
        split_stems[new_split].discard(stem)
        split_stems[original_split].add(stem)

        moved += 1

    print(f"\n=== 정리 완료 ===")
    print(f"이동한 파일(누수 해결): {moved}쌍")
    print(f"원래 문제 없던 것: {already_ok}개")
    print(f"파일 못 찾음(수동 확인 필요): {not_found}개")

    print("\n*** 다시 한번: 학습 돌리기 전에 아래 캐시 삭제 필수 ***")
    print(r"  train\labels.cache")
    print(r"  valid\labels.cache")


if __name__ == "__main__":
    main()
