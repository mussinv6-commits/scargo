"""
번호판 크롭의 화질 개선/확대가 OCR 인식에 도움이 되는지 실험한다 (게이트 영상에서 확정 못 한 차량 대상).

준비: gate_pipeline.py 의 SAVE_READ_CROPS = True 로 영상을 한 번 돌리면
      C:\\plate_dataset\\gate_crops\\reads\\id0021_f01005.jpg 같은 크롭이 차량별로 저장된다.
      (같은 폴더의 gate_pipeline.py 설정(OCR 모델 경로 등)을 그대로 쓴다)

실행 예:
  python eval_enhance.py C:\\plate_dataset\\gate_crops\\reads --truth 21=충남90배5929 2=32마7284

  --truth  차량ID=실제번호 (영상을 보고 확인한 값). 여러 개 가능. 없는 차량은 건너뜀.
출력: 방법별로 '실제 번호와 정확히 일치한 읽기 수', '지역명/글자 부분 일치', 평균 신뢰도.
해석: 'base'(아무 처리 안 함)보다 일치 수가 뚜렷하게 늘어나는 방법만 파이프라인에 넣는다.
      늘어나지 않으면 해상도 자체가 부족한 것이라 화질 개선으로는 해결되지 않는다.
"""
import os
import re
import sys
import glob
import argparse

import cv2
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def imread(path):
    return cv2.imdecode(np.fromfile(path, dtype=np.uint8), cv2.IMREAD_COLOR)   # 한글 경로 대응


def unsharp(img, amount, sigma=1.2):
    blur = cv2.GaussianBlur(img, (0, 0), sigma)
    return cv2.addWeighted(img, 1 + amount, blur, -amount, 0)


def clahe(img, clip=2.0):
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
    c = cv2.createCLAHE(clipLimit=clip, tileGridSize=(4, 4))
    lab[..., 0] = c.apply(lab[..., 0])
    return cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)


def stretch(img):
    g = img.astype(np.float32)
    lo, hi = np.percentile(g, 2), np.percentile(g, 98)
    return np.clip((g - lo) / max(hi - lo, 1.0) * 255, 0, 255).astype(np.uint8)


def up2_lanczos(img):
    return cv2.resize(img, None, fx=2, fy=2, interpolation=cv2.INTER_LANCZOS4)


def up3_cubic_sharp(img):
    return unsharp(cv2.resize(img, None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC), 1.0, 2.0)


def shrink(img, m=0.03):
    h, w = img.shape[:2]
    dy, dx = int(h * m), int(w * m)
    return img[dy:h - dy, dx:w - dx] if h - 2 * dy > 4 and w - 2 * dx > 4 else img


def grow(img, m=0.03):
    h, w = img.shape[:2]
    dy, dx = max(1, int(h * m)), max(1, int(w * m))
    return cv2.copyMakeBorder(img, dy, dy, dx, dx, cv2.BORDER_REPLICATE)


def denoise(img):
    return cv2.fastNlMeansDenoisingColored(img, None, 5, 5, 5, 15)


VARIANTS = {
    'base': lambda im: im,
    '확대2x(lanczos)': up2_lanczos,
    '확대3x+샤픈': up3_cubic_sharp,
    '샤픈(약)': lambda im: unsharp(im, 0.8),
    '샤픈(강)': lambda im: unsharp(im, 1.8),
    'CLAHE': clahe,
    '대비늘리기': stretch,
    '노이즈제거': denoise,
    '샤픈+CLAHE': lambda im: clahe(unsharp(im, 0.8)),
    '테두리3%깎기': shrink,
    '테두리3%넓히기': grow,
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('folder')
    ap.add_argument('--truth', nargs='+', required=True, help='차량ID=실제번호 (예: 21=충남90배5929)')
    args = ap.parse_args()

    truth = {}
    for t in args.truth:
        k, v = t.split('=', 1)
        truth[int(k)] = v

    from gate_pipeline import run_ocr      # gate_pipeline.py 의 모델/전처리를 그대로 사용

    files = {}
    for p in sorted(glob.glob(os.path.join(args.folder, '*.jpg'))):
        m = re.match(r'id(\d+)_f(\d+)', os.path.basename(p))
        if m and int(m.group(1)) in truth:
            files.setdefault(int(m.group(1)), []).append(p)
    if not files:
        print("크롭을 찾지 못했습니다. 폴더 경로와 --truth 의 차량 ID를 확인하세요.")
        return

    for vid, paths in files.items():
        gt = truth[vid]
        m = re.match(r'^(?:(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주))?(\d{2,3})(.)(\d{4})$', gt)
        region, front, letter, back = (m.group(1) or '', m.group(2), m.group(3), m.group(4)) if m else ('', '', '', '')
        print("=" * 78)
        print(f"차량 {vid}  실제 {gt}  (크롭 {len(paths)}장)")
        print(f"{'방법':<14}{'정확일치':>9}{'지역명':>8}{'글자':>7}{'뒤4자리':>9}{'평균신뢰도':>11}")
        imgs = [imread(p) for p in paths]
        base_exact = None
        for name, fn in VARIANTS.items():
            ex = rg = lt = bk = 0
            confs = []
            for im in imgs:
                try:
                    text, conf = run_ocr(fn(im.copy()))
                except Exception:
                    continue
                ex += text == gt
                rg += (not region) or text.startswith(region)
                lt += bool(letter) and letter in text
                bk += bool(back) and text.endswith(back)
                confs.append(conf)
            n = len(imgs)
            mark = ''
            if name == 'base':
                base_exact = ex
            elif base_exact is not None and ex > base_exact:
                mark = '  <- base보다 증가'
            print(f"{name:<14}{ex:>5}/{n:<3}{rg:>5}/{n:<2}{lt:>4}/{n:<2}{bk:>6}/{n:<2}{np.mean(confs) if confs else 0:>10.2f}{mark}")
    print("\n읽는 법: 정확일치가 base보다 크게(예: 절반 이상 늘어날 정도) 오르는 방법이 없으면 화질 개선은 효과가 없다는 뜻입니다.")
    print("         '지역명'/'글자' 열이 오르는지도 보세요. 작은 글자가 문제라면 이 열이 먼저 달라집니다.")


if __name__ == '__main__':
    main()
