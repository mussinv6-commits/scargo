"""
YOLO 실험 실행 + 결과 자동 기록 스크립트
- 아래 "실험 설정" 부분만 바꿔서 실행하면 학습이 끝난 뒤 결과가 자동으로
  experiments_log.csv 에 한 줄 추가되고, 전체 비교 표가 콘솔에 출력됩니다.
- train_strict.py 처럼 수동으로 결과를 옮겨 적지 않아도 되도록 만든 버전입니다.

실행: python run_experiment.py
"""
import os
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import csv
import time
from pathlib import Path

from ultralytics import YOLO

# ================= 실험 설정 (다음 실험 시 여기만 바꿔서 사용) =================
RUN_NAME = "plate_detect_next"     # runs/detect/<RUN_NAME> 에 결과 저장 (기존 이름과 겹치지 않게)
MODEL = "yolo11l.pt"               # 사용할 모델 가중치
IMGSZ = 960
BATCH = 4
NOTE = ""                          # 이번 실험에서 바꾼 점/의도를 한 줄로 적어두면 표에 같이 기록됨
# ==============================================================================

PROJECT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
DATA_YAML = PROJECT_DIR / "data.yaml"
TRAIN_IMG_DIR = PROJECT_DIR / "train" / "images"
LOG_CSV = PROJECT_DIR / "experiments_log.csv"

FIELDNAMES = [
    "date", "run_name", "model", "train_images", "imgsz", "batch",
    "precision", "recall", "mAP50", "mAP50-95", "train_hours", "note",
]


def count_train_images():
    if not TRAIN_IMG_DIR.exists():
        return ""
    exts = {".jpg", ".jpeg", ".png", ".bmp"}
    return sum(1 for p in TRAIN_IMG_DIR.iterdir() if p.suffix.lower() in exts)


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
    header = f"{'run_name':<20}{'model':<26}{'imgsz':>7}{'mAP50':>8}{'mAP50-95':>10}"
    print(header)
    print("-" * len(header))
    for r in rows:
        print(
            f"{r['run_name']:<20}{r['model']:<26}{r.get('imgsz',''):>7}"
            f"{r.get('mAP50',''):>8}{r.get('mAP50-95',''):>10}"
        )


if __name__ == "__main__":
    model = YOLO(MODEL)

    start = time.time()
    train_result = model.train(
        data=str(DATA_YAML),
        epochs=100,          # 100 epoch 기준으로 변경 (run-17: patience=30으로 109에서 조기종료, mAP는 run-16보다 소폭 하락)
        patience=30,        # 30 epoch 개선 없으면 조기 종료
        imgsz=IMGSZ,
        batch=BATCH,        # autobatch(-1)는 오작동 이력 있어 항상 명시값 사용
        box=15.0,           # 박스 위치 손실 가중치 (기본 7.5)
        cos_lr=True,
        close_mosaic=20,
        optimizer="AdamW",
        lr0=0.001,
        workers=4,
        cache=True,
        name=RUN_NAME,
        device=0,           # GPU 없으면 "cpu"
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
