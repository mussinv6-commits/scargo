import os
import json
import re

source_root = r'C:/Users/1\Downloads/103.자동차_차종-연식-번호판_인식용_데이터/추가_데이터_보완_건_211229(폴더구조수정)/1.Training'

REGIONS = ["서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
           "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"]
region_pattern = re.compile(r'^(' + '|'.join(REGIONS) + ')')

no_region_samples = []
fail_samples = []  # (파일경로, 에러메시지)

count = 0
for dirpath, dirnames, filenames in os.walk(source_root):
    for filename in filenames:
        if not filename.lower().endswith('.json'):
            continue
        if len(no_region_samples) >= 20 and len(fail_samples) >= 20:
            break

        json_path = os.path.join(dirpath, filename)
        try:
            with open(json_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
            value = data.get('value')
            image_name = data.get('imagePath')

            if not value or not image_name:
                if len(fail_samples) < 20:
                    fail_samples.append((json_path, f"필드 없음: value={value}, imagePath={image_name}"))
                continue

            if not region_pattern.match(value):
                if len(no_region_samples) < 20:
                    no_region_samples.append(value)

        except Exception as e:
            if len(fail_samples) < 20:
                fail_samples.append((json_path, f"{type(e).__name__}: {e}"))

print("=== '지역명 없음'으로 분류된 실제 value 샘플 20개 ===")
for v in no_region_samples:
    print(f"  {v}")

print("\n=== '오류'로 분류된 실제 원인 샘플 20개 ===")
for path, err in fail_samples:
    print(f"  {path}\n    → {err}")