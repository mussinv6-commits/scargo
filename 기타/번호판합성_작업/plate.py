# AI 클립 속 번호판 · 전광판을 가상 한국 화물차 번호판으로 교체 + OCR 시연 오버레이
import cv2, numpy as np, subprocess
from PIL import Image, ImageDraw, ImageFont
F = '/usr/share/fonts/opentype/noto/NotoSansCJK-%s.ttc'
def font(w, s): return ImageFont.truetype(F % w, s, index=1)
TOP, BOT, FULL = '인천 80', '사 2210', '인천 80사 2210'
N = 146; A, B = 75, 146

def make_plate(w=440, h=220):  # 노란 바탕 2줄 영업용
    im = Image.new('RGB', (w, h), (246, 196, 30)); d = ImageDraw.Draw(im)
    d.rounded_rectangle((3, 3, w - 4, h - 4), 14, outline=(25, 25, 25), width=6)
    for x in (34, w - 34): d.ellipse((x - 7, 22, x + 7, 36), fill=(120, 110, 60))
    d.text((w / 2, 58), TOP, font=font('Bold', 64), fill=(15, 15, 15), anchor='mm')
    d.text((w / 2, 150), BOT, font=font('Black', 112), fill=(15, 15, 15), anchor='mm')
    return np.asarray(im)[:, :, ::-1]
PLATE = make_plate()

def find_plate(img, prev):
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    m = ((hsv[..., 2] > 170) & (hsv[..., 1] < 60)).astype(np.uint8) * 255
    m[:760] = 0; m[1060:] = 0
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    best = None
    for c in cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)[0]:
        x, y, w, h = cv2.boundingRect(c); ar = w / max(h, 1)
        if 35 < w < 220 and 1.4 < ar < 2.8 and cv2.contourArea(c) > 0.6 * w * h:
            sc = -abs(ar - 1.95) * 50 + w - (0 if prev is None else abs(x + w / 2 - prev[0] - prev[2] / 2) * 0.5)
            if best is None or sc > best[0]: best = (sc, (x, y, w, h))
    return best[1] if best else None

def find_sign(img):
    b, g, r = [img[..., i].astype(int) for i in range(3)]
    m = ((g > 140) & (g - r > 60) & (g - b > 30)); m[:, 320:] = False; m[:350] = False; m[650:] = False
    ys, xs = np.nonzero(m)
    if len(xs) < 60: return None
    return xs.min(), ys.min(), xs.max(), ys.max()

frames = [cv2.imread(f'f1/{i:04d}.png') for i in range(1, N + 1)]
gray = [cv2.cvtColor(f, cv2.COLOR_BGR2GRAY) for f in frames]
REF, RB = 110, (575, 876, 92, 55)   # 기준 프레임(0부터) · 번호판 박스
boxes, signs = {REF: RB}, {}
def track(rng):
    x, y, w, h = RB; tpl = gray[REF][y:y + h, x:x + w]
    for i in rng:
        best = None
        for sc in (0.94, 0.97, 1.0, 1.03, 1.06):
            tw, th = int(w * sc), int(h * sc)
            if tw < 16 or th < 10: continue
            t = cv2.resize(tpl, (tw, th))
            x0, y0 = max(0, int(x + w / 2 - tw / 2 - 40)), max(0, int(y + h / 2 - th / 2 - 30))
            win = gray[i][y0:y0 + th + 60, x0:x0 + tw + 80]
            if win.shape[0] < th or win.shape[1] < tw: continue
            r = cv2.matchTemplate(win, t, cv2.TM_CCOEFF_NORMED); _, mv, _, ml = cv2.minMaxLoc(r)
            if best is None or mv > best[0]: best = (mv, x0 + ml[0], y0 + ml[1], tw, th)
        if best is None or best[0] < 0.45: break
        _, x, y, w, h = best; boxes[i] = (x, y, w, h); tpl = gray[i][y:y + h, x:x + w]
track(range(REF - 1, A - 1, -1)); track(range(REF + 1, B))
last = max(boxes); v = np.subtract(boxes[last], boxes[last - 3]) / 3
for i in range(last + 1, B):   # 추적이 끊긴 뒤 화면 밖으로 나갈 때까지 이어 붙이기
    bx = np.array(boxes[i - 1], float) + v; v = v * 1.03
    if bx[0] + bx[2] * 0.3 > 715 or bx[1] + bx[3] > 1270: break
    boxes[i] = tuple(bx)
for i in range(A, B):
    s_ = find_sign(frames[i])
    if s_: signs[i] = s_
# 빈 프레임 보간 · 부드럽게
ks = sorted(boxes)
def interp(d, i):
    if i in d: return np.array(d[i], float)
    lo = [k for k in d if k < i]; hi = [k for k in d if k > i]
    if lo and hi: a, b = max(lo), min(hi); t = (i - a) / (b - a); return np.array(d[a]) * (1 - t) + np.array(d[b]) * t
    return None
sm = {}
for i in range(A, B):
    v = interp(boxes, i)
    if v is None: continue
    sm[i] = v if i - 1 not in sm else sm[i - 1] * 0.3 + v * 0.7
print('plate frames', len(boxes), 'sign frames', len(signs))

def paste_plate(img, box):
    x, y, w, h = box; x, y, w, h = int(x - w * 0.04), int(y - h * 0.06), int(w * 1.08), int(h * 1.12)
    if w < 10 or y + h > img.shape[0] or x < 0: return
    if x + w > img.shape[1]:
        cut = x + w - img.shape[1]; img2 = img[:, :]; full = np.zeros((h, w, 3), np.uint8)
        w2 = w - cut
        if w2 < 4: return
        roi_full = np.concatenate([img[y:y + h, x:img.shape[1]], np.zeros((h, cut, 3), np.uint8)], 1)
    else: roi_full = None
    p = cv2.resize(PLATE, (w, h), interpolation=cv2.INTER_AREA).astype(np.float32)
    roi = (roi_full if roi_full is not None else img[y:y + h, x:x + w]).astype(np.float32)
    lum = cv2.GaussianBlur(cv2.cvtColor(roi.astype(np.uint8), cv2.COLOR_BGR2GRAY), (0, 0), 3).astype(np.float32) / 255
    p = p * (0.42 + 0.5 * lum[..., None])                                  # 주변 밝기 반영
    p = cv2.GaussianBlur(p, (0, 0), max(0.6, w / 160))                      # 원본 선명도에 맞춤
    p += np.random.default_rng(i).normal(0, 4, p.shape)
    mask = np.zeros((h, w), np.float32); cv2.rectangle(mask, (1, 1), (w - 2, h - 2), 1, -1); mask = cv2.GaussianBlur(mask, (0, 0), 1.0)[..., None]
    outp = np.clip(roi * (1 - mask) + p * mask, 0, 255).astype(np.uint8)
    xe = min(img.shape[1], x + w); img[y:y + h, x:xe] = outp[:, :xe - x]

def paste_sign(img, s):
    x0, y0, x1, y1 = s; pad = 10
    x0, y0, x1, y1 = max(0, x0 - pad), max(0, y0 - pad), x1 + pad, y1 + pad
    w, h = x1 - x0, y1 - y0
    pim = Image.new('RGB', (w, h), (8, 14, 10)); d = ImageDraw.Draw(pim)
    fs = max(12, int(h * 0.2))
    d.text((w / 2, h * 0.24), '번호판 인식', font=font('Bold', fs), fill=(80, 255, 140), anchor='mm')
    d.text((w / 2, h * 0.52), '인천80사', font=font('Bold', int(fs * 1.05)), fill=(80, 255, 140), anchor='mm')
    d.text((w / 2, h * 0.8), '2210 ✓', font=font('Bold', int(fs * 1.05)), fill=(80, 255, 140), anchor='mm')
    arr = cv2.GaussianBlur(np.asarray(pim)[:, :, ::-1].astype(np.float32), (0, 0), 0.7)
    img[y0:y1, x0:x1] = np.clip(arr, 0, 255).astype(np.uint8)

clean = []
for i, f in enumerate(frames):
    img = f.copy()
    if i in signs: paste_sign(img, signs[i])
    if i in sm: paste_plate(img, sm[i])
    clean.append(img)

def write(path, imgs, fps=24):
    p = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'bgr24', '-s', '720x1280', '-r', str(fps), '-i', '-', '-c:v', 'libx264', '-crf', '17', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', path], stdin=subprocess.PIPE)
    for im in imgs: p.stdin.write(im.tobytes())
    p.stdin.close(); p.wait()
write('게이트_번호판_원본.mp4', clean)

# OCR 시연 오버레이 버전
CY = (63, 224, 204)
demo = []
first = min(sm) if sm else 999
for i, img in enumerate(clean):
    im = Image.fromarray(img[:, :, ::-1]); d = ImageDraw.Draw(im, 'RGBA')
    if i in sm and i >= first + 4:
        x, y, w, h = sm[i]; k = i - first
        c = CY if k > 14 else (255, 210, 80)
        L = max(8, w * 0.25)
        for (ax, ay, sx, sy) in ((x, y, 1, 1), (x + w, y, -1, 1), (x, y + h, 1, -1), (x + w, y + h, -1, -1)):
            d.line((ax, ay, ax + L * sx, ay), fill=c + (255,), width=3); d.line((ax, ay, ax, ay + L * sy), fill=c + (255,), width=3)
        if k <= 14:  # 스캔 라인
            sy_ = y + h * ((k % 7) / 6); d.line((x, sy_, x + w, sy_), fill=(255, 210, 80, 200), width=2)
            d.rounded_rectangle((x - 4, y - 46, x + 150, y - 10), 8, fill=(9, 14, 21, 220))
            d.text((x + 6, y - 28), '번호판 인식 중…', font=font('Bold', 18), fill=(255, 210, 80), anchor='lm')
        else:
            bx, by = min(720 - 356, max(16, x + w / 2 - 170)), max(150, y - 170)
            d.rounded_rectangle((bx, by, bx + 340, by + 132), 12, fill=(9, 14, 21, 225), outline=CY + (255,), width=2)
            d.text((bx + 18, by + 16), 'OCR · YOLO 검출 + CRNN 인식', font=font('Regular', 15), fill=(160, 175, 190))
            d.text((bx + 18, by + 38), FULL, font=font('Black', 34), fill=(255, 255, 255))
            d.text((bx + 18, by + 88), '신뢰도 98.7% · 사전 허가 ✓ · 출차 승인', font=font('Bold', 16), fill=(79, 220, 138))
            d.line((bx + 170, by + 132, x + w / 2, y), fill=CY + (200,), width=2)
    demo.append(np.asarray(im)[:, :, ::-1].copy())
write('게이트_번호판_OCR시연.mp4', demo)
cv2.imwrite('chk_clean.jpg', cv2.resize(np.hstack([clean[85], clean[112]]), (720, 640)))
cv2.imwrite('chk_demo.jpg', cv2.resize(np.hstack([demo[85], demo[112]]), (720, 640)))
print('ok')
