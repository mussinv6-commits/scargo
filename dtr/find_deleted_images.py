"""
직접 눈으로 보고 지운 이미지가 무엇이었는지 찾는다.

원리: LMDB를 만들 때 쓴 gt.txt(원본 폴더의 전체 목록, 지우기 전 상태)와
지금 그 폴더에 실제로 남아있는 파일을 비교한다. gt.txt에는 있는데 지금 폴더에 없으면 지운 것이다.

  python find_deleted_images.py --folder C:/plate_dataset/cropped_final_v6/val --out deleted_val.csv

출력: deleted_val.csv (old_label,new_label 형식, new_label은 전부 DELETE로 채워짐)
      -> 이 파일을 그대로 apply_label_fixes.py --fixes 뒤에 넣으면 된다.

주의: gt.txt가 그 폴더에 남아있어야 한다 (LMDB 만들 때 같이 생성된 그 파일). gt.txt 자체를 지우셨다면
      이 방법은 쓸 수 없고, LMDB 안의 라벨 목록과 직접 대조해야 한다 (아래 --lmdb 옵션 참고).
"""
import os
import csv
import argparse

IMG_EXT = ('.jpg', '.jpeg', '.png', '.bmp')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--folder', required=True, help='LMDB를 만들 때 쓴 원본 폴더 (gt.txt가 있는 곳)')
    ap.add_argument('--gt', default='gt.txt', help='목록 파일 이름 (기본: gt.txt)')
    ap.add_argument('--out', default='deleted_fixes.csv')
    ap.add_argument('--lmdb', help='gt.txt가 없을 때: 대신 LMDB 자체와 비교한다 (find_label_suspects.py와 같은 폴더에 있어야 함)')
    args = ap.parse_args()

    gt_path = os.path.join(args.folder, args.gt)

    if os.path.exists(gt_path):
        raw = open(gt_path, 'rb').read()
        for enc in ('utf-8-sig', 'cp949'):
            try:
                text = raw.decode(enc)
                break
            except UnicodeDecodeError:
                continue
        original = {}
        for line in text.splitlines():
            parts = line.rstrip('\r\n').split('\t')
            if len(parts) == 2:
                original[parts[0]] = parts[1]
        print(f"gt.txt 기준 원래 파일 수: {len(original)}")
    elif args.lmdb:
        import sys
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        import find_label_suspects as fls
        import lmdb
        env = lmdb.open(args.lmdb, readonly=True, lock=False, readahead=False, meminit=False)
        idx_list = fls.lmdb_index_list(env, fls.CHARACTER)
        # LMDB에는 파일명이 따로 저장되지 않으므로, 라벨을 '파일명 추정'으로 쓴다 (파일명이 온전한 라벨 형태일 때만 정확함)
        original = {f"{label}.jpg": label for _, label in idx_list}
        print(f"LMDB 기준 원래 샘플 수: {len(original)} (파일명은 '라벨.jpg'로 추정)")
    else:
        print(f"{gt_path} 를 찾을 수 없습니다. --gt 로 실제 파일명을 지정하거나, --lmdb 로 LMDB와 비교하세요.")
        return

    current = {f for f in os.listdir(args.folder) if f.lower().endswith(IMG_EXT)}
    print(f"지금 폴더에 남아있는 이미지 수: {len(current)}")

    deleted = [(fn, lab) for fn, lab in original.items() if fn not in current]
    print(f"\n지운 것으로 보이는 파일: {len(deleted)}개")
    for fn, lab in deleted:
        print(f"  {fn}  (라벨: {lab})")

    if not deleted:
        print("\n차이가 없습니다. gt.txt가 이미 최신 상태이거나, 다른 폴더를 지우신 것일 수 있습니다.")
        return

    with open(args.out, 'w', encoding='utf-8-sig', newline='') as f:
        w = csv.writer(f)
        w.writerow(['old_label', 'new_label'])
        for fn, lab in deleted:
            w.writerow([lab, 'DELETE'])
    print(f"\n저장: {args.out}")
    print("이 CSV를 열어서 정말 지우려던 게 맞는지 한 번 확인한 뒤,")
    print(f"  python apply_label_fixes.py --lmdb <해당 LMDB 경로> --fixes {args.out} --dry-run")
    print("으로 먼저 확인하고 반영하세요.")


if __name__ == '__main__':
    main()
