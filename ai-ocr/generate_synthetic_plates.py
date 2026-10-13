# -*- coding: utf-8 -*-
"""
generate_synthetic_plates.py
====================================================
실제 사진을 더 구할 수 없는 상황에서, 화물차 번호판처럼 보이는 합성(가상) 이미지를
대량으로 만들어서 CRNN을 먼저 사전학습(pretrain)시키기 위한 스크립트.

왜 필요한가
====================================================
지금 CRNN은 실제 사진 1,765장(학습)만으로 처음부터(random init) 학습돼서, 특정
지역명/숫자 조합을 충분히 못 본 탓에 번호판 전체를 다른 조합으로 통째로 헷갈리는
오류가 있음(예: "경북86아7724" -> "부산81바2822"). 실제 사진은 못 구해도, 있는
글자 조합(chars.json에 이미 있는 문자들)으로 훨씬 더 다양한 "지역명 x 숫자" 조합을
합성으로 대량 만들어서 먼저 사전학습시키면, 실제 사진 1,765장으로 미세조정할 때
지역명 조합을 헷갈리는 문제가 줄어들 것으로 기대됨.

핵심 원칙: 반드시 ocr_model/chars.json에 있는 문자만 사용함 (pretrain된 모델을
그대로 실제 데이터로 이어서 미세조정하려면, 사전학습 때 쓴 문자 집합과 실제
학습 때 문자 집합이 완전히 같아야 가중치를 그대로 이어받을 수 있음).

또한, 깨끗한 합성 이미지 그대로 학습시키면 오히려 역효과가 날 수 있음 - 이미
실제로 테스트해봤을 때 "너무 깨끗한 가상 번호판"에서 오히려 오독이 났던 사례가
있었음(실사진과 화질/조명/각도가 너무 다른 이미지라 학습에 안 좋은 영향을 줄 수
있음). 그래서 이 스크립트는 깨끗하게 그린 번호판에 회전/원근왜곡/블러/노이즈/
저조도/글레어/압축열화 등을 일부러 강하게 입혀서, 실제 사진과 비슷한 "지저분함"을
갖도록 만듦.

실행 방법
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    pip install pillow  (이미 설치돼 있을 가능성 높음)
    python generate_synthetic_plates.py

ocr_train_data_synthetic\\crops 폴더에 이미지가, ocr_train_data_synthetic\\labels.csv에
라벨이 생성됩니다. 기본 생성 개수는 NUM_SAMPLES(기본 20000)로 조절 가능합니다.
"""
import csv
import io
import random
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

PROJECT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
CHARS_JSON_PATH = PROJECT_DIR / "ocr_model" / "chars.json"

OUT_DIR = PROJECT_DIR / "ocr_train_data_synthetic"
OUT_CROPS_DIR = OUT_DIR / "crops"
OUT_LABELS_CSV = OUT_DIR / "labels.csv"

NUM_SAMPLES = 200  # 실제 사진(1,961장)의 약 10% - 사용자 지정값.
                    # 참고로 남겨둠: 이 정도 개수로는 실제 데이터에 없던 "지역명 x 숫자"
                    # 조합을 아주 폭넓게 새로 보여주긴 어려워서, 애초 의도했던 "지역명
                    # 통째로 헷갈리는 오류를 줄이는" 효과는 20,000장일 때보다 제한적일
                    # 수 있음 - 그래도 해는 없고, 부담 없이 빠르게 시도해볼 수 있는 값임.
                    # 필요하면 이 숫자만 바꿔서 언제든 다시 생성하면 됨.
SEED = 123

# Windows(실제 실행 환경)에 보통 있는 한글 폰트를 우선 찾고, 없으면(예: 이 스크립트를
# 다른 OS에서 미리보기로 돌릴 때) 리눅스 Noto 폰트로 대체함.
FONT_CANDIDATES = [
    (r"C:\Windows\Fonts\malgunbd.ttf", 0),   # 맑은 고딕 Bold - 가장 우선
    (r"C:\Windows\Fonts\malgun.ttf", 0),
    (r"C:\Windows\Fonts\gulim.ttc", 0),
    ("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 2),  # 미리보기/개발용
]

REGION_NAMES = [
    "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
    "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
]

# set()으로 중복 제거 - 지역명 글자(예: "경기"의 "경","기")와 기본 목록 글자가 겹칠 수 있음
HANGUL_FALLBACK = sorted(set(
    "가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주바사아자배허하호"
    + "".join(REGION_NAMES)  # 지역명에 쓰이는 글자도 기본 목록에 포함(서울/부산/... 등)
))


def load_vocab():
    """chars.json이 있으면(정상적인 경우, 이미 모델을 한 번 학습시켰으므로 항상 있어야 함)
    그 문자 집합을 그대로 씀. 없으면(=미리보기 등 예외 상황) 대략적인 기본 한글 목록으로
    대체 - 이 경우 사전학습 결과는 실제 미세조정과 호환되지 않으므로 경고를 출력함."""
    import json

    if CHARS_JSON_PATH.exists():
        with open(CHARS_JSON_PATH, "r", encoding="utf-8") as f:
            chars = json.load(f)["chars"]
        digits = [c for c in chars if c.isdigit()]
        hangul = [c for c in chars if not c.isdigit()]
        return chars, digits, hangul
    print(
        f"[경고] {CHARS_JSON_PATH} 를 찾지 못해 기본 한글 목록으로 대체합니다. "
        "이 상태로 만든 합성 데이터는 실제 미세조정과 문자 집합이 달라 호환되지 "
        "않을 수 있으니, 반드시 chars.json이 있는 환경(학습을 한 번이라도 마친 뒤)에서 "
        "다시 실행해주세요."
    )
    digits = list("0123456789")
    hangul = HANGUL_FALLBACK
    return digits + hangul, digits, hangul


def get_font(size):
    for path, index in FONT_CANDIDATES:
        if Path(path).exists():
            try:
                return ImageFont.truetype(path, size, index=index)
            except Exception:
                continue
    return ImageFont.load_default()


def usable_regions(hangul_chars):
    vocab = set(hangul_chars)
    usable = [r for r in REGION_NAMES if all(ch in vocab for ch in r)]
    dropped = [r for r in REGION_NAMES if r not in usable]
    if dropped:
        print(f"[안내] chars.json에 없는 문자가 포함돼 제외된 지역명: {dropped}")
    if usable:
        return usable
    # 실제 지역명을 하나도 못 만드는 극단적인 경우(어휘가 너무 좁음) - 없는 문자를
    # 억지로 쓰면 나중에 학습이 깨지므로, 대신 vocab 안의 무작위 한글 2글자를
    # "지역명"처럼 취급함 (None을 돌려주면 make_plate_text가 이 경로를 탐).
    print("[경고] chars.json 안의 문자로는 실제 지역명을 하나도 못 만들어서, "
          "무작위 한글 2글자 조합으로 대체합니다.")
    return None


def make_plate_text(regions, digits, hangul):
    if regions:
        region = random.choice(regions)
    else:
        region = "".join(random.choice(hangul) for _ in range(2))
    d2 = "".join(random.choice(digits) for _ in range(2))
    cls = random.choice(hangul)
    d4 = "".join(random.choice(digits) for _ in range(4))
    return region, d2, cls, d4


def render_clean_plate(region, d2, cls, d4, two_line: bool):
    """깨끗한 번호판 이미지를 그림 (증강 전 단계). RGB PIL 이미지 반환."""
    bg_color = (250, 205, 15) if random.random() < 0.85 else (250, 250, 250)  # 영업용 노란색 위주
    border_color = (30, 30, 30)

    if two_line:
        w, h = 340, 220
        img = Image.new("RGB", (w, h), bg_color)
        draw = ImageDraw.Draw(img)
        draw.rectangle([4, 4, w - 5, h - 5], outline=border_color, width=4)
        top_font = get_font(46)
        bottom_font = get_font(90)
        top_text = f"{region}{d2}"
        bottom_text = f"{cls}{d4}"
        tb = draw.textbbox((0, 0), top_text, font=top_font)
        draw.text(((w - (tb[2] - tb[0])) / 2, 14), top_text, font=top_font, fill=(0, 0, 0))
        bb = draw.textbbox((0, 0), bottom_text, font=bottom_font)
        draw.text(
            ((w - (bb[2] - bb[0])) / 2, h - (bb[3] - bb[1]) - 22),
            bottom_text, font=bottom_font, fill=(0, 0, 0),
        )
    else:
        w, h = 760, 170
        img = Image.new("RGB", (w, h), bg_color)
        draw = ImageDraw.Draw(img)
        draw.rectangle([4, 4, w - 5, h - 5], outline=border_color, width=4)
        font = get_font(110)
        left_text = f"{region}{d2}"
        right_text = f"{cls}{d4}"
        lb = draw.textbbox((0, 0), left_text, font=font)
        rb = draw.textbbox((0, 0), right_text, font=font)
        gap = 26
        total_w = (lb[2] - lb[0]) + gap + (rb[2] - rb[0])
        x = (w - total_w) / 2
        y = (h - (lb[3] - lb[1])) / 2 - lb[1]
        draw.text((x, y), left_text, font=font, fill=(0, 0, 0))
        draw.text((x + (lb[2] - lb[0]) + gap, y), right_text, font=font, fill=(0, 0, 0))

    # 위쪽 모서리 볼트 느낌 (실제 번호판 특유의 고정 나사 구멍)
    for cx in (int(w * 0.08), int(w * 0.92)):
        draw.ellipse([cx - 5, 10, cx + 5, 20], fill=(90, 90, 90))
    return img


def add_context_margin(img_bgr):
    """플레이트 주변에 약간의 여유 배경(실제 크롭에도 늘 조금 딸려오는 차체/배경 톤)을 둠."""
    h, w = img_bgr.shape[:2]
    pad_x = int(w * random.uniform(0.06, 0.20))
    pad_y = int(h * random.uniform(0.10, 0.30))
    bg = random.choice([
        (40, 40, 40), (120, 120, 120), (200, 200, 200), (60, 70, 90), (10, 10, 10),
    ])
    canvas = np.full((h + pad_y * 2, w + pad_x * 2, 3), bg, dtype=np.uint8)
    canvas[pad_y:pad_y + h, pad_x:pad_x + w] = img_bgr
    return canvas


def random_rotation_perspective(img_bgr):
    h, w = img_bgr.shape[:2]
    angle = random.uniform(-9, 9)
    center = (w / 2, h / 2)
    rot_mat = cv2.getRotationMatrix2D(center, angle, 1.0)
    bg = tuple(int(v) for v in img_bgr[2, 2])
    img_bgr = cv2.warpAffine(img_bgr, rot_mat, (w, h), borderValue=bg, flags=cv2.INTER_LINEAR)

    if random.random() < 0.6:
        jitter = min(w, h) * 0.06
        src = np.float32([[0, 0], [w, 0], [0, h], [w, h]])
        dst = np.float32([
            [random.uniform(0, jitter), random.uniform(0, jitter)],
            [w - random.uniform(0, jitter), random.uniform(0, jitter)],
            [random.uniform(0, jitter), h - random.uniform(0, jitter)],
            [w - random.uniform(0, jitter), h - random.uniform(0, jitter)],
        ])
        M = cv2.getPerspectiveTransform(src, dst)
        img_bgr = cv2.warpPerspective(img_bgr, M, (w, h), borderValue=bg, flags=cv2.INTER_LINEAR)
    return img_bgr


def apply_realism(img_bgr):
    """깨끗한 렌더링 결과에 실제 사진 느낌을 입힘 (회전/원근/블러/노이즈/조명/압축열화)."""
    img_bgr = add_context_margin(img_bgr)
    img_bgr = random_rotation_perspective(img_bgr)

    # 블러 (초점/흔들림)
    if random.random() < 0.5:
        k = random.choice([3, 5])
        img_bgr = cv2.GaussianBlur(img_bgr, (k, k), 0)

    # 밝기/대비 - 가끔 아주 어둡거나 밝게(글레어) 만들어서 저조도/역광 사진을 흉내냄
    alpha = random.uniform(0.6, 1.3)  # 대비
    beta = random.uniform(-60, 60)    # 밝기
    img_bgr = cv2.convertScaleAbs(img_bgr, alpha=alpha, beta=beta)

    if random.random() < 0.12:
        # 헤드라이트 반사 글레어 흉내 - 밝은 타원을 살짝 겹침
        h, w = img_bgr.shape[:2]
        overlay = img_bgr.copy()
        cx, cy = random.randint(0, w), random.randint(0, h)
        axis = (random.randint(w // 4, w // 2), random.randint(h // 4, h // 2))
        cv2.ellipse(overlay, (cx, cy), axis, 0, 0, 360, (255, 255, 255), -1)
        img_bgr = cv2.addWeighted(overlay, 0.5, img_bgr, 0.5, 0)

    # 가우시안 노이즈
    noise = np.random.normal(0, random.uniform(3, 14), img_bgr.shape).astype(np.float32)
    img_bgr = np.clip(img_bgr.astype(np.float32) + noise, 0, 255).astype(np.uint8)

    # 저해상도 촬영 흉내 (축소 후 재확대)
    if random.random() < 0.4:
        h, w = img_bgr.shape[:2]
        scale = random.uniform(0.4, 0.8)
        small = cv2.resize(img_bgr, (max(1, int(w * scale)), max(1, int(h * scale))), interpolation=cv2.INTER_AREA)
        img_bgr = cv2.resize(small, (w, h), interpolation=cv2.INTER_LINEAR)

    # JPEG 압축 열화
    quality = random.randint(35, 90)
    ok, enc = cv2.imencode(".jpg", img_bgr, [cv2.IMWRITE_JPEG_QUALITY, quality])
    if ok:
        img_bgr = cv2.imdecode(enc, cv2.IMREAD_COLOR)

    return img_bgr


def generate_one(idx, regions, digits, hangul):
    two_line = random.random() < 0.35  # 2줄 번호판 비율(실제 데이터 비율과 비슷하게)
    region, d2, cls, d4 = make_plate_text(regions, digits, hangul)
    text = f"{region}{d2}{cls}{d4}"
    clean = render_clean_plate(region, d2, cls, d4, two_line)
    img_bgr = cv2.cvtColor(np.array(clean), cv2.COLOR_RGB2BGR)
    realistic = apply_realism(img_bgr)
    filename = f"{idx:05d}__{text}.jpeg"
    line_count = 2 if two_line else 1
    return filename, text, line_count, realistic


def main():
    random.seed(SEED)
    np.random.seed(SEED)

    all_chars, digits, hangul = load_vocab()
    regions = usable_regions(hangul)
    print(f"문자 집합 크기: {len(all_chars)} (숫자 {len(digits)} + 한글 {len(hangul)})")
    print(f"사용 가능한 지역명: {len(regions)}개 {regions}")
    print(f"생성할 이미지 수: {NUM_SAMPLES}\n")

    OUT_CROPS_DIR.mkdir(parents=True, exist_ok=True)
    rows = []
    for i in range(NUM_SAMPLES):
        filename, text, line_count, img_bgr = generate_one(i, regions, digits, hangul)
        ok, enc = cv2.imencode(".jpeg", img_bgr, [cv2.IMWRITE_JPEG_QUALITY, 90])
        if ok:
            (OUT_CROPS_DIR / filename).write_bytes(enc.tobytes())
            rows.append((filename, text, line_count))
        if (i + 1) % 2000 == 0:
            print(f"  ...{i + 1}/{NUM_SAMPLES}장 생성됨")

    with open(OUT_LABELS_CSV, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(["filename", "text", "line_count_guess"])
        writer.writerows(rows)

    print(f"\n=== 완료 === {len(rows)}장 -> {OUT_CROPS_DIR}")
    print(f"라벨: {OUT_LABELS_CSV}")
    print("다음 단계: python pretrain_synthetic.py 로 이 데이터를 이용해 사전학습을 진행하세요.")


if __name__ == "__main__":
    main()
