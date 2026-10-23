"""
YOLO 추론 속도 벤치마크 (영상 전체를 돌리지 않고, 설정별 속도와 탐지 수를 한 번에 비교한다)

gate_pipeline.py 와 같은 방식(ROI+여백만 잘라서 입력)으로, FP32/FP16 x 입력 크기 조합의
'프레임당 추론 시간'과 '탐지된 번호판 수'를 비교한다.
탐지 수가 기준(첫 줄)과 같거나 비슷하면서 시간이 확 줄어드는 조합을 gate_pipeline.py 에 적용한다.

실행 예:
  python bench_yolo.py
  python bench_yolo.py --video C:\\plate_dataset\\test_front_clip.mp4 --roi 0.25,0.72,0.85,0.98
"""
import time
import logging
import argparse
import subprocess

import cv2
import numpy as np


def gpu_status():
    """nvidia-smi로 GPU 클럭/전력 상태를 한 줄로 가져온다 (없으면 빈 문자열). 부하 중에 호출하면 '실제 동작 클럭'을 볼 수 있다."""
    try:
        out = subprocess.run(['nvidia-smi', '--query-gpu=name,pstate,clocks.sm,clocks.max.sm,power.draw,power.limit,utilization.gpu',
                              '--format=csv,noheader'], capture_output=True, text=True, timeout=5).stdout.strip()
        return out
    except Exception:
        return ''


def main():
    logging.getLogger('ultralytics').setLevel(logging.ERROR)      # 'half is deprecated' 경고 반복 출력 숨김
    ap = argparse.ArgumentParser()
    ap.add_argument('--video', default=r'C:\plate_dataset\test_front_2.mp4')
    ap.add_argument('--yolo', default=r'C:\plate_dataset\runs\detect\runs\lp_detect\yolo26n_v2\weights\best.pt')
    ap.add_argument('--roi', default='0.25,0.72,0.85,0.98', help='gate_pipeline.py 의 ROI_NORM 과 같은 값 (왼쪽,위,오른쪽,아래 비율)')
    ap.add_argument('--margin', type=float, default=0.10, help='DET_MARGIN')
    ap.add_argument('--base-imgsz', type=int, default=960, help='YOLO_IMGSZ (전체 화면 기준)')
    ap.add_argument('--conf', type=float, default=0.5)
    ap.add_argument('--samples', type=int, default=60, help='영상에서 고르게 뽑을 프레임 수 (탐지 수 비교용)')
    ap.add_argument('--reps', type=int, default=60, help='속도 측정 반복 횟수')
    args = ap.parse_args()

    import torch
    from ultralytics import YOLO
    use_cuda = torch.cuda.is_available()
    dev = 0 if use_cuda else 'cpu'
    print(f"장치: {torch.cuda.get_device_name(0) if use_cuda else 'CPU (GPU를 못 찾음!)'}")

    cap = cv2.VideoCapture(args.video)
    n_total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    W, H = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)), int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    r = [float(v) for v in args.roi.split(',')]
    roi = (int(r[0] * W), int(r[1] * H), int(r[2] * W), int(r[3] * H))
    mx, my = int(args.margin * W), int(args.margin * H)
    det = (max(0, roi[0] - mx), max(0, roi[1] - my), min(W, roi[2] + mx), min(H, roi[3] + my))
    print(f"영상 {W}x{H}, YOLO 입력 영역 x {det[0]}~{det[2]}, y {det[1]}~{det[3]}")

    crops = []
    for k in range(args.samples):
        cap.set(cv2.CAP_PROP_POS_FRAMES, int(k * n_total / args.samples))
        ok, fr = cap.read()
        if ok:
            crops.append(np.ascontiguousarray(fr[det[1]:det[3], det[0]:det[2]]))
    cap.release()
    if not crops:
        print("프레임을 읽지 못했습니다.")
        return

    model = YOLO(args.yolo)
    cw = det[2] - det[0]

    def eff(base):
        return max(32, int(round(base * cw / W / 32.0)) * 32)

    configs = []
    for half in (False, True):
        for base in (args.base_imgsz, 1280, 1600, 800, 640):
            if (half, base) not in [(h, b) for h, b, _ in configs]:
                configs.append((half, base, eff(base)))
    if not use_cuda:
        configs = [c for c in configs if not c[0]]      # CPU에서는 FP16 비교 생략

    print(f"\n{'정밀도':<7}{'기준imgsz':>9}{'실제imgsz':>10}{'추론ms':>9}{'전체ms':>9}{'탐지수':>8}")
    base_count = None
    sample = crops[len(crops) // 2]
    for half, base, im in configs:
        h_flag = bool(half and use_cuda)
        for _ in range(5):                                # 워밍업
            model.predict(sample, conf=args.conf, imgsz=im, device=dev, half=h_flag, verbose=False)
        inf, tot = [], []
        for _ in range(args.reps):
            t = time.perf_counter()
            res = model.predict(sample, conf=args.conf, imgsz=im, device=dev, half=h_flag, verbose=False)
            if use_cuda:
                torch.cuda.synchronize()
            tot.append((time.perf_counter() - t) * 1000.0)
            inf.append(res[0].speed.get('inference', 0.0))
        gs = gpu_status()
        count = 0
        for c in crops:
            count += len(model.predict(c, conf=args.conf, imgsz=im, device=dev, half=h_flag, verbose=False)[0].boxes)
        if base_count is None:
            base_count = count
        note = '' if count >= base_count * 0.97 else '  <- 탐지가 줄어듦'
        print(f"{'FP16' if h_flag else 'FP32':<7}{base:>9}{im:>10}{np.mean(inf):>9.1f}{np.mean(tot):>9.1f}{count:>8}{note}")
        if gs and half is False and base == args.base_imgsz:
            print(f"        GPU 상태(측정 직후): {gs}   (name, pstate, 현재SM클럭, 최대SM클럭, 소비전력, 전력한도, 사용률)")
    print(f"\n탐지수는 영상에서 고르게 뽑은 {len(crops)}프레임 합계입니다. 첫 줄(FP32, 기준 크기)과 비슷하면서 추론ms가 작은 줄을 고르세요.")
    print("고른 설정: FP16이면 gate_pipeline.py 의 YOLO_HALF = True, 크기는 YOLO_IMGSZ 를 '기준imgsz' 값으로.")


if __name__ == '__main__':
    main()