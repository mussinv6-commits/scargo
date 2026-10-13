# -*- coding: utf-8 -*-
"""
verify_integration_attn.py
====================================================
[실험판] train_ocr_recognizer_attn.py + plate_ocr_crnn_attn.py(BiLSTM+self-attention
구조)의 검증 195장 기준 정확도를 확인하는 스크립트. verify_integration.py(기존)와
완전히 같은 방식이고, 대상만 실험판 모델/모듈로 바꿨습니다.

기존 기록(BiLSTM 단독, 지금 앱이 실제로 씀): CER 0.074 / 완전 일치율 74.9%
이 실험판이 그보다 나아야 앱에 통합할 가치가 있습니다.

실행 방법
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    python verify_integration_attn.py
"""
import csv
import random
from pathlib import Path

import numpy as np
import cv2

from plate_ocr_crnn_attn import CRNNRecognizer

PROJECT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
DATA_DIR = PROJECT_DIR / "ocr_train_data"
CROPS_DIR = DATA_DIR / "crops"
LABELS_CSV = DATA_DIR / "labels.csv"

OUT_DIR = PROJECT_DIR / "ocr_model_attn"
BEST_MODEL_PATH = OUT_DIR / "best_recognizer_attn.pth"
CHARS_JSON_PATH = OUT_DIR / "chars_attn.json"

VAL_RATIO = 0.1
SEED = 42  # train_ocr_recognizer_attn.py와 동일한 시드 -> 같은 검증셋 재현


def imread_unicode(path: Path):
    data = np.fromfile(str(path), dtype=np.uint8)
    if data.size == 0:
        return None
    return cv2.imdecode(data, cv2.IMREAD_COLOR)


def char_error_rate(pred: str, gt: str) -> float:
    if len(gt) == 0:
        return 0.0 if len(pred) == 0 else 1.0
    dp = list(range(len(gt) + 1))
    for i, pc in enumerate(pred, start=1):
        prev = dp[0]
        dp[0] = i
        for j, gc in enumerate(gt, start=1):
            cur = dp[j]
            dp[j] = min(dp[j] + 1, dp[j - 1] + 1, prev + (0 if pc == gc else 1))
            prev = cur
    return dp[len(gt)] / len(gt)


def main():
    if not BEST_MODEL_PATH.exists():
        print(f"[오류] {BEST_MODEL_PATH} 가 없습니다. train_ocr_recognizer_attn.py를 먼저 실행하세요.")
        return

    random.seed(SEED)

    with open(LABELS_CSV, "r", encoding="utf-8-sig") as f:
        rows = [(r["filename"], r["text"]) for r in csv.DictReader(f)]

    random.shuffle(rows)
    n_val = max(1, int(len(rows) * VAL_RATIO))
    val_rows = rows[:n_val]
    print(f"검증셋 재현: {len(val_rows)}장 (train_ocr_recognizer_attn.py와 동일한 시드)\n")

    recognizer = CRNNRecognizer()
    recognizer.load(BEST_MODEL_PATH, CHARS_JSON_PATH)
    print("[실험판] CRNN(BiLSTM+self-attention) 로드 완료\n")

    stats = {k: [0.0, 0] for k in ("greedy", "beam", "greedy_tta", "beam_tta")}
    results = []

    for filename, gt in val_rows:
        img = imread_unicode(CROPS_DIR / filename)
        if img is None:
            continue
        text, conf = recognizer.recognize(img)
        text_beam, conf_beam = recognizer.recognize(img, decode="beam")
        text_tta, conf_tta = recognizer.recognize(img, tta=True)
        text_beam_tta, conf_beam_tta = recognizer.recognize(img, tta=True, decode="beam")

        for key, (t, _c) in (
            ("greedy", (text, conf)), ("beam", (text_beam, conf_beam)),
            ("greedy_tta", (text_tta, conf_tta)), ("beam_tta", (text_beam_tta, conf_beam_tta)),
        ):
            cer_sum, correct = stats[key]
            cer_sum += char_error_rate(t, gt)
            correct += 1 if t == gt else 0
            stats[key] = [cer_sum, correct]

        cer = char_error_rate(text, gt)
        results.append((cer, filename, gt, text, conf, text_beam, conf_beam))

    n = len(results)
    print(f"=== [실험판] 결과 ({n}장) ===")
    labels = {
        "greedy": "[greedy, TTA 끔]", "beam": "[beam,   TTA 끔]",
        "greedy_tta": "[greedy, TTA 켬]", "beam_tta": "[beam,   TTA 켬]",
    }
    for key in ("greedy", "beam", "greedy_tta", "beam_tta"):
        cer_sum, correct = stats[key]
        print(f"{labels[key]}  평균 CER: {cer_sum / n:.3f}  완전 일치율: {correct / n:.3f} ({correct}/{n})")
    print(
        "\n(비교 기준 - 기존 BiLSTM단독(지금 앱): CER 0.074 / 완전 일치율 0.749. "
        "위 [greedy, TTA 끔] 값이 이보다 낮은 CER/높은 일치율이 나와야 attention 레이어가 "
        "실제로 도움이 됐다는 뜻)"
    )

    results.sort(key=lambda t: -t[0])
    print("\n=== CER이 가장 나쁜 예시 15개 ===")
    for cer, filename, gt, pred, conf, pred_beam, conf_beam in results[:15]:
        conf_s = f"{conf:.2f}" if conf is not None else "?"
        conf_beam_s = f"{conf_beam:.2f}" if conf_beam is not None else "?"
        print(f"  정답: {gt:12s} 예측: {pred:12s}(conf {conf_s}) "
              f"beam예측: {pred_beam:12s}(conf {conf_beam_s})  CER: {cer:.2f}   ({filename})")

    # ---- greedy conf vs beam conf: 정답/오답 분리력 비교. 기존 BiLSTM 단독판에서는
    # beam conf가 greedy conf보다 훨씬 뚜렷하게 정답/오답을 갈라놓아서(차이 0.423 vs
    # 0.102) 앱의 폴백 판단 기준을 beam conf로 바꿨음. self-attention층을 추가한
    # 이 모델도 confidence 분포가 달라졌을 수 있으니 같은 방식으로 다시 확인함.
    correct_g_conf, wrong_g_conf = [], []
    correct_b_conf, wrong_b_conf = [], []
    for cer, filename, gt, pred, conf, pred_beam, conf_beam in results:
        if conf is not None:
            (correct_g_conf if pred == gt else wrong_g_conf).append(conf)
        if conf_beam is not None:
            (correct_b_conf if pred_beam == gt else wrong_b_conf).append(conf_beam)

    def avg(xs):
        return sum(xs) / len(xs) if xs else float("nan")

    print("\n=== [실험판] greedy conf vs beam conf: 정답/오답 분리력 비교 ===")
    print(f"{'':10s} {'맞은 것 평균conf':>16} {'틀린 것 평균conf':>16} {'차이(클수록 좋음)':>18}")
    print(f"{'greedy':10s} {avg(correct_g_conf):>16.3f} {avg(wrong_g_conf):>16.3f} {avg(correct_g_conf)-avg(wrong_g_conf):>18.3f}")
    print(f"{'beam':10s} {avg(correct_b_conf):>16.3f} {avg(wrong_b_conf):>16.3f} {avg(correct_b_conf)-avg(wrong_b_conf):>18.3f}")
    print(
        "(참고 - 기존 BiLSTM 단독판 기록: greedy 차이 0.102 / beam 차이 0.423. "
        "beam 차이가 여기서도 크게 나오면 지금 앱처럼 beam conf 기준을 그대로 쓰면 됨)"
    )

    # ---- CRNN<->EasyOCR 폴백 임계값(plate_detector_gui.py의 crnn_conf_threshold,
    # 현재 0.3 - 기존 BiLSTM 단독판에서 derive한 값을 그대로 이어받아 쓰고 있음)
    # 재조정을 위한 데이터: 이 self-attention 모델 자체의 confidence 분포로 다시
    # 뽑은 표. 감으로 새 숫자를 정하지 않고 이 표를 보고 데이터 기반으로 정하도록 함.
    print("\n=== [실험판] 폴백 확신도 임계값별 CRNN 신뢰도 (beam conf 기준) ===")
    print(f"{'임계값':>6} {'통과 샘플':>10} {'통과 비율':>10} {'그 안에서 완전일치율':>18}")
    for th in (0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9):
        passed = [(gt, pred_beam) for cer, filename, gt, pred, conf, pred_beam, conf_beam in results
                  if conf_beam is not None and conf_beam >= th]
        if not passed:
            print(f"{th:>6.1f} {0:>10} {'0.0%':>10} {'-':>18}")
            continue
        correct = sum(1 for gt, pred_beam in passed if gt == pred_beam)
        print(f"{th:>6.1f} {len(passed):>10} {len(passed)/n*100:>9.1f}% {correct/len(passed)*100:>17.1f}%")
    print(
        "\n(참고 - 기존 BiLSTM 단독판 기록: 임계값 0.3에서 통과율 91.3%/완전일치율 82.0%. "
        "이 표에서 현재 앱이 쓰는 0.3 지점의 통과율/완전일치율을 확인하고, EasyOCR 자체 "
        "정확도(약 50~60%대)보다 낮아지는 지점이 있으면 그 밑으로는 임계값을 올려야 함. "
        "가장 균형 잡힌 지점(통과율을 너무 깎지 않으면서 완전일치율이 충분히 높은 임계값)을 "
        "새 crnn_conf_threshold 값으로 plate_detector_gui.py에 반영하면 됨)"
    )


if __name__ == "__main__":
    main()
