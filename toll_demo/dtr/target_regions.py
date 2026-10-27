import os

base = r'C:\plate_dataset\cropped_final_712'
target_regions = ['인천', '광주', '세종', '제주']

for split in ['train', 'val', 'test']:
    gt_path = os.path.join(base, split, 'gt.txt')
    counts = {r: 0 for r in target_regions}
    
    with open(gt_path, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split('\t')
            if len(parts) == 2:
                text = parts[1]
                for r in target_regions:
                    if text.startswith(r):
                        counts[r] += 1
    
    print(f"[{split}] {counts}")