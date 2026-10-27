import re
from collections import Counter, defaultdict

log_path = r'C:\plate_dataset\deep-text-recognition-benchmark\saved_models\TPS-ResNet-BiLSTM-CTC-v2-full\log_train.txt'

with open(log_path, 'rb') as f:
    text = f.read().decode('cp949', errors='replace')

lines = text.split('\r\n')

false_gt_list = []       # 틀렸던 정답 번호판 전체
char_confusion = Counter()  # 어떤 글자가 어떤 글자로 헷갈렸는지

for line in lines:
    parts = line.split('|')
    if len(parts) == 3 and 'False' in parts[2]:
        gt = parts[0].strip()
        pred = parts[1].strip()
        false_gt_list.append(gt)

        if len(gt) == len(pred):
            for g_char, p_char in zip(gt, pred):
                if g_char != p_char:
                    char_confusion[(g_char, p_char)] += 1

print(f"틀린 번호판 총 {len(false_gt_list)}개")
print("\n틀린 번호판 목록:")
for gt in false_gt_list:
    print(f"  {gt}")

print("\n가장 자주 헷갈린 글자 쌍:")
for (g, p), count in char_confusion.most_common(20):
    print(f"  {g} → {p} : {count}회")

# 자주 틀린 개별 글자 추출 (정답 기준)
weak_chars = Counter()
for (g, p), count in char_confusion.items():
    weak_chars[g] += count

print("\n자주 오답을 유발하는 정답 글자 (우선 보강 대상):")
for char, count in weak_chars.most_common(15):
    print(f"  {char}: {count}회")