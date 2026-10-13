from pathlib import Path

root = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")

print("=== data.yaml ===")
print((root / "data.yaml").read_text(encoding="utf-8"))

print("=== 이미지 / 라벨 개수 ===")
for split in ["train", "valid", "test"]:
    img_dir = root / split / "images"
    lbl_dir = root / split / "labels"
    imgs = [p for p in img_dir.glob("*") if p.suffix.lower() in {".jpg", ".jpeg", ".png", ".bmp"}] if img_dir.exists() else []
    lbls = list(lbl_dir.glob("*.txt")) if lbl_dir.exists() else []
    print(f"{split:6s} 이미지 {len(imgs):5d} / 라벨 {len(lbls):5d}")
