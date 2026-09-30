# -*- coding: utf-8 -*-
"""
merge_reviewed_labels.py
====================================================
추가학습_검수 폴더 검수가 끝난 뒤(검수_안내.txt 6단계) images/labels 내용을
train/images, train/labels로 복사(병합)하는 스크립트.

2026-09-29 검수 작업 요약(Claude가 처리):
- 라벨 초안이 2~3개 겹쳐 있던 12장은 실제 번호판 사진과 대조해서 진짜 번호판
  박스 하나만 남기고 나머지(꼬리등/반사테이프/전화번호 스티커 등 오탐지)는
  버림. 이 중 2장(006너9792, 경남82사6125)은 기존 후보 2개가 전부 오탐지라
  번호판 위치를 새로 그려 넣음.
- 라벨 초안이 아예 없던 6장(수동_라벨링_필요.txt)은 사진을 직접 보고 번호판
  위치에 박스를 새로 그림.
- 나머지 단일 박스 라벨 219장 중 8장을 무작위로 뽑아 사진과 대조 확인 -
  8장 전부 정상(번호판 위치와 일치). 모델이 conf>=0.15에서 뽑은 단일
  후보라 이 219장은 labelImg로 전수 재검수는 안 했음 - 병합 전에 한 번 더
  labelImg로 훑어보고 싶으면 이 스크립트 실행 전에 해도 됨(병합 후에도
  train/labels에서 직접 고칠 수 있음).

이 스크립트가 하는 일:
1. 추가학습_검수/labels의 라벨 파일을 열어 "클래스 cx cy w h" 형식이 맞는지
   가볍게 검사함(줄 수, 숫자 범위 0~1) - 이상하면 건너뛰고 경고만 출력,
   train에는 절대 안 넣음(잘못된 라벨이 조용히 섞여 들어가는 걸 막기 위함).
2. 검사를 통과한 것만 이미지+라벨 쌍을 train/images, train/labels로 복사.
   대상 경로에 같은 이름 파일이 이미 있으면(=이미 병합된 적 있음) 덮어쓰지
   않고 건너뜀 - 실수로 두 번 실행해도 안전함.
3. 끝나고 복사/건너뜀 개수를 요약 출력.

실행:
    python merge_reviewed_labels.py
    (미리보기만 하려면) python merge_reviewed_labels.py --dry-run
"""
import shutil
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
SRC_DIR = APP_DIR.parent / "추가학습_검수"
SRC_IMAGES = SRC_DIR / "images"
SRC_LABELS = SRC_DIR / "labels"
DST_IMAGES = APP_DIR / "train" / "images"
DST_LABELS = APP_DIR / "train" / "labels"


def _validate_label(path):
    """가벼운 형식 검사만 함(내용이 실제로 번호판을 잘 잡았는지는 검사 못함 -
    그건 검수_안내.txt의 labelImg 육안 확인 몫). 줄마다 "정수 float*4" 5개
    토큰이고, cx/cy/w/h가 0~1 범위인지만 확인."""
    try:
        lines = path.read_text(encoding="utf-8").strip().splitlines()
    except Exception as e:
        return False, f"읽기 실패: {e}"
    if not lines:
        return False, "빈 파일"
    for i, line in enumerate(lines):
        parts = line.split()
        if len(parts) != 5:
            return False, f"{i+1}번째 줄 토큰 수 이상({len(parts)}개)"
        try:
            cls = int(parts[0])
            cx, cy, w, h = (float(x) for x in parts[1:])
        except ValueError:
            return False, f"{i+1}번째 줄 숫자 파싱 실패"
        if cls != 0:
            return False, f"{i+1}번째 줄 클래스가 0이 아님({cls})"
        for name, v in (("cx", cx), ("cy", cy), ("w", w), ("h", h)):
            if not (0.0 < v <= 1.0):
                return False, f"{i+1}번째 줄 {name}={v} 범위 벗어남(0~1)"
    return True, f"{len(lines)}줄(박스 {len(lines)}개)"


def main():
    dry_run = "--dry-run" in sys.argv

    if not SRC_IMAGES.exists() or not SRC_LABELS.exists():
        raise SystemExit(f"소스 폴더를 못 찾음: {SRC_IMAGES} / {SRC_LABELS}")
    DST_IMAGES.mkdir(parents=True, exist_ok=True)
    DST_LABELS.mkdir(parents=True, exist_ok=True)

    copied = skipped_exists = skipped_bad_label = skipped_no_label = 0

    for img_path in sorted(SRC_IMAGES.iterdir()):
        if img_path.suffix.lower() not in (".jpg", ".jpeg", ".png"):
            continue
        label_path = SRC_LABELS / f"{img_path.stem}.txt"
        if not label_path.exists():
            print(f"[건너뜀-라벨없음] {img_path.name}")
            skipped_no_label += 1
            continue

        ok, msg = _validate_label(label_path)
        if not ok:
            print(f"[건너뜀-라벨이상] {img_path.name}: {msg}")
            skipped_bad_label += 1
            continue

        dst_img = DST_IMAGES / img_path.name
        dst_label = DST_LABELS / label_path.name
        if dst_img.exists() or dst_label.exists():
            print(f"[건너뜀-이미존재] {img_path.name}")
            skipped_exists += 1
            continue

        if not dry_run:
            shutil.copy2(img_path, dst_img)
            shutil.copy2(label_path, dst_label)
        print(f"[복사] {img_path.name} ({msg})")
        copied += 1

    print("\n==================== 요약 ====================")
    print(f"복사됨: {copied}")
    print(f"건너뜀(이미 train에 존재): {skipped_exists}")
    print(f"건너뜀(라벨 형식 이상 - 확인 필요): {skipped_bad_label}")
    print(f"건너뜀(라벨 파일 없음): {skipped_no_label}")
    if dry_run:
        print("\n--dry-run 모드라 실제로는 복사 안 함. 확인 후 --dry-run 없이 다시 실행하세요.")


if __name__ == "__main__":
    main()
