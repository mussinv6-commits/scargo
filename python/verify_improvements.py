# -*- coding: utf-8 -*-
"""
verify_improvements.py
====================================================
번호판 검출/인식 "개선 전(OLD)" vs "개선 후(NEW)" 실제 비교 검증 스크립트.

이 스크립트는 plate_detector_gui.py에 최근 적용한 개선사항을 그대로 재현해서,
바꾸기 전 방식과 바꾼 후 방식을 "같은 실제 사진들"에 대해 나란히 돌려보고
결과를 CSV와 화면에 출력합니다. Claude가 임의로 판단한 게 아니라, 실제 모델과
OCR 엔진을 그대로 써서 눈으로 확인 가능한 비교 결과를 내는 게 목적입니다.

비교 대상:
  OLD: imgsz 미지정(기본 640) + augment 없음 + OCR 크롭 패딩 없음 + OCR 글자 순서 그대로
  NEW: imgsz=960(학습 해상도) + augment=True(TTA) + OCR 크롭 8% 패딩 + OCR 글자 순서/잡음 보정
       + OCR 저확신도 재시도(1차 글자인식률<0.5거나 빈 결과면 저조도 보정판으로 2차 시도)

사용법 (Anaconda Prompt에서, plate_build 또는 base 환경 - ultralytics/easyocr 설치된 환경):
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    python verify_improvements.py "테스트 사진들이 있는 폴더 경로"

    예) python verify_improvements.py "C:\\Users\\user\\Desktop\\번호판_테스트사진"

    폴더 인자를 생략하면 기본값으로 프로젝트 폴더 아래 test_images 폴더를 찾습니다.
    "55y7a742" 문제였던 사진처럼 예전에 실패했던 사진들을 그 폴더에 넣고 돌려보면
    가장 직접적인 비교가 됩니다.

    파일명이 "12가3456"이나 "경기12가3456" 같은 번호판 형식이면 자동으로 정답으로
    간주해서 OCR 정확도(글자가 정답과 완전히 일치하는 비율)까지 같이 계산합니다.
    (그냥 눈으로 비교만 하고 싶으면 파일명이 뭐든 상관없이 OLD/NEW 결과만 출력됩니다)

결과: 실행한 폴더의 부모 폴더에 verify_result.csv 로 상세 결과가 저장되고,
      화면에 검출률/OCR 일치율 요약이 출력됩니다.
====================================================
"""
import sys
import re
import csv
from pathlib import Path

import cv2
import numpy as np

try:
    from ultralytics import YOLO
except ImportError:
    print("ultralytics가 설치되어 있지 않습니다. 'pip install ultralytics' 후 다시 실행해주세요.")
    sys.exit(1)

# 현재 기본 모델. plate_detect_m(mAP50-95=0.874)과 plate_detect_m_lowlight(0.874, 저조도 실험)는
# mAP50-95가 사실상 동점이지만, 실제 저조도 사진 확신도 비교(0.825->0.916)에서 lowlight 쪽이
# 뚜렷하게 더 나아서 GUI 자동 선택 기준도 이쪽을 기본으로 고르도록 맞춰뒀음(동점 tie-break).
# 이 스크립트도 같은 모델을 기본값으로 씀 - 다른 모델로 비교하고 싶으면 두 번째 인자로 경로를 넘기면 됨.
DEFAULT_MODEL_PATH = Path("runs/detect/plate_detect_m_lowlight/weights/best.pt")
CONF = 0.9  # 사용자가 요구한 엄격한 기준(0.9) 그대로 유지해서 비교함 - 여기서 낮추지 않음
NEW_IMGSZ = 960  # args.yaml 기준 이 프로젝트 모델들의 학습 해상도

# "12가3456" / "경기12가3456" 형태의 번호판 패턴 (파일명이 이 형식이면 정답으로 취급)
PLATE_RE = re.compile(r"^\d{2,3}[가-힣]\d{4}$|^[가-힣]{2}\d{2}[가-힣]\d{4}$")


# ---------------- 검출 (OLD vs NEW) ----------------
def old_detect(model, img):
    """이전 방식: imgsz 지정 안 함(기본값 640으로 추론됨), TTA 없음."""
    r = model.predict(source=img, conf=CONF, verbose=False)[0]
    return r.boxes.xyxy.cpu().numpy(), r.boxes.conf.cpu().numpy()


def new_detect(model, img):
    """새 방식: 학습 해상도(imgsz=960)로 추론 + TTA(augment=True)."""
    r = model.predict(source=img, conf=CONF, imgsz=NEW_IMGSZ, augment=True, verbose=False)[0]
    return r.boxes.xyxy.cpu().numpy(), r.boxes.conf.cpu().numpy()


# ---------------- OCR용 크롭 (OLD vs NEW) ----------------
def old_crop(img, x1, y1, x2, y2):
    """이전 방식: YOLO 박스를 그대로 잘라서 씀 (여유 없음)."""
    return img[int(y1):int(y2), int(x1):int(x2)]


def new_crop(img, x1, y1, x2, y2, pad_ratio=0.08):
    """새 방식: 박스보다 8%(최소 3px) 여유를 두고 잘라서 글자 끝 잘림을 줄임."""
    h, w = img.shape[:2]
    bw, bh = x2 - x1, y2 - y1
    pad_x = max(3.0, bw * pad_ratio)
    pad_y = max(3.0, bh * pad_ratio)
    px1 = max(0, int(x1 - pad_x))
    py1 = max(0, int(y1 - pad_y))
    px2 = min(w, int(x2 + pad_x))
    py2 = min(h, int(y2 + pad_y))
    return img[py1:py2, px1:px2]


# ---------------- OCR 전처리 (공통) ----------------
def _prep_gray(crop, alt=False):
    """alt=True면 plate_detector_gui.py의 _ocr_once(alt=True)와 동일하게 저조도 보정을 추가함:
    어두운 crop(평균 밝기<100)만 감마 보정(gamma=1.8)으로 먼저 밝히고, CLAHE도 더 강하게
    (clipLimit 2.0->4.0) 건다. 밝은 crop은 감마 보정을 건너뛰어 불필요한 왜곡을 막음."""
    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
    h, w = gray.shape[:2]
    target_h = 160
    if h < target_h:
        scale = target_h / max(h, 1)
        gray = cv2.resize(gray, (max(1, int(w * scale)), target_h), interpolation=cv2.INTER_CUBIC)
    if alt and float(gray.mean()) < 100.0:
        gamma = 1.8
        inv_gamma = 1.0 / gamma
        lut = np.array([((i / 255.0) ** inv_gamma) * 255 for i in range(256)]).astype("uint8")
        gray = cv2.LUT(gray, lut)
    clip_limit = 4.0 if alt else 2.0
    clahe = cv2.createCLAHE(clipLimit=clip_limit, tileGridSize=(8, 8))
    return clahe.apply(gray)


def ocr_old(reader, crop):
    """이전 방식: EasyOCR이 준 순서 그대로 공백으로 이어붙임 (잡음/순서 보정 없음)."""
    if crop is None or crop.size == 0:
        return ""
    gray = _prep_gray(crop)
    result = reader.readtext(
        gray, detail=0, paragraph=False, text_threshold=0.4, low_text=0.3, link_threshold=0.3
    )
    return " ".join(result).strip()


def _order_fragments(raw):
    """plate_detector_gui.py의 _order_ocr_fragments와 동일한 로직.
    (텍스트, 글자인식률) 튜플을 돌려줌 - GUI에서 검출 인식률과 별도로 표시하는
    "글자 인식률"과 정확히 같은 계산식."""
    if not raw:
        return "", None
    filtered = [it for it in raw if it[2] >= 0.2]
    items = filtered if filtered else raw
    ocr_conf = sum(it[2] for it in items) / len(items)

    def y_center(b):
        return sum(p[1] for p in b) / len(b)

    def x_center(b):
        return sum(p[0] for p in b) / len(b)

    def box_h(b):
        ys = [p[1] for p in b]
        return max(ys) - min(ys)

    avg_h = sum(box_h(it[0]) for it in items) / len(items) or 1.0
    items_y = sorted(items, key=lambda it: y_center(it[0]))
    rows = []
    for it in items_y:
        placed = False
        for row in rows:
            if abs(y_center(it[0]) - y_center(row[0][0])) < avg_h * 0.6:
                row.append(it)
                placed = True
                break
        if not placed:
            rows.append([it])
    rows.sort(key=lambda row: min(y_center(it[0]) for it in row))
    lines = []
    for row in rows:
        row.sort(key=lambda it: x_center(it[0]))
        lines.append(" ".join(it[1] for it in row))
    return " ".join(lines).strip(), ocr_conf


def _ocr_once(reader, crop, alt=False):
    """plate_detector_gui.py의 _ocr_once와 동일 - crop 한 장에서 한 번 읽어봄."""
    if crop is None or crop.size == 0:
        return "", None
    gray = _prep_gray(crop, alt=alt)
    raw = reader.readtext(
        gray, detail=1, paragraph=False, text_threshold=0.4, low_text=0.3, link_threshold=0.3
    )
    return _order_fragments(raw)


def ocr_new(reader, crop):
    """새 방식: 위치 기반 줄 정렬 + 저확신도 잡음 제거 + 저확신도 재시도.
    plate_detector_gui.py의 _ocr_plate(retry_if_low_conf=True)와 동일한 로직 -
    1차 결과의 글자인식률이 낮거나(<0.5) 아예 못 읽었으면, 저조도 보정을 추가로 건
    2차 시도(alt=True)를 한 번 더 해보고 더 나은 쪽을 채택함.
    (텍스트, 글자인식률) 튜플 반환."""
    text, conf = _ocr_once(reader, crop, alt=False)
    if text and conf is not None and conf >= 0.5:
        return text, conf
    alt_text, alt_conf = _ocr_once(reader, crop, alt=True)
    if not alt_text:
        return text, conf
    if not text or conf is None or (alt_conf is not None and alt_conf > conf):
        return alt_text, alt_conf
    return text, conf


def norm(s):
    return re.sub(r"\s+", "", s or "")


def main():
    folder = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("test_images")
    model_path = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_MODEL_PATH

    if not folder.exists():
        print(f"[오류] 테스트 사진 폴더를 찾을 수 없습니다: {folder}")
        print("사용법: python verify_improvements.py \"테스트 사진 폴더 경로\"")
        return
    if not model_path.exists():
        print(f"[오류] 모델을 찾을 수 없습니다: {model_path}")
        return

    imgs = sorted(p for p in folder.glob("*") if p.suffix.lower() in (".jpg", ".jpeg", ".png", ".bmp"))
    if not imgs:
        print(f"[오류] 폴더에 이미지 파일이 없습니다: {folder}")
        return

    print(f"모델 로딩: {model_path}")
    model = YOLO(str(model_path))
    print("OCR 엔진 로딩 중 (최초 1회 언어 모델 다운로드가 있을 수 있습니다)...")
    import easyocr

    reader = easyocr.Reader(["ko", "en"], gpu=False)
    print(f"대상 이미지 {len(imgs)}장, conf={CONF} 기준으로 OLD/NEW 비교 시작\n")

    rows_out = []
    old_hits = new_hits = 0
    old_ocr_match = new_ocr_match = 0
    matchable = 0

    for p in imgs:
        img = cv2.imread(str(p))
        if img is None:
            print(f"[건너뜀] 이미지를 열 수 없음: {p.name}")
            continue

        gt = norm(p.stem)
        is_matchable = bool(PLATE_RE.match(p.stem))

        ob, oc = old_detect(model, img)
        nb, nc = new_detect(model, img)

        old_text, new_text = "", ""
        old_conf_s, new_conf_s, new_ocr_conf_s = "", "", ""

        if len(ob):
            i = int(oc.argmax())
            x1, y1, x2, y2 = ob[i]
            old_conf_s = f"{float(oc[i]):.3f}"
            old_text = ocr_old(reader, old_crop(img, x1, y1, x2, y2))
            old_hits += 1
        if len(nb):
            i = int(nc.argmax())
            x1, y1, x2, y2 = nb[i]
            new_conf_s = f"{float(nc[i]):.3f}"
            new_text, new_ocr_conf = ocr_new(reader, new_crop(img, x1, y1, x2, y2))
            new_ocr_conf_s = f"{new_ocr_conf:.3f}" if new_ocr_conf is not None else ""
            new_hits += 1

        if is_matchable:
            matchable += 1
            if norm(old_text) == gt:
                old_ocr_match += 1
            if norm(new_text) == gt:
                new_ocr_match += 1

        print(
            f"[{p.name}] "
            f"OLD: {'O' if len(ob) else 'X'}(검출 {old_conf_s or '-'}) '{old_text}'  |  "
            f"NEW: {'O' if len(nb) else 'X'}(검출 {new_conf_s or '-'} · 글자인식 {new_ocr_conf_s or '-'}) '{new_text}'"
        )

        rows_out.append(
            [
                p.name,
                gt if is_matchable else "",
                "O" if len(ob) else "X",
                old_conf_s,
                old_text,
                "O" if len(nb) else "X",
                new_conf_s,
                new_ocr_conf_s,
                new_text,
            ]
        )

    out_csv = folder.parent / "verify_result.csv"
    with open(out_csv, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(
            ["파일명", "정답(파일명 기준)", "OLD검출", "OLD 검출conf", "OLD OCR",
             "NEW검출", "NEW 검출conf", "NEW 글자인식conf", "NEW OCR"]
        )
        w.writerows(rows_out)

    n = len(imgs)
    print("\n===== 요약 =====")
    print(f"전체 이미지: {n}장")
    print(
        f"conf={CONF} 검출률   OLD {old_hits}/{n} ({old_hits/n*100:.0f}%)  ->  "
        f"NEW {new_hits}/{n} ({new_hits/n*100:.0f}%)"
    )
    if matchable:
        print(
            f"OCR 정답 일치율(파일명이 번호판 형식인 {matchable}장 대상)   "
            f"OLD {old_ocr_match}/{matchable} ({old_ocr_match/matchable*100:.0f}%)  ->  "
            f"NEW {new_ocr_match}/{matchable} ({new_ocr_match/matchable*100:.0f}%)"
        )
    else:
        print("(파일명이 번호판 형식(예: 12가3456)이 아니라서 OCR 정답 일치율은 계산하지 않았습니다.")
        print(" 위 OLD/NEW OCR 결과를 눈으로 직접 비교해주세요.)")
    print(f"상세 결과 저장됨: {out_csv}")


if __name__ == "__main__":
    main()
