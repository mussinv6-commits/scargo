# -*- coding: utf-8 -*-
"""
run_experiment_expanded.py
====================================================
"B안" 실험: 저조도 개선(hsv_v 0.4->0.7, plate_detect_m_lowlight)에 더해서,
추가학습_검수 폴더의 237장(자동 라벨 초안 231장 + 직접 확인한 6장)을 학습 데이터에
더 넣어서 재학습합니다.

====================================================
데이터를 실제로 복사하지 않고 합치는 방법
====================================================
기존 train/images, train/labels 폴더를 그대로 두고, 새 이미지 237장은
C:\\Users\\user\\Desktop\\추가학습_검수\\images (+ 같은 위치의 labels 폴더)에 그대로 둔 채,
data_expanded.yaml에서 train 항목을 리스트로 만들어 두 경로를 모두 학습에 포함시킵니다:

    train:
      - ../train/images
      - C:/Users/user/Desktop/추가학습_검수/images

ultralytics는 각 이미지 경로의 "images"를 "labels"로 바꿔서 라벨 파일을 찾으므로,
추가학습_검수/images 옆의 추가학습_검수/labels가 자동으로 짝지어집니다.
(기존 train 폴더를 건드리지 않아서 원본 데이터셋은 그대로 보존됩니다.)

====================================================
이번 실험에서 바꾼 것
====================================================
- 데이터: 기존 1922장(대략) + 신규 237장 = 약 2159장 (data_expanded.yaml)
- hsv_v: 0.7 유지 (plate_detect_m_lowlight에서 이미 검증된 저조도 개선 값 그대로)
- 나머지 하이퍼파라미터는 plate_detect_m_lowlight와 완전히 동일 (모델, batch, imgsz 등)
  -> 이번에 추가된 변수는 "학습 데이터량"뿐이라, 재학습 후 성능 차이가 있다면
     데이터 추가의 순수한 효과로 해석할 수 있습니다 (지금까지의 단일 변수 실험 원칙 유지).

====================================================
실행 방법 (Anaconda Prompt, GPU 있는 환경 - base 환경 권장)
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    set KMP_DUPLICATE_LIB_OK=TRUE
    python run_experiment_expanded.py

    끝나면 자동으로 experiments_log.csv에 결과 한 줄이 추가되고, 화면에
    전체 실험 비교표가 출력됩니다. plate_detect_m_lowlight(mAP50-95=0.874)와
    비교해서 성능이 비슷하거나 더 좋으면, check_confidence.py를 이번 모델로 다시
    돌려서 화물차_merged 1961장 기준 평균 confidence가 더 올랐는지 확인해보세요
    (직전 실험의 성공 기준이 mAP가 아니라 실제 confidence 개선이었던 것과 같은 이유).

====================================================
"""
import os

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import csv
import time
from pathlib import Path

from ultralytics import YOLO

# ================= 실험 설정 =================
RUN_NAME = "plate_detect_m_expanded"    # runs/detect/plate_detect_m_expanded 에 결과 저장
MODEL = "yolo11m.pt"                     # plate_detect_m_lowlight와 동일
IMGSZ = 960
BATCH = 6                                # plate_detect_m_lowlight와 동일
HSV_V = 0.7                              # plate_detect_m_lowlight에서 검증된 값 그대로 유지
NOTE = "B안 데이터 보강 - 추가학습_검수 237장(자동 초안 231 + 수동 6) 추가, hsv_v=0.7 유지"
# ==============================================================================

PROJECT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
DATA_YAML = PROJECT_DIR / "data_expanded.yaml"   # train 항목이 리스트로 두 폴더를 합침
TRAIN_IMG_DIR = PROJECT_DIR / "train" / "images"
EXTRA_IMG_DIR = Path(r"C:\Users\user\Desktop\추가학습_검수\images")
LOG_CSV = PROJECT_DIR / "experiments_log.csv"

FIELDNAMES = [
    "date", "run_name", "model", "train_images", "imgsz", "batch",
    "precision", "recall", "mAP50", "mAP50-95", "train_hours", "note",
]


def count_train_images():
    """기존 train/images + 추가학습_검수/images 두 폴더를 합친 실제 학습 이미지 수."""
    exts = {".jpg", ".jpeg", ".png", ".bmp"}
    total = 0
    for d in (TRAIN_IMG_DIR, EXTRA_IMG_DIR):
        if d.exists():
            total += sum(1 for p in d.iterdir() if p.suffix.lower() in exts)
    return total


def append_log(row: dict):
    is_new = not LOG_CSV.exists()
    with open(LOG_CSV, "a", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=FIELDNAMES)
        if is_new:
            writer.writeheader()
        writer.writerow(row)


def print_comparison_table():
    if not LOG_CSV.exists():
        return
    with open(LOG_CSV, newline="", encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))
    print("\n=== 전체 실험 비교 (experiments_log.csv) ===")
    header = f"{'run_name':<24}{'model':<20}{'imgsz':>7}{'mAP50':>8}{'mAP50-95':>10}"
    print(header)
    print("-" * len(header))
    for r in rows:
        print(
            f"{r['run_name']:<24}{r['model']:<20}{r.get('imgsz',''):>7}"
            f"{r.get('mAP50',''):>8}{r.get('mAP50-95',''):>10}"
        )


if __name__ == "__main__":
    if not DATA_YAML.exists():
        print(f"[오류] {DATA_YAML} 이 없습니다. data_expanded.yaml을 project.v4i.yolov8 폴더에 먼저 넣어주세요.")
        raise SystemExit(1)
    if not EXTRA_IMG_DIR.exists():
        print(f"[오류] {EXTRA_IMG_DIR} 이 없습니다. prepare_assisted_labels.py 결과 폴더가 맞는지 확인해주세요.")
        raise SystemExit(1)

    model = YOLO(MODEL)

    start = time.time()
    train_result = model.train(
        data=str(DATA_YAML),
        epochs=100,
        patience=30,
        imgsz=IMGSZ,
        batch=BATCH,
        box=15.0,
        cos_lr=True,
        close_mosaic=20,
        optimizer="AdamW",
        lr0=0.001,
        workers=4,
        cache=True,
        hsv_v=HSV_V,
        name=RUN_NAME,
        device=0,              # GPU 없으면 "cpu"
    )
    elapsed_hr = (time.time() - start) / 3600

    metrics = train_result.results_dict
    row = {
        "date": time.strftime("%Y-%m-%d"),
        "run_name": RUN_NAME,
        "model": MODEL,
        "train_images": count_train_images(),
        "imgsz": IMGSZ,
        "batch": BATCH,
        "precision": round(metrics.get("metrics/precision(B)", 0), 3),
        "recall": round(metrics.get("metrics/recall(B)", 0), 3),
        "mAP50": round(metrics.get("metrics/mAP50(B)", 0), 3),
        "mAP50-95": round(metrics.get("metrics/mAP50-95(B)", 0), 3),
        "train_hours": round(elapsed_hr, 2),
        "note": NOTE,
    }
    append_log(row)

    print("\n=== 이번 실험 결과 (자동 기록됨) ===")
    for k, v in row.items():
        print(f"{k}: {v}")
    print(f"\n기록 파일: {LOG_CSV}")
    print_comparison_table()

    print(
        "\n[다음 확인 단계] check_confidence.py의 모델 경로를 이번에 나온 "
        f"runs/detect/{RUN_NAME}/weights/best.pt 로 바꿔서 다시 돌려보고, "
        "plate_detect_m_lowlight 결과(평균 0.916)보다 더 좋아졌는지 확인해보세요."
    )
