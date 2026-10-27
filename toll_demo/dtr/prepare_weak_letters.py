"""
파일명이 이미 "01무3527.jpg", "02오8951-20.jpg"처럼 정상적으로 되어 있는 폴더용.
(인코딩이 깨진 경우는 decode_hashU_labels.py를 대신 쓰세요.)

파일명 끝의 "-숫자"는 같은 번호판을 다른 각도로 찍은 사본 번호이며, 정답에는 포함되지 않는다.
  01무3527.jpg      -> 정답: 01무3527
  02오8951-20.jpg   -> 정답: 02오8951 (뒤의 "-20"은 제외)

  python prepare_weak_letters.py --input C:/Users/1/Desktop/하허무부오호 --out C:/plate_dataset/synthetic_weak_letters

출력: out 폴더에 "정답_순번.jpg" 형식으로 복사 + gt.txt(파일명<TAB>정답) 생성
      -> 기존 병합 스크립트의 source_folders 목록에 이 폴더를 그대로 추가하면 된다.
"""
import os
import re
import shutil
import argparse
from collections import defaultdict

PATTERN = re.compile(r'^(.+?)(?:-\d+)?\.(jpe?g|png)$', re.IGNORECASE)
CHARACTER = "0123456789가나다라마바사아자거너더러머버서어저고노도로모보소오조구누두루무부수우주하허호배충육기북합남울강원경산전대국해광천종세인제"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--input', required=True, help='정상적으로 이름 붙은 파일들이 있는 폴더')
    ap.add_argument('--out', required=True)
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    files = sorted(os.listdir(args.input))

    decoded, fail = [], []
    for fn in files:
        m = PATTERN.match(fn)
        if not m:
            fail.append(f"{fn}  (이미지 파일이 아니거나 형식을 인식 못함)")
            continue
        value = m.group(1)
        if not value or any(c not in CHARACTER for c in value):
            fail.append(f"{fn}  (정답으로 추정한 값 '{value}'에 문자셋 밖 글자가 있음 - 직접 확인 필요)")
            continue
        decoded.append((fn, value))

    print(f"전체 {len(files)}개 중 인식 성공 {len(decoded)}개, 실패 {len(fail)}개")
    if fail:
        print("\n실패한 파일 (직접 확인 필요):")
        for f in fail:
            print(f"  {f}")

    counters = defaultdict(int)
    gt_lines = []
    for fn, value in decoded:
        idx = counters[value]
        counters[value] += 1
        new_name = f"{value}_{idx}.jpg"
        shutil.copy(os.path.join(args.input, fn), os.path.join(args.out, new_name))
        gt_lines.append(f"{new_name}\t{value}")

    with open(os.path.join(args.out, 'gt.txt'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(gt_lines))

    print(f"\n저장 완료: {len(gt_lines)}장 -> {args.out}")
    print(f"고유 번호판 종류: {len(counters)}개")

    from collections import Counter as Cnt
    letter_counts = Cnt()
    for _, value in decoded:
        for ch in value:
            if ch in "허하무부오호":
                letter_counts[ch] += 1
    print("\n목표 글자별 등장 횟수:")
    for ch in "허하무부오호":
        print(f"  {ch}: {letter_counts.get(ch, 0)}회")


if __name__ == '__main__':
    main()
