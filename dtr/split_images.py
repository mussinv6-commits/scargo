import os
import json
import shutil
import random
import re
from collections import defaultdict

# ===== 지역명 없는 AI Hub 데이터 (기존과 동일) =====
json_root = r'C:/Users/1/Downloads/103.자동차_차종-연식-번호판_인식용_데이터/추가_데이터_보완_건_211229(폴더구조수정)/1.Training'
image_roots = [
    r'C:/Users/1/Downloads/103.자동차_차종-연식-번호판_인식용_데이터/추가_데이터_보완_건_211229(폴더구조수정)/1.Training/자동차번호판OCR_이미지',
]

REGIONS = ["서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
           "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"]
region_pattern = re.compile(r'^(' + '|'.join(REGIONS) + ')')

print("이미지 인덱싱 중...")
image_index = {}
for image_root in image_roots:
    for dirpath, dirnames, filenames in os.walk(image_root):
        for f in filenames:
            if f.lower().endswith(('.jpg', '.jpeg', '.png')):
                image_index[f] = os.path.join(dirpath, f)

no_region_groups = defaultdict(list)
for dirpath, dirnames, filenames in os.walk(json_root):
    for filename in filenames:
        if not filename.lower().endswith('.json'):
            continue
        try:
            with open(os.path.join(dirpath, filename), 'r', encoding='utf-8') as f:
                data = json.load(f)
            value = data.get('value')
            image_name = data.get('imagePath')
            if not value or not image_name:
                continue
            if region_pattern.match(value):
                continue
            img_path = image_index.get(image_name)
            if img_path:
                no_region_groups[value].append(img_path)
        except Exception:
            continue

MAX_NOREGION_PLATES = 8000
no_region_values = list(no_region_groups.keys())
random.seed(42)
random.shuffle(no_region_values)
selected_noregion_values = no_region_values[:MAX_NOREGION_PLATES]

# ===== 경기 다운샘플링 (기존과 동일) =====
MAX_GYEONGGI = 1500
aihub_region_dir = r'C:\plate_dataset\aihub_region_only'
gt_path = os.path.join(aihub_region_dir, 'gt.txt')

with open(gt_path, 'r', encoding='utf-8') as f:
    all_lines = [l.strip() for l in f if l.strip()]

gyeonggi_lines = [l for l in all_lines if l.split('\t')[1].startswith('경기')]
other_lines = [l for l in all_lines if not l.split('\t')[1].startswith('경기')]
random.shuffle(gyeonggi_lines)
gyeonggi_sampled = gyeonggi_lines[:MAX_GYEONGGI]
final_aihub_region_lines = other_lines + gyeonggi_sampled

# ===== 전체 소스 통합  =====
all_pairs = []

for folder in [r'C:\plate_dataset\cropped_train', r'C:\plate_dataset\cropped_val',
               r'C:\plate_dataset\cropped_cargo', r'C:\plate_dataset\synthetic_truck',
               r'C:\plate_dataset\synthetic_weak_regions',
               r'C:\plate_dataset\synthetic_weak_regions2',
               r'C:\plate_dataset\cropped_cargo2',
               r'C:\plate_dataset\synthetic_weak_letters',  # 새로 추가
               ]:  
    if os.path.exists(folder):
        count = 0
        for f in os.listdir(folder):
            if f.lower().endswith(('.jpg', '.jpeg', '.png')):
                value = os.path.splitext(f)[0]
                all_pairs.append((os.path.join(folder, f), value))
                count += 1
        print(f"{folder}: {count}장")

for line in final_aihub_region_lines:
    filename, value = line.split('\t')
    img_path = os.path.join(aihub_region_dir, filename)
    if os.path.exists(img_path):
        all_pairs.append((img_path, value))

for value in selected_noregion_values:
    img_paths = no_region_groups[value][:1]
    for img_path in img_paths:
        all_pairs.append((img_path, value))

print(f"\n전체 이미지: {len(all_pairs)}장")

# ===== value 기준 그룹화 후 7:2:1 분할 =====
value_groups = defaultdict(list)
for img_path, value in all_pairs:
    value_groups[value].append(img_path)

print(f"고유 번호판 종류: {len(value_groups)}개")

unique_values = list(value_groups.keys())
random.shuffle(unique_values)

train_end = int(len(unique_values) * 0.7)
val_end = train_end + int(len(unique_values) * 0.2)

train_values = set(unique_values[:train_end])
val_values = set(unique_values[train_end:val_end])
test_values = set(unique_values[val_end:])

output_base = r'C:\plate_dataset\cropped_final_v7'   # v6 → v7

def save_split(values_set, split_name):
    out_dir = os.path.join(output_base, split_name)
    os.makedirs(out_dir, exist_ok=True)
    gt_lines = []
    idx = 0
    for value in values_set:
        for img_path in value_groups[value]:
            ext = os.path.splitext(img_path)[1]
            new_filename = f"{value}_{idx}{ext}"
            dst = os.path.join(out_dir, new_filename)
            if not os.path.exists(dst):
                shutil.copy(img_path, dst)
                gt_lines.append(f"{new_filename}\t{value}")
            idx += 1
    with open(os.path.join(out_dir, 'gt.txt'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(gt_lines))
    return len(gt_lines)

train_count = save_split(train_values, 'train')
val_count = save_split(val_values, 'val')
test_count = save_split(test_values, 'test')

print(f"\n=== 최종 결과 ===")
print(f"Train: {train_count}장")
print(f"Val: {val_count}장")
print(f"Test: {test_count}장")