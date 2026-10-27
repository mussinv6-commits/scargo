import os

# 데이터셋 기본 경로 (본인 경로에 맞게 확인)
output_base = r'C:\plate_dataset\cropped_final_v2'

# 제외하고 싶은 글자 목록 (터미널에 떴던 missing 글자들)
unwanted_chars = set({'초', '관', '계', '김', '문', '인', '카', '종', '동', '천', 
                      '홀', '세', '금', '파', '안', '용', '광', '중', '영', '포', 
                      '차', '제', '등', '타', '평', '양', '연', '미', '추', '작', '악'})

for split in ['train', 'val', 'test']:
    gt_path = os.path.join(output_base, split, 'gt.txt')
    if not os.path.exists(gt_path):
        continue
        
    cleaned_lines = []
    removed_count = 0
    
    with open(gt_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
        
    for line in lines:
        parts = line.strip().split('\t')
        if len(parts) == 2:
            img_rel_path, label = parts[0], parts[1]
            
            # 라벨에 삭제 대상 글자가 하나라도 포함되어 있는지 확인
            if any(char in label for char in unwanted_chars):
                # 해당 이미지 파일 실제 삭제
                img_full_path = os.path.join(output_base, split, img_rel_path)
                # 만약 경로 형태가 다르면 상위 경로 조정 필요
                if not os.path.exists(img_full_path):
                    # gt.txt에 이미지 파일명만 적혀있는 경우
                    img_full_path = os.path.join(output_base, split, os.path.basename(img_rel_path))
                    
                if os.path.exists(img_full_path):
                    os.remove(img_full_path)
                
                removed_count += 1
            else:
                cleaned_lines.append(line)
                
    # 정제된 내용으로 gt.txt 덮어쓰기
    with open(gt_path, 'w', encoding='utf-8') as f:
        f.writelines(cleaned_lines)
        
    print(f"[{split}] 정제 완료: 총 {removed_count}개의 불필요한 이미지/라벨 삭제됨.")