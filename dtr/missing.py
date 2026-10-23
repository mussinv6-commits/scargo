import os

output_base = r'C:\plate_dataset\cropped_final_v2'  # 실제 경로 확인 필요

existing = set("0123456789가나다라마바사아자거너더러머버서어저고노도로모보소오조구누두루무부수우주하허호배충육기북합남울강원경산전대국해")

new_chars = set()
for split in ['train', 'val', 'test']:
    gt_path = os.path.join(output_base, split, 'gt.txt')
    with open(gt_path, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split('\t')
            if len(parts) == 2:
                new_chars.update(parts[1])

missing = new_chars - existing
print(f"문자셋에 없는 새 글자: {missing}")