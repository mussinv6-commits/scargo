# -*- coding: utf-8 -*-
"""
resume_experiment_expanded.py
====================================================
run_experiment_expanded.py가 51 epoch 근처에서 중단된 걸 이어서 학습합니다.
처음부터 다시 돌리지 않고, 마지막으로 저장된 체크포인트(last.pt, 50 epoch까지 정상
저장됨)에서 이어갑니다.

====================================================
중단 원인 추정
====================================================
에러 메시지 자체는 tqdm 진행바 포맷팅에서 깨진 것으로, 코드/데이터 버그라기보다는
학습 도중 PC가 절전모드로 들어가면서 GPU 연결이 끊기고 그 배치 데이터가 손상되어
생긴 증상으로 보입니다 (자리를 비우신 시점과 정확히 겹침). 다시 돌리기 전에
절전모드를 꺼두는 걸 권장합니다:
    설정 > 시스템 > 전원 및 배터리 > 화면 및 절전 > "절전 모드"를 모두 "안 함"으로 변경
    (노트북이면 전원 옵션에서 "덮개를 닫을 때의 동작"도 "아무 작업 안 함"으로 변경 권장)

====================================================
실행 방법 (Anaconda Prompt)
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    set KMP_DUPLICATE_LIB_OK=TRUE
    python resume_experiment_expanded.py

끝나면 run_experiment_expanded.py와 동일하게 experiments_log.csv에 결과 한 줄이
추가되고 전체 실험 비교표가 출력됩니다.
====================================================
"""
import os

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import csv
import time
from pathlib import Path

from ultralytics import YOLO

RUN_NAME = "plate_detect_m_expanded"
# 실제로 51epoch까지 돌았던 폴더는 "plate_detect_m_expanded-2" 입니다.
# (data_expanded.yaml 경로를 고치기 전, 첫 실행이 즉시 실패하면서 폴더만 만들고
#  빈 채로 남은 "plate_detect_m_expanded"와는 다른 폴더입니다.)
ACTUAL_RUN_DIR_NAME = "plate_detect_m_expanded-2"
PROJECT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
LAST_CKPT = PROJECT_DIR / "runs" / "detect" / ACTUAL_RUN_DIR_NAME / "weights" / "last.pt"
TRAIN_IMG_DIR = PROJECT_DIR / "train" / "images"
EXTRA_IMG_DIR = Path(r"C:\Users\user\Desktop\추가학습_검수\images")
LOG_CSV = PROJECT_DIR / "experiments_log.csv"
NOTE = "B안 데이터 보강 - 추가학습_검수 237장(자동 초안 231 + 수동 6) 추가, hsv_v=0.7 유지 (51epoch 부근 중단 후 재개)"

FIELDNAMES = [
    "date", "run_name", "model", "train_images", "imgsz", "batch",
    "precision", "recall", "mAP50", "mAP50-95", "train_hours", "note",
]


def count_train_images():
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
    if not LAST_CKPT.exists():
        print(f"[오류] {LAST_CKPT} 를 찾을 수 없습니다. 중단된 학습의 체크포인트 경로가 맞는지 확인해주세요.")
        raise SystemExit(1)

    model = YOLO(str(LAST_CKPT))

    start = time.time()
    # resume=True는 중단 시점의 epoch/optimizer 상태와 원래 학습 설정(data.yaml, epochs,
    # patience, hsv_v 등)을 그대로 이어받습니다. 다른 인자를 추가로 주면 충돌할 수 있어
    # 일부러 넣지 않았습니다.
    train_result = model.train(resume=True)
    elapsed_hr = (time.time() - start) / 3600

    metrics = train_result.results_dict
    row = {
        "date": time.strftime("%Y-%m-%d"),
        "run_name": RUN_NAME,
        "model": "yolo11m.pt",
        "train_images": count_train_images(),
        "imgsz": 960,
        "batch": 6,
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
