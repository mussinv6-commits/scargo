import sys
sys.path.append(r'C:\plate_dataset\deep-text-recognition-benchmark')
from dataset import preprocess_license_plate_opencv
from PIL import Image
import os

test_folder = r'C:\plate_dataset\cropped_merged_712\train'
output_folder = r'C:\plate_dataset\preprocess_check_lowratio'
os.makedirs(output_folder, exist_ok=True)

count = 0
for filename in os.listdir(test_folder):
    if not filename.lower().endswith(('.jpg', '.jpeg', '.png')):
        continue
    img_path = os.path.join(test_folder, filename)
    img = Image.open(img_path).convert('L')
    w, h = img.size
    ratio = w / h

    if ratio < 2.5:  # 2줄 분할 로직이 작동하는 케이스만 골라서 저장
        processed = preprocess_license_plate_opencv(img)
        combined = Image.new('L', (max(img.width, processed.width), img.height + processed.height))
        combined.paste(img, (0, 0))
        combined.paste(processed, (0, img.height))
        combined.save(os.path.join(output_folder, f"ratio{ratio:.2f}_{filename}"))
        count += 1
        if count >= 20:
            break

print(f"{count}장 저장 완료 (비율 2.5 미만만 골라냄)")