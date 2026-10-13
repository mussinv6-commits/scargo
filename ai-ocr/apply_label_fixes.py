# -*- coding: utf-8 -*-
"""
apply_label_fixes.py
====================================================
audit_labels.py (또는 audit_gui.py)가 만든/채운 label_audit_candidates.csv를
실제 ocr_train_data/labels.csv에 반영하는 스크립트.

new_label 칸의 값에 따라 동작이 다름:
  - 비어있음                : 변경 없음 (모델이 틀렸고 원래 라벨이 맞는 경우)
  - "DELETE"                : 번호판이 아니거나 읽을 수 없는 불량 크롭 -> labels.csv에서
                               해당 행을 통째로 제거하고, crops 폴더의 이미지 파일도
                               crops_removed 폴더로 옮겨서 학습에서 완전히 제외
  - 그 외 텍스트             : 라벨 오류 수정 -> text 칸을 새 값으로 교체

실수로 잘못 반영해도 labels.csv를 덮어쓰기 전에 labels_backup_<타임스탬프>.csv로
원본을 먼저 백업해둠.

실행 방법
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    python apply_label_fixes.py
"""
import csv
import shutil
from datetime import datetime
from pathlib import Path

PROJECT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
DATA_DIR = PROJECT_DIR / "ocr_train_data"
CROPS_DIR = DATA_DIR / "crops"
REMOVED_DIR = DATA_DIR / "crops_removed"
LABELS_CSV = DATA_DIR / "labels.csv"
AUDIT_CSV = DATA_DIR / "label_audit_candidates.csv"

DELETE_MARK = "DELETE"


def main():
    if not AUDIT_CSV.exists():
        print(f"{AUDIT_CSV} 가 없습니다. 먼저 audit_labels.py를 실행하고, "
              f"엑셀에서(또는 audit_gui.py로) new_label 칸을 채운 뒤 다시 실행해주세요.")
        return

    with open(AUDIT_CSV, "r", encoding="utf-8-sig") as f:
        fixes = {}
        for r in csv.DictReader(f):
            new_label = (r.get("new_label(정답이면 여기에 입력)") or "").strip()
            if new_label:
                fixes[r["filename"]] = new_label

    if not fixes:
        print("new_label이 채워진 행이 없습니다 - 반영할 내용이 없어 종료합니다.")
        return

    deletes = {fn: v for fn, v in fixes.items() if v == DELETE_MARK}
    relabels = {fn: v for fn, v in fixes.items() if v != DELETE_MARK}
    print(f"라벨 수정: {len(relabels)}건, 제거(불량 크롭): {len(deletes)}건")

    with open(LABELS_CSV, "r", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames
        rows = list(reader)

    # 덮어쓰기 전에 원본을 반드시 백업 - 잘못 반영했을 때 되돌릴 수 있게.
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = DATA_DIR / f"labels_backup_{ts}.csv"
    shutil.copy(LABELS_CSV, backup_path)
    print(f"원본 백업: {backup_path}")

    applied = 0
    kept_rows = []
    for row in rows:
        fn = row["filename"]
        if fn in deletes:
            print(f"  [제거] {fn}")
            continue  # labels.csv에서 이 행 자체를 뺌
        if fn in relabels:
            old = row["text"]
            new = relabels[fn]
            if old != new:
                print(f"  [수정] {fn}: {old} -> {new}")
                row["text"] = new
                applied += 1
        kept_rows.append(row)

    with open(LABELS_CSV, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(kept_rows)

    # 불량 크롭 이미지 파일도 실제로 옮겨서 눈에 안 띄게 정리
    if deletes:
        REMOVED_DIR.mkdir(exist_ok=True)
        moved, missing = 0, 0
        for fn in deletes:
            src = CROPS_DIR / fn
            if src.exists():
                shutil.move(str(src), str(REMOVED_DIR / fn))
                moved += 1
            else:
                missing += 1
        print(f"불량 크롭 파일 이동: {moved}개 -> {REMOVED_DIR} (파일 없음 {missing}개)")

    print(f"\n완료 - 라벨 수정 {applied}건, 제거 {len(deletes)}건 반영됨. "
          f"{LABELS_CSV} 에 총 {len(kept_rows)}행 남음 (원래 {len(rows)}행).")
    print("train_ocr_recognizer.py를 다시 돌려서 재학습하면 "
          "고쳐진 라벨/줄어든 데이터로 학습/평가됩니다.")


if __name__ == "__main__":
    main()
