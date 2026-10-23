import os
import json
import shutil
import re
from collections import defaultdict

# ===== 실제 경로로 수정 =====
json_root = r'C:/Users/1\Downloads/103.자동차_차종-연식-번호판_인식용_데이터/추가_데이터_보완_건_211229(폴더구조수정)/1.Training'          # JSON(라벨) 파일들이 있는 최상위 폴더
image_roots = [
    r'C:/Users/1\Downloads/103.자동차_차종-연식-번호판_인식용_데이터/추가_데이터_보완_건_211229(폴더구조수정)/1.Training/자동차번호판OCR_이미지',           # 이미지 파일들이 있는 폴더 (여러 개면 리스트에 추가)
]

output_dir = r'C:\plate_dataset\aihub_region_only'
os.makedirs(output_dir, exist_ok=True)

REGIONS = ["서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
           "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"]
region_pattern = re.compile(r'^(' + '|'.join(REGIONS) + ')')

MAX_PER_PLATE = 3

# ===== 1단계: 이미지 전체를 파일명 기준으로 인덱싱 (한 번만) =====
print("이미지 인덱싱 중...")
image_index = {}
for image_root in image_roots:
    for dirpath, dirnames, filenames in os.walk(image_root):
        for f in filenames:
            if f.lower().endswith(('.jpg', '.jpeg', '.png')):
                image_index[f] = os.path.join(dirpath, f)

print(f"인덱싱된 이미지 개수: {len(image_index)}")

# ===== 2단계: JSON 스캔하면서 지역명 필터링 + 이미지 매칭 =====
value_groups = defaultdict(list)  # value -> [img_path, ...]
skipped_no_region = 0
fail_missing_field = 0
fail_missing_image = 0

for dirpath, dirnames, filenames in os.walk(json_root):
    for filename in filenames:
        if not filename.lower().endswith('.json'):
            continue

        json_path = os.path.join(dirpath, filename)
        try:
            with open(json_path, 'r', encoding='utf-8') as f:
                data = json.load(f)

            value = data.get('value')
            image_name = data.get('imagePath')

            if not value or not image_name:
                fail_missing_field += 1
                continue

            if not region_pattern.match(value):
                skipped_no_region += 1
                continue

            # 인덱싱된 이미지에서 찾기
            img_path = image_index.get(image_name)
            if not img_path:
                fail_missing_image += 1
                continue

            value_groups[value].append(img_path)

        except Exception:
            fail_missing_field += 1

print(f"\n지역명 없어서 제외: {skipped_no_region}장")
print(f"필드 누락 실패: {fail_missing_field}건")
print(f"이미지 못 찾음: {fail_missing_image}건")
print(f"지역명 있는 고유 번호판 종류: {len(value_groups)}개")

# ===== 3단계: 번호판당 최대 N장 제한 + 저장 =====
gt_lines = []
success = 0

for value, img_paths in value_groups.items():
    selected = img_paths[:MAX_PER_PLATE]
    for idx, img_path in enumerate(selected):
        new_filename = f"{value}_{idx}.jpg"
        dst_path = os.path.join(output_dir, new_filename)
        if os.path.exists(dst_path):
            continue
        shutil.copy(img_path, dst_path)
        gt_lines.append(f"{new_filename}\t{value}")
        success += 1

with open(os.path.join(output_dir, 'gt.txt'), 'w', encoding='utf-8') as f:
    f.write('\n'.join(gt_lines))

print(f"\n최종 저장된 이미지: {success}장")

# ===== 지역별 분포 =====
region_counts = defaultdict(int)
for line in gt_lines:
    v = line.split('\t')[1]
    m = region_pattern.match(v)
    if m:
        region_counts[m.group(1)] += 1

print("\n=== 지역별 데이터 수 ===")
for region, count in sorted(region_counts.items(), key=lambda x: -x[1]):
    print(f"{region}: {count}장")