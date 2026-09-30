import shutil
from pathlib import Path

root = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
train_img = root / "train" / "images" if (root / "train" / "images").exists() else root / "train"
train_lbl = root / "train" / "labels" if (root / "train" / "labels").exists() else root / "train"
train_img.mkdir(parents=True, exist_ok=True)
train_lbl.mkdir(parents=True, exist_ok=True)

moved = dup = 0
for split in ("valid", "test"):
    for sub, dst in (("images", train_img), ("labels", train_lbl)):
        d = root / split / sub
        if not d.exists():
            continue
        for p in list(d.iterdir()):
            if not p.is_file():
                continue
            target = dst / p.name
            if target.exists():   # 이미 train 에 같은 파일이 있으면 중복이라 삭제
                p.unlink()
                dup += 1
            else:
                shutil.move(str(p), target)
                moved += 1

for c in root.rglob("*.cache"):
    c.unlink()

print(f"train 으로 되돌림: {moved}개 / 중복 삭제: {dup}개")
