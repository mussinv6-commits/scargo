# -*- coding: utf-8 -*-
"""
화물차_merged\images 전체(1961장)를 best.pt로 돌려서 YOLO 신뢰도(confidence)를 확인.
- 각 이미지 최고 confidence 기록
- 평균/중앙값/최소/최대 + 구간별 분포 출력
- 신뢰도 낮은 이미지 목록은 파일로 저장 (검수용)
"""
import os
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

from ultralytics import YOLO

SRC_DIR = r"C:\Users\user\Desktop\화물차_merged\images"
OUT_TXT = r"C:\Users\user\Desktop\화물차_merged\confidence_report_expanded.txt"

# 저조도(야간/역광) 개선 실험(hsv_v 0.4->0.7) 효과를 확인하기 위해 대조군으로 삼은
# 기존 최저 확신도 3장 - plate_detect_m 기준 값과 이번 결과를 바로 비교해서 출력함
BASELINE_CONF = {
    "경남14노6787.jpeg": 0.057,
    "경남14노6692.jpeg": 0.059,
    "경남06모7783.jpeg": 0.065,
}

if __name__ == "__main__":  # Windows에서 필수
    # B안(데이터 보강) 실험 결과 - 실제 체크포인트가 쌓인 폴더는 "-2" 접미사가 붙은 쪽임
    # (data.yaml 경로 오류로 즉시 실패하며 빈 채로 남은 plate_detect_m_expanded와는 다른 폴더).
    model = YOLO(r"C:\Users\user\Desktop\project.v4i.yolov8\runs\detect\plate_detect_m_expanded-2\weights\best.pt")

    results = model.predict(
        source=SRC_DIR,
        imgsz=960,          # 실제 학습에 쓰는 해상도 기준으로 확인
        conf=0.01,          # 낮게 잡아서 실제 confidence 값을 그대로 확인
        save=False,
        stream=True,        # 1961장이라 메모리 절약을 위해 스트림 모드
        device=0,           # GPU 없으면 "cpu"
        verbose=False,
    )

    records = []  # (파일명, 최고 confidence 또는 None)
    for r in results:
        name = os.path.basename(r.path)
        if len(r.boxes) == 0:
            records.append((name, None))
        else:
            best_conf = float(r.boxes.conf.max())
            records.append((name, best_conf))

    detected = [c for _, c in records if c is not None]
    zero_det = [n for n, c in records if c is None]

    detected.sort()
    n = len(detected)

    def pct(p):
        if n == 0:
            return 0.0
        idx = min(n - 1, int(n * p))
        return detected[idx]

    print(f"\n전체 이미지: {len(records)}장")
    print(f"검출됨: {n}장 / 검출 안됨: {len(zero_det)}장")
    if n:
        avg = sum(detected) / n
        print(f"평균 confidence: {avg:.3f}  (plate_detect_m_lowlight 기준값 0.916과 비교)")
        print(f"중앙값: {pct(0.5):.3f}")
        print(f"최소: {detected[0]:.3f} / 최대: {detected[-1]:.3f}")

        buckets = {"0.9+": 0, "0.7~0.9": 0, "0.5~0.7": 0, "0.3~0.5": 0, "0.3미만": 0}
        for c in detected:
            if c >= 0.9:
                buckets["0.9+"] += 1
            elif c >= 0.7:
                buckets["0.7~0.9"] += 1
            elif c >= 0.5:
                buckets["0.5~0.7"] += 1
            elif c >= 0.3:
                buckets["0.3~0.5"] += 1
            else:
                buckets["0.3미만"] += 1

        print("\n구간별 분포:")
        for k, v in buckets.items():
            print(f"  {k}: {v}장 ({v/n*100:.1f}%)")

    # 신뢰도 낮은 순으로 정렬해서 저장 (검수용, 상위 100개 + 미검출 전체)
    low_conf_sorted = sorted([(n_, c) for n_, c in records if c is not None], key=lambda x: x[1])
    with open(OUT_TXT, "w", encoding="utf-8") as f:
        f.write(f"전체 {len(records)}장 / 검출 {n}장 / 미검출 {len(zero_det)}장\n")
        if n:
            f.write(f"평균 {sum(detected)/n:.3f} / 중앙값 {pct(0.5):.3f} / 최소 {detected[0]:.3f} / 최대 {detected[-1]:.3f}\n\n")
        f.write("=== confidence 낮은 순 (하위 100개) ===\n")
        for name, c in low_conf_sorted[:100]:
            f.write(f"{c:.3f}  {name}\n")
        if zero_det:
            f.write("\n=== 이번엔 아예 미검출 ===\n")
            for name in zero_det:
                f.write(name + "\n")

    print(f"\n상세 리포트 저장: {OUT_TXT}")

    # 저조도 개선 실험(hsv_v 상향)의 실제 성공 기준 - 아래 3장이 눈에 띄게 올랐는지가 핵심
    conf_by_name = dict(records)
    print("\n=== 저조도 대조군 3장 비교 (plate_detect_m 기존값 -> plate_detect_m_lowlight) ===")
    for name, before in BASELINE_CONF.items():
        after = conf_by_name.get(name)
        if after is None:
            print(f"  {name}: {before:.3f} -> 검출 안 됨(!)")
        else:
            diff = after - before
            arrow = "UP" if diff > 0.01 else ("DOWN" if diff < -0.01 else "동일")
            print(f"  {name}: {before:.3f} -> {after:.3f}  ({diff:+.3f}, {arrow})")
