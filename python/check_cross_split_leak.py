# -*- coding: utf-8 -*-
"""
train/valid/test 사이에 같은 번호판(같은 파일명 기준, _truck2 접미사는 무시)이
서로 다른 스플릿에 흩어져 있는지 검사.
- 있으면 데이터 누수 위험 (같은 트럭 사진이 학습/검증에 동시에 존재)
"""
import os
from collections import defaultdict

PROJECT_DIR = r"C:\Users\user\Desktop\project.v4i.yolov8"
SPLITS = ["train", "valid", "test"]


def base_stem(fname):
    stem = os.path.splitext(fname)[0]
    if stem.endswith("_truck2"):
        stem = stem[: -len("_truck2")]
    return stem


def main():
    split_stems = {}
    for split in SPLITS:
        img_dir = os.path.join(PROJECT_DIR, split, "images")
        stems = set()
        for fname in os.listdir(img_dir):
            stems.add(base_stem(fname))
        split_stems[split] = stems
        print(f"{split}: 이미지 {len(os.listdir(img_dir))}개, 고유 번호판 stem {len(stems)}개")

    stem_to_splits = defaultdict(list)
    for split, stems in split_stems.items():
        for s in stems:
            stem_to_splits[s].append(split)

    leaks = {s: sp for s, sp in stem_to_splits.items() if len(sp) > 1}
    print(f"\n교차 스플릿 누수 의심(같은 stem이 2개 이상 스플릿에 존재): {len(leaks)}개")

    out_path = os.path.join(PROJECT_DIR, "cross_split_leak.txt")
    with open(out_path, "w", encoding="utf-8") as f:
        for s, sp in sorted(leaks.items()):
            f.write(f"{s}: {sp}\n")
    print(f"전체 목록 저장: {out_path}")

    for s, sp in list(leaks.items())[:20]:
        print(f"  {s}: {sp}")


if __name__ == "__main__":
    main()
