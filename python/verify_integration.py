# -*- coding: utf-8 -*-
"""
verify_integration.py
====================================================
plate_detector_gui.py에 실제로 통합된 plate_ocr_crnn.py(CRNNRecognizer)가,
학습 때 측정했던 정확도(CER 0.085 / 완전 일치율 73.5%)를 그대로 내는지 확인하는
검증 스크립트.

가상(합성) 번호판이 아니라, train_ocr_recognizer.py가 실제로 썼던 것과 동일한
196장의 진짜 검증 사진(트럭에서 잘라낸 crop)으로 테스트함 - CRNNRecognizer는
앱이 실제로 import해서 쓰는 바로 그 클래스이므로, 여기서 나오는 숫자가 곧
"통합이 제대로 됐는지"에 대한 정직한 답임.

실행 방법
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    python verify_integration.py

출력되는 내용 전체를 그대로 캡처해서 보내주세요.
"""
import csv
import random
from pathlib import Path

import numpy as np
import cv2

from plate_ocr_crnn import CRNNRecognizer

PROJECT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
DATA_DIR = PROJECT_DIR / "ocr_train_data"
CROPS_DIR = DATA_DIR / "crops"
LABELS_CSV = DATA_DIR / "labels.csv"

OUT_DIR = PROJECT_DIR / "ocr_model"
BEST_MODEL_PATH = OUT_DIR / "best_recognizer.pth"
CHARS_JSON_PATH = OUT_DIR / "chars.json"

VAL_RATIO = 0.1
SEED = 42  # 학습 스크립트(train_ocr_recognizer.py)와 동일한 시드
           # -> 그때 떼어놨던 검증 196장을 그대로 다시 뽑아냄


def imread_unicode(path: Path):
    data = np.fromfile(str(path), dtype=np.uint8)
    if data.size == 0:
        return None
    return cv2.imdecode(data, cv2.IMREAD_COLOR)


def char_error_rate(pred: str, gt: str) -> float:
    """편집거리 기반 문자 오류율 (Levenshtein distance / 정답 길이)."""
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
    random.seed(SEED)

    with open(LABELS_CSV, "r", encoding="utf-8-sig") as f:
        rows = [(r["filename"], r["text"]) for r in csv.DictReader(f)]

    # 학습 스크립트와 동일한 시드로 shuffle -> 그때의 검증 196장을 그대로 재현
    random.shuffle(rows)
    n_val = max(1, int(len(rows) * VAL_RATIO))
    val_rows = rows[:n_val]
    print(f"검증셋 재현: {len(val_rows)}장 (train_ocr_recognizer.py와 동일한 시드)\n")

    # plate_detector_gui.py가 실제로 쓰는 것과 완전히 같은 클래스/가중치.
    recognizer = CRNNRecognizer()
    recognizer.load(BEST_MODEL_PATH, CHARS_JSON_PATH)
    print("CRNN 로드 완료 - 앱에서 쓰는 것과 동일한 plate_ocr_crnn.CRNNRecognizer\n")

    # tta=False(기본 추론)와 tta=True(앱에서 정지 이미지일 때 실제로 켜는 옵션)를
    # 같은 196장에 대해 둘 다 돌려서 TTA가 실제로 얼마나 개선을 주는지 수치로 비교함
    # - 지금까지는 코드에만 있고 이 검증 스크립트엔 반영이 안 돼 있어서 "TTA를 켜면
    # 실제로 얼마나 좋아지는지"를 직접 확인한 적이 없었음. TTA는 변형 5개를 다 돌리므로
    # 기본 추론보다 몇 배 느림 - 196장 정도라 부담 없지만, 훨씬 큰 셋에서는 시간이 늘어남.
    # 4가지 조합(greedy/beam x TTA끔/켬)을 같은 196장에 대해 전부 비교함 - beam search는
    # 재학습 없이 추론 방식만 바꿔서 정확도를 끌어올릴 수 있는지 보려는 것.
    stats = {k: [0.0, 0] for k in ("greedy", "beam", "greedy_tta", "beam_tta")}
    results = []

    for filename, gt in val_rows:
        img = imread_unicode(CROPS_DIR / filename)
        if img is None:
            continue
        # 학습용 crop은 이미 번호판 주변 여유를 두고 잘려있는 이미지라, 앱의
        # _padded_crop(YOLO 박스에 12~18% 여유를 더 주는 단계)을 다시 거칠 필요 없이
        # recognizer.recognize()에 곧바로 넣음 - 앱에서도 _padded_crop을 거친 크롭을
        # 똑같이 recognize()에 그대로 넘기므로 인터페이스는 동일함.
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
    print(f"=== 결과 ({n}장) ===")
    labels = {
        "greedy": "[greedy, TTA 끔] (기존 방식)", "beam": "[beam,   TTA 끔]",
        "greedy_tta": "[greedy, TTA 켬]", "beam_tta": "[beam,   TTA 켬]",
    }
    for key in ("greedy", "beam", "greedy_tta", "beam_tta"):
        cer_sum, correct = stats[key]
        print(f"{labels[key]}  평균 CER: {cer_sum / n:.3f}  완전 일치율: {correct / n:.3f} ({correct}/{n})")
    print(
        "\n(학습 때 기록: CER 0.085 / 완전 일치율 0.735 - [greedy, TTA 끔] 값이 이와 비슷하게 "
        "나와야 통합이 제대로 된 것. 다른 3가지 조합 중 더 좋은 게 있으면 그걸로 앱 기본값을 "
        "바꾸는 걸 고려)"
    )

    results.sort(key=lambda t: -t[0])
    print("\n=== CER(greedy, TTA 끔 기준)이 가장 나쁜 예시 15개 (통합 과정에서 문제가 있다면 여기서 드러남) ===")
    for cer, filename, gt, pred, conf, pred_beam, conf_beam in results[:15]:
        conf_s = f"{conf:.2f}" if conf is not None else "?"
        conf_beam_s = f"{conf_beam:.2f}" if conf_beam is not None else "?"
        print(f"  정답: {gt:12s} 예측: {pred:12s}(conf {conf_s}) "
              f"beam예측: {pred_beam:12s}(conf {conf_beam_s})  CER: {cer:.2f}   ({filename})")

    # ---- CRNN<->EasyOCR 폴백 임계값(plate_detector_gui.py의 crnn_conf_threshold, 현재
    # 0.5) 재튜닝을 위한 데이터: 임계값 후보별로 "그 확신도를 넘긴 샘플들만 봤을 때"
    # CRNN 결과가 실제로 얼마나 믿을 만한지 보여줌. 이 비율이 EasyOCR 자체 정확도
    # (약 50~60%대)보다 낮은 지점이 있다면, 그 임계값은 너무 낮게 잡혀 있다는 뜻 -
    # 감으로 새 숫자를 정하지 않고 이 표를 보고 데이터 기반으로 정하도록 함.
    print("\n=== 폴백 확신도 임계값별 CRNN 신뢰도 (TTA 끔 기준) ===")
    print(f"{'임계값':>6} {'통과 샘플':>10} {'통과 비율':>10} {'그 안에서 완전일치율':>18}")
    for th in (0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9):
        passed = [(gt, pred) for cer, filename, gt, pred, conf, *_rest in results if conf is not None and conf >= th]
        if not passed:
            print(f"{th:>6.1f} {0:>10} {'0.0%':>10} {'-':>18}")
            continue
        correct = sum(1 for gt, pred in passed if gt == pred)
        print(f"{th:>6.1f} {len(passed):>10} {len(passed)/n*100:>9.1f}% {correct/len(passed)*100:>17.1f}%")
    print(
        "(참고: 현재 앱의 crnn_conf_threshold=0.5. 여기서 완전일치율이 EasyOCR 자체 정확도인 "
        "약 50~60%대보다 낮게 나오는 임계값 구간이 있다면, 그 지점까진 임계값을 올리는 게 "
        "이득이라는 뜻)"
    )

    # ---- greedy conf vs beam conf: 어느 쪽이 "맞았다/틀렸다"를 더 잘 구분해주는 신뢰도인지
    # 비교. worst-15 목록에서 beam conf가 틀린 예측에서 유독 확 낮아지는 패턴이 보였는데,
    # 195장 전체로 확인해서 우연이 아닌지 검증함. 만약 beam conf가 정말 더 잘 분리한다면
    # 폴백 판단(crnn_conf_threshold)을 greedy conf 대신 beam conf 기준으로 바꾸는 게
    # 재학습 없이 바로 적용 가능한 개선이 됨.
    correct_g_conf, wrong_g_conf = [], []
    correct_b_conf, wrong_b_conf = [], []
    for cer, filename, gt, pred, conf, pred_beam, conf_beam in results:
        if conf is not None:
            (correct_g_conf if pred == gt else wrong_g_conf).append(conf)
        if conf_beam is not None:
            (correct_b_conf if pred_beam == gt else wrong_b_conf).append(conf_beam)

    def avg(xs):
        return sum(xs) / len(xs) if xs else float("nan")

    print("\n=== greedy conf vs beam conf: 정답/오답 분리력 비교 ===")
    print(f"{'':10s} {'맞은 것 평균conf':>16} {'틀린 것 평균conf':>16} {'차이(클수록 좋음)':>18}")
    print(f"{'greedy':10s} {avg(correct_g_conf):>16.3f} {avg(wrong_g_conf):>16.3f} {avg(correct_g_conf)-avg(wrong_g_conf):>18.3f}")
    print(f"{'beam':10s} {avg(correct_b_conf):>16.3f} {avg(wrong_b_conf):>16.3f} {avg(correct_b_conf)-avg(wrong_b_conf):>18.3f}")
    print("(차이가 클수록 그 conf 값으로 맞았는지/틀렸는지 구분이 잘 된다는 뜻)")

    print("\n=== 폴백 확신도 임계값별 CRNN 신뢰도 (beam conf 기준) ===")
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
        "(beam conf 기준 표. greedy conf 표와 비교했을 때 같은 통과 비율에서 완전일치율이 "
        "더 높게 나온다면, 앱의 폴백 판단을 beam conf로 바꾸는 게 이득이라는 뜻 - beam 예측"
        "텍스트 자체는 지금까지 greedy와 거의 항상 같았으므로, 텍스트는 그대로 두고 신뢰도 "
        "판단 기준만 beam conf로 바꾸는 방안도 가능함)"
    )


if __name__ == "__main__":
    main()
