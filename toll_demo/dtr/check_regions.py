import os
import re
from collections import Counter

cargo2_folder = r'C:\plate_dataset\cropped_cargo2'
gt_path = os.path.join(cargo2_folder, 'gt.txt')

REGIONS = ["서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
           "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"]
region_pattern = re.compile(r'^(' + '|'.join(REGIONS) + ')')

with open(gt_path, 'r', encoding='utf-8') as f:
    lines = [l.strip() for l in f if l.strip()]

region_counts = Counter()
no_region = 0
for line in lines:
    parts = line.split('\t')
    if len(parts) == 2:
        m = region_pattern.match(parts[1])
        if m:
            region_counts[m.group(1)] += 1
        else:
            no_region += 1

print(f"전체: {len(lines)}장")
print(f"지역명 없음: {no_region}장")
print("\n지역별 분포:")
for r, c in region_counts.most_common():
    print(f"  {r}: {c}장")