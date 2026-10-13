import random
import shutil
from collections import defaultdict
from pathlib import Path

root = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
EXTS = {".jpg", ".jpeg", ".png", ".bmp"}
SEED = 42
RATIO = {"train": 0.7, "valid": 0.2, "test": 0.1}

# 이미지/라벨 폴더 위치 (train\images + train\labels 또는 train 한 폴더에 섞여 있는 경우 모두 처리)
src = root / "train"
img_dir = src / "images" if (src / "images").exists() else src
lbl_dir = src / "labels" if (src / "labels").exists() else src

# 이미 분할했는지 확인 (중복 실행 방지)
for s in ("valid", "test"):
    d = root / s / "images"
    if d.exists() and any(d.iterdir()):
        raise SystemExit(f"{s}\\images 가 비어있지 않아요. 이미 분할된 것 같아서 중단합니다.")

images = sorted(p for p in img_dir.iterdir() if p.suffix.lower() in EXTS)
total = len(images)
print("전체 이미지:", total)

# Roboflow 증강본(같은 원본에서 나온 파일)은 같은 묶음으로 처리 -> 데이터 누수 방지
groups = defaultdict(list)
for p in images:
    groups[p.name.split(".rf.")[0]].append(p)

keys = sorted(groups)
random.Random(SEED).shuffle(keys)

target_valid = round(total * RATIO["valid"])
target_test = round(total * RATIO["test"])
split = {"train": [], "valid": [], "test": []}
for k in keys:
    if len(split["valid"]) < target_valid:
        split["valid"] += groups[k]
    elif len(split["test"]) < target_test:
        split["test"] += groups[k]
    else:
        split["train"] += groups[k]

# valid / test 로 이동 (train 에는 나머지가 남음)
for name in ("valid", "test"):
    out_img = root / name / "images"
    out_lbl = root / name / "labels"
    out_img.mkdir(parents=True, exist_ok=True)
    out_lbl.mkdir(parents=True, exist_ok=True)
    for p in split[name]:
        shutil.move(str(p), out_img / p.name)
        lbl = lbl_dir / (p.stem + ".txt")
        if lbl.exists():
            shutil.move(str(lbl), out_lbl / lbl.name)

# 예전 캐시 삭제 (안 지우면 이전 목록으로 읽어서 오류 날 수 있음)
for c in root.rglob("*.cache"):
    c.unlink()

for name, items in split.items():
    print(f"{name:6s} {len(items):5d}장 ({len(items) / total:.0%})")
