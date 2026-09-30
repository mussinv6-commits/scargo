# -*- coding: utf-8 -*-
"""
prepare_assisted_labels.py
====================================================
"B안" (데이터 보강, 237장) - 보조 라벨링(assisted labeling) 준비 스크립트.

추가학습_후보목록.txt에 있는 237장(학습셋엔 아직 없지만 검출/인식에 문제가
있었던 것으로 확인된 사진들)에 대해, 현재 최고 성능 모델(plate_detect_m_lowlight)로
먼저 예측을 돌려서 YOLO 라벨(.txt) 초안을 자동으로 만들어줍니다.

즉, 사용자가 237장을 처음부터 손으로 다 그릴 필요 없이:
  - 모델이 이미 잘 찾은 사진 -> 박스만 눈으로 확인하고 넘어가면 됨
  - 모델이 못 찾았거나(또는 박스가 부정확한) 사진 -> 이런 것만 수동으로 그리거나 수정
으로 작업량을 크게 줄이는 게 목적입니다 (사용자가 선택한 "보조 라벨링(추천)" 방식).

====================================================
후보 이미지를 어디서 찾는가
====================================================
추가학습_후보목록.txt의 각 줄은 "파일명.jpeg  <- 출처"형태로, 출처가 3가지 중 하나임:
  - "저확신도(...화물차_merged)"  -> C:\\Users\\user\\Desktop\\화물차_merged\\images
  - "still_no_detection.txt"      -> C:\\Users\\user\\Desktop\\화물차 번호판 사진 (하위 폴더 포함 탐색)
  - "no_detection_list.txt"       -> C:\\Users\\user\\Desktop\\project.v4i.yolov8\\train\\images

경로가 사람마다/환경마다 조금 다를 수 있어서, 위 3곳을 전부 뒤져서 파일명이 일치하는
이미지를 찾습니다 (하위 폴더까지 재귀적으로 탐색). 혹시 못 찾은 파일이 있으면
"이미지_못찾음.txt"에 따로 기록되니, 그 목록만 사용자가 직접 위치를 확인해서
SEARCH_DIRS에 폴더를 추가하고 다시 돌리면 됩니다.

====================================================
결과물 (출력 폴더 구조 - train/images, train/labels와 동일한 형식)
====================================================
추가학습_검수/
  images/   <- 237장 원본 복사 (또는 찾은 만큼)
  labels/   <- 모델이 예측한 YOLO 라벨(.txt). 클래스 0(번호판) 박스 0개 이상.
             (탐지 결과가 아예 없는 사진은 labels 파일을 만들지 않고 "수동_라벨링_필요.txt"에 기록)
  검수_안내.txt  <- 검수 방법 안내

라벨 초안은 conf=0.15(낮게 잡아서 놓치는 것보다 일단 후보로 많이 보여주는 쪽을 택함 -
어차피 사람이 눈으로 확인하므로 오탐은 지우면 되고, 누락이 더 아까움)로 생성하고,
imgsz=960(학습 해상도) + augment=True(TTA)를 적용해 최대한 정확한 초안을 만듭니다.
한 사진에 번호판이 여러 개 있을 수 있어(예: 여러 대가 찍힌 사진) 박스를 1개로 제한하지
않고 conf=0.15 이상인 박스를 전부 라벨에 포함합니다.

====================================================
검수 방법 (라벨링 도구 예: labelImg)
====================================================
1. pip install labelImg (한 번만)
2. labelImg 실행 후 "Open Dir"로 추가학습_검수\\images 선택,
   "Change Save Dir"로 추가학습_검수\\labels 선택
3. YOLO 포맷으로 설정되어 있는지 확인 (화면 왼쪽 "PascalVOC" 표시를 클릭해서 "YOLO"로 전환)
4. 사진을 넘기면서 박스가 번호판을 제대로 잡았는지 확인 -> 문제 있으면 수정/삭제/추가
5. "수동_라벨링_필요.txt"에 있는 파일들은 박스가 아예 없으니 직접 새로 그려야 함
6. 검수가 끝나면 추가학습_검수\\images의 내용을 train\\images로,
   추가학습_검수\\labels의 내용을 train\\labels로 복사(병합)하면 재학습에 바로 쓸 수 있음
   (기존 train 폴더 파일과 이름이 겹치지 않으니 안전하게 그대로 복사하면 됨)

====================================================
실행 방법 (Anaconda Prompt, ultralytics 설치된 환경)
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    set KMP_DUPLICATE_LIB_OK=TRUE
    python prepare_assisted_labels.py
====================================================
"""
import os

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import shutil
from pathlib import Path

from ultralytics import YOLO

# ================= 설정 =================
PROJECT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
CANDIDATES_TXT = PROJECT_DIR / "추가학습_후보목록.txt"
MODEL_PATH = PROJECT_DIR / "runs" / "detect" / "plate_detect_m_lowlight" / "weights" / "best.pt"

# 후보 이미지를 찾아볼 폴더들 (하위 폴더까지 재귀적으로 뒤짐)
SEARCH_DIRS = [
    Path(r"C:\Users\user\Desktop\화물차_merged\images"),
    Path(r"C:\Users\user\Desktop\화물차 번호판 사진"),
    PROJECT_DIR / "train" / "images",
]

OUT_DIR = Path(r"C:\Users\user\Desktop") / "추가학습_검수"
OUT_IMG_DIR = OUT_DIR / "images"
OUT_LBL_DIR = OUT_DIR / "labels"
NOT_FOUND_TXT = OUT_DIR / "이미지_못찾음.txt"
NEEDS_MANUAL_TXT = OUT_DIR / "수동_라벨링_필요.txt"
GUIDE_TXT = OUT_DIR / "검수_안내.txt"

IMGSZ = 960
DRAFT_CONF = 0.15  # 초안 생성용 - 낮게 잡아서 누락보다 오탐(=사람이 지우면 그만)을 택함
# ==========================================================================


def parse_candidates(txt_path):
    """추가학습_후보목록.txt에서 실제 파일명만 뽑아냄 ("파일명  <- 출처" 형식, 주석/빈 줄 제외)."""
    names = []
    with open(txt_path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip() or line.startswith("=") or "<-" not in line:
                continue
            name = line.split("<-")[0].strip()
            if name:
                names.append(name)
    return names


def build_search_index(search_dirs):
    """SEARCH_DIRS 전체를 한 번만 재귀적으로 훑어서 {파일명: 전체경로} 인덱스를 만듦
    (매 파일마다 폴더를 새로 훑으면 237장 기준 느려지므로 미리 한 번에 인덱싱)."""
    index = {}
    exts = {".jpg", ".jpeg", ".png", ".bmp"}
    for d in search_dirs:
        if not d.exists():
            continue
        for p in d.rglob("*"):
            if p.is_file() and p.suffix.lower() in exts and p.name not in index:
                index[p.name] = p
    return index


def to_yolo_label(boxes_xyxy, boxes_conf, img_w, img_h):
    """YOLO 포맷(class x_center y_center width height, 0~1 정규화) 라벨 텍스트 생성.
    확신도 높은 순으로 정렬해서 사람이 검수할 때 위쪽 줄부터 더 믿을만한 박스가 오게 함."""
    order = sorted(range(len(boxes_conf)), key=lambda i: -boxes_conf[i])
    lines = []
    for i in order:
        x1, y1, x2, y2 = boxes_xyxy[i]
        xc = ((x1 + x2) / 2) / img_w
        yc = ((y1 + y2) / 2) / img_h
        w = (x2 - x1) / img_w
        h = (y2 - y1) / img_h
        lines.append(f"0 {xc:.6f} {yc:.6f} {w:.6f} {h:.6f}")
    return "\n".join(lines)


if __name__ == "__main__":  # Windows에서 필수
    if not CANDIDATES_TXT.exists():
        print(f"[오류] 후보 목록 파일을 찾을 수 없습니다: {CANDIDATES_TXT}")
        raise SystemExit(1)
    if not MODEL_PATH.exists():
        print(f"[오류] 모델을 찾을 수 없습니다: {MODEL_PATH}")
        raise SystemExit(1)

    OUT_IMG_DIR.mkdir(parents=True, exist_ok=True)
    OUT_LBL_DIR.mkdir(parents=True, exist_ok=True)

    candidates = parse_candidates(CANDIDATES_TXT)
    print(f"후보 목록: {len(candidates)}장")

    print("이미지 위치 인덱싱 중 (화물차_merged, 화물차 번호판 사진, train/images)...")
    index = build_search_index(SEARCH_DIRS)
    print(f"탐색된 이미지 총 {len(index)}장 (검색 대상 폴더 기준)")

    model = YOLO(str(MODEL_PATH))

    found_count = 0
    not_found = []
    needs_manual = []
    drafted = 0

    for name in candidates:
        src = index.get(name)
        if src is None:
            not_found.append(name)
            continue
        found_count += 1

        dst_img = OUT_IMG_DIR / name
        shutil.copy2(src, dst_img)

        r = model.predict(
            source=str(src), conf=DRAFT_CONF, imgsz=IMGSZ, augment=True, verbose=False
        )[0]
        boxes_xyxy = r.boxes.xyxy.cpu().numpy()
        boxes_conf = r.boxes.conf.cpu().numpy()
        img_h, img_w = r.orig_shape

        if len(boxes_xyxy) == 0:
            needs_manual.append(name)
            continue

        label_text = to_yolo_label(boxes_xyxy, boxes_conf, img_w, img_h)
        label_path = OUT_LBL_DIR / (Path(name).stem + ".txt")
        with open(label_path, "w", encoding="utf-8") as f:
            f.write(label_text + "\n")
        drafted += 1

    with open(NOT_FOUND_TXT, "w", encoding="utf-8") as f:
        f.write(f"총 {len(not_found)}장 - SEARCH_DIRS 폴더들에서 못 찾음\n")
        f.write("파일 위치를 직접 확인한 뒤, 스크립트의 SEARCH_DIRS에 해당 폴더를 추가하고 다시 돌리거나\n")
        f.write(f"아래 파일들을 직접 {OUT_IMG_DIR}에 복사해서 라벨링해주세요.\n\n")
        for n in not_found:
            f.write(n + "\n")

    with open(NEEDS_MANUAL_TXT, "w", encoding="utf-8") as f:
        f.write(f"총 {len(needs_manual)}장 - 모델이 conf={DRAFT_CONF}에서도 전혀 못 찾음 (라벨 초안 없음)\n")
        f.write("labelImg 등에서 박스를 처음부터 직접 그려야 하는 사진들입니다.\n\n")
        for n in needs_manual:
            f.write(n + "\n")

    with open(GUIDE_TXT, "w", encoding="utf-8") as f:
        f.write(
            "추가학습_검수 폴더 안내\n"
            "====================================================\n"
            f"- images/: 찾은 이미지 {found_count}장 복사본\n"
            f"- labels/: 모델이 만든 라벨 초안 {drafted}장 (conf={DRAFT_CONF} 이상 박스, YOLO 포맷)\n"
            f"- 수동_라벨링_필요.txt: 초안이 없어 처음부터 박스를 그려야 하는 {len(needs_manual)}장\n"
            f"- 이미지_못찾음.txt: 폴더에서 못 찾은 {len(not_found)}장 (있다면 위치 확인 필요)\n\n"
            "검수 방법:\n"
            "1) labelImg 설치: pip install labelImg\n"
            "2) labelImg 실행 -> Open Dir: images 폴더, Change Save Dir: labels 폴더\n"
            "3) 왼쪽 포맷 표시를 클릭해서 YOLO로 전환\n"
            "4) 사진을 넘기며 박스가 번호판을 제대로 잡았는지 확인 (수정/삭제/추가)\n"
            "5) 수동_라벨링_필요.txt에 있는 파일들은 박스를 새로 그려야 함\n"
            "6) 검수 끝나면 images/labels 내용을 train/images, train/labels로 복사(병합)\n"
        )

    print("\n=== 완료 ===")
    print(f"후보 {len(candidates)}장 중 이미지 발견: {found_count}장 / 못 찾음: {len(not_found)}장")
    print(f"라벨 초안 생성됨: {drafted}장 / 초안 없음(수동 필요): {len(needs_manual)}장")
    print(f"\n결과 폴더: {OUT_DIR}")
    print(f"안내 파일: {GUIDE_TXT}")
    if not_found:
        print(f"\n[참고] 못 찾은 파일 목록은 {NOT_FOUND_TXT}에서 확인하세요.")
