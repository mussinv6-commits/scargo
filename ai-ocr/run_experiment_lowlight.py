# -*- coding: utf-8 -*-
"""
run_experiment_lowlight.py
====================================================
다음 학습 실험: "저조도(야간/역광/그림자) 대비 강화" 실험용 스크립트.
run_experiment.py를 그대로 베이스로 쓰되, 딱 한 가지 변수만 바꿔서 비교합니다
(run-16 -> run-17 실험도 모델 크기 하나만 바꿔서 비교했던 것과 같은 방식).

====================================================
왜 이 실험이 필요한가 (근거)
====================================================
confidence_report.txt(화물차_merged 1961장, best.pt=plate_detect_m 기준)에서
확신도가 가장 낮았던 이미지들을 하나씩 열어서 직접 확인해본 결과, 하위권 이미지
6장 중 6장 전부 공통점이 있었습니다: 전부 "저조도(야간/그림자/역광 글레어)"
사진이었습니다. 그중 3장은 심지어 이미 학습 데이터(train/images)에 포함돼
있었는데도 확신도가 0.057~0.077 수준으로 극히 낮았습니다:

  0.057  경남14노6787.jpeg  (학습 데이터 포함됨, 주황색 특수차량 번호판 + 그림자)
  0.059  경남14노6692.jpeg  (학습 데이터 포함됨, 주황색 특수차량 번호판 + 그림자)
  0.065  경남06모7783.jpeg  (학습 데이터 포함됨, 어두운 실내/터널 야간)
  0.077  경남81아1308.jpeg  (미학습, 어두운 화물칸 그림자)
  0.077  전남80바2849.jpeg  (미학습, 야간 저조도)
  0.121  경남14고8874.jpeg  (미학습, 야간 + 헤드라이트 역광 글레어)

즉 "모델을 더 키우면(run-17) 나아지나?"는 이미 시도해서 효과가 없었던 반면
(experiments_log.csv 참고 - run-16 대비 오히려 소폭 하락), 실제 실패 사례를
직접 까본 결과는 모델 용량 문제가 아니라 "저조도 조건에 특히 약하다"는
훨씬 구체적인 원인을 가리키고 있습니다. 이미 학습에 포함된 사진조차 확신도가
바닥인 걸 보면, 단순히 이런 사진을 더 넣는 것만으로는 부족하고, 저조도 조건에
대한 augmentation(밝기/명도 변형) 강도 자체를 올려서 모델이 어두운 조건의
변형을 더 적극적으로 보고 학습하게 만드는 게 우선입니다.

====================================================
이번 실험에서 바꾼 것 (딱 1가지)
====================================================
hsv_v (명도 랜덤 변형 강도): 0.4 -> 0.7
  - run-16과 완전히 동일한 데이터 · 동일한 나머지 하이퍼파라미터에서 이 값만
    올려서, 순수하게 "밝기 변형을 더 강하게 주면 저조도 사진 확신도가
    좋아지는가"만 확인하기 위함입니다 (한 번에 여러 변수를 바꾸면 뭐가
    효과 있었는지 알 수 없으므로, run-16/17 비교와 같은 원칙을 지켰습니다).
  - 모델은 run-17(yolo11l)이 아니라 run-16과 같은 yolo11m을 그대로 씀
    (모델을 키우는 건 이미 효과 없다고 확인됐으므로).

====================================================
실행 방법 (Anaconda Prompt, GPU 있는 환경 - base 환경 권장)
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    set KMP_DUPLICATE_LIB_OK=TRUE
    python run_experiment_lowlight.py

    끝나면 자동으로 experiments_log.csv에 결과 한 줄이 추가되고, 화면에
    전체 실험 비교표가 출력됩니다. run-16(현재 최고, mAP50-95=0.874)과
    비교해서 mAP가 비슷하거나 더 좋으면서 특히 confidence_report.txt로
    재검증했을 때 위에 나온 저조도 사진들의 확신도가 실제로 올랐는지
    확인하는 게 이번 실험의 진짜 성공 기준입니다 (mAP 숫자만으로는
    저조도 케이스 개선 여부가 안 보일 수 있음 - 전체 평균이라).

====================================================
"""
import os

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

import csv
import time
from pathlib import Path

from ultralytics import YOLO

# ================= 실험 설정 =================
RUN_NAME = "plate_detect_m_lowlight"   # runs/detect/plate_detect_m_lowlight 에 결과 저장
MODEL = "yolo11m.pt"                    # run-16과 동일 모델 (run-17에서 확인했듯 모델 확대는 효과 없었음)
IMGSZ = 960
BATCH = 6                               # run-16과 동일 (run-17의 batch=4는 VRAM 제약 때문이었을 뿐, 이번엔 불필요)
HSV_V = 0.7                             # <- 이번 실험의 유일한 변경점 (기본/run-16: 0.4)
NOTE = "저조도(야간/역광) 대비 개선 실험 - hsv_v 0.4->0.7, 나머지는 run-16과 동일"
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
    header = f"{'run_name':<24}{'model':<20}{'imgsz':>7}{'mAP50':>8}{'mAP50-95':>10}"
    print(header)
    print("-" * len(header))
    for r in rows:
        print(
            f"{r['run_name']:<24}{r['model']:<20}{r.get('imgsz',''):>7}"
            f"{r.get('mAP50',''):>8}{r.get('mAP50-95',''):>10}"
        )


if __name__ == "__main__":
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
        hsv_v=HSV_V,          # <- 유일한 변경점
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
        "\n[다음 확인 단계] check_confidence.py를 이번에 나온 "
        f"runs/detect/{RUN_NAME}/weights/best.pt 로 다시 돌려서, "
        "위 저조도 사진들의 확신도가 실제로 올랐는지 꼭 확인해보세요."
    )
