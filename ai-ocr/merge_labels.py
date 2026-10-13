# -*- coding: utf-8 -*-
"""
auto_labels / recheck_labels / recheck_labels2 결과를 정리해서
하나의 최종 라벨 세트로 합치는 스크립트.
- 이미지당 박스가 여러 개면: confidence 있으면 최고 confidence만, 없으면 제일 큰 박스만 채택
- confidence 컬럼은 제거하고 표준 YOLO 포맷(class cx cy w h)으로 저장
- 완전 미검출(still_no_detection.txt + still_no_detection2.txt) 이미지는 제외
- 결과는 화물차_merged\images, 화물차_merged\labels 에 모음 (원본은 안 건드림, 복사만 함)
"""
import os
import shutil

SRC_DIR = r"C:\Users\user\Desktop\화물차 번호판 사진"
OUT_DIR = os.path.join(SRC_DIR, "..", "화물차_merged")
OUT_DIR = os.path.normpath(OUT_DIR)

LABEL_SOURCES = [
    os.path.join(SRC_DIR, "auto_labels", "labels"),      # conf 없음 (save_conf=False)
    os.path.join(SRC_DIR, "recheck_labels", "labels"),   # conf 있음
    os.path.join(SRC_DIR, "recheck_labels2", "labels"),  # conf 있음
]

EXCLUDE_LISTS = [
    os.path.join(SRC_DIR, "still_no_detection.txt"),
    os.path.join(SRC_DIR, "still_no_detection2.txt"),
]


def load_exclude_set():
    excluded = set()
    for path in EXCLUDE_LISTS:
        if not os.path.exists(path):
            continue
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                name = line.strip()
                if name:
                    stem = os.path.splitext(name)[0]
                    excluded.add(stem)
    return excluded


def pick_best_line(lines):
    """여러 줄(박스) 중 하나만 남긴다."""
    parsed = []
    for line in lines:
        parts = line.strip().split()
        if len(parts) < 5:
            continue
        parsed.append(parts)

    if not parsed:
        return None
    if len(parsed) == 1:
        return parsed[0][:5]

    # confidence 컬럼(6번째)이 있으면 최고 confidence, 없으면 제일 큰 박스(w*h) 채택
    has_conf = all(len(p) >= 6 for p in parsed)
    if has_conf:
        best = max(parsed, key=lambda p: float(p[5]))
    else:
        best = max(parsed, key=lambda p: float(p[3]) * float(p[4]))
    return best[:5]


def main():
    excluded = load_exclude_set()
    print(f"제외 대상(완전 미검출): {len(excluded)}장")

    out_images = os.path.join(OUT_DIR, "images")
    out_labels = os.path.join(OUT_DIR, "labels")
    os.makedirs(out_images, exist_ok=True)
    os.makedirs(out_labels, exist_ok=True)

    total = 0
    multi_box_count = 0
    merged_stems = set()

    for label_dir in LABEL_SOURCES:
        if not os.path.isdir(label_dir):
            print(f"(건너뜀 - 폴더 없음) {label_dir}")
            continue

        for fname in os.listdir(label_dir):
            if not fname.endswith(".txt"):
                continue
            stem = os.path.splitext(fname)[0]

            if stem in excluded:
                continue
            if stem in merged_stems:
                # 여러 라운드에 같은 이미지가 중복으로 있으면 먼저 처리된 것 우선
                continue

            label_path = os.path.join(label_dir, fname)
            with open(label_path, "r", encoding="utf-8") as f:
                lines = [l for l in f.readlines() if l.strip()]

            if not lines:
                continue
            if len(lines) > 1:
                multi_box_count += 1

            best = pick_best_line(lines)
            if best is None:
                continue

            # 원본 이미지 찾기 (jpeg/jpg 둘 다 시도)
            src_img = None
            for ext in (".jpeg", ".jpg", ".png"):
                candidate = os.path.join(SRC_DIR, stem + ext)
                if os.path.exists(candidate):
                    src_img = candidate
                    break
            if src_img is None:
                print(f"경고: 원본 이미지 못 찾음 -> {stem}")
                continue

            # 이미지 복사
            dst_img = os.path.join(out_images, os.path.basename(src_img))
            shutil.copy2(src_img, dst_img)

            # 라벨 저장 (confidence 컬럼 제거, class cx cy w h만)
            dst_label = os.path.join(out_labels, stem + ".txt")
            with open(dst_label, "w", encoding="utf-8") as f:
                f.write(" ".join(best) + "\n")

            merged_stems.add(stem)
            total += 1

    print(f"\n최종 병합 완료: {total}장")
    print(f"박스 여러개라서 1개만 채택한 이미지: {multi_box_count}장")
    print(f"결과 위치: {OUT_DIR}")


if __name__ == "__main__":
    main()
