"""
val/test는 이미 라벨을 정리해둔 것(val_v6, test_v6)을 그대로 유지하고, train만 새로 만든다.

왜 필요한가: 전체를 모아서 7:2:1로 다시 나누면 val/test가 완전히 다른 샘플로 바뀌어서,
지금까지 정리해둔 val_v6/test_v6 라벨과, 그걸 기준으로 비교해온 v6/v7 결과가 전부 무의미해진다.
이 스크립트는 val_v6/test_v6에 들어있는 번호판 값은 그대로 건드리지 않고 빼놓은 뒤,
나머지 전부(새로 추가한 데이터 포함)를 train으로만 모은다. (val/test 쪽은 다시 만들 필요가 없다)

  python build_train_keep_eval.py --sources 폴더1 폴더2 ... --val-lmdb C:/plate_dataset/lmdb/val_v6 ^
      --test-lmdb C:/plate_dataset/lmdb/test_v6 --out C:/plate_dataset/cropped_final_v7_train

--sources 에는 지금까지 병합 스크립트의 source_folders에 넣었던 폴더들을 전부(새로 추가한 것 포함) 넣으면 된다.
같은 번호판(value)이 여러 장 있으면 전부 train에 포함된다 (val/test에 속한 값만 제외).
"""
import os
import argparse

import lmdb

IMG_EXT = ('.jpg', '.jpeg', '.png', '.bmp')
DELETE_PREFIX = '#DELETED#'


def reserved_values(lmdb_path):
    """이미 만들어둔 val/test LMDB에서 쓰이고 있는 번호판 값들 (삭제 표시된 것은 제외)"""
    env = lmdb.open(lmdb_path, readonly=True, lock=False, readahead=False, meminit=False)
    values = set()
    with env.begin(write=False) as txn:
        n = int(txn.get('num-samples'.encode()))
        for i in range(1, n + 1):
            v = txn.get(b'label-%09d' % i)
            if v is None:
                continue
            label = v.decode('utf-8')
            if not label.startswith(DELETE_PREFIX):
                values.add(label)
    return values


def collect(folders):
    """(이미지 경로, 값) 쌍을 전부 모은다. 파일명이 '값.jpg' 또는 '값_숫자.jpg' 형식인 것을 값으로 쓴다."""
    pairs = []
    for folder in folders:
        if not os.path.isdir(folder):
            print(f"  [건너뜀] 폴더 없음: {folder}")
            continue
        gt_path = os.path.join(folder, 'gt.txt')
        count = 0
        if os.path.exists(gt_path):
            raw = open(gt_path, 'rb').read()
            for enc in ('utf-8-sig', 'cp949'):
                try:
                    text = raw.decode(enc)
                    break
                except UnicodeDecodeError:
                    continue
            for line in text.splitlines():
                parts = line.rstrip('\r\n').split('\t')
                if len(parts) == 2:
                    img_path = os.path.join(folder, parts[0])
                    if os.path.exists(img_path):
                        pairs.append((img_path, parts[1]))
                        count += 1
        else:
            for f in os.listdir(folder):
                if f.lower().endswith(IMG_EXT):
                    value = os.path.splitext(f)[0]
                    pairs.append((os.path.join(folder, f), value))
                    count += 1
        print(f"  {folder}: {count}장")
    return pairs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--sources', nargs='+', required=True)
    ap.add_argument('--val-lmdb', required=True)
    ap.add_argument('--test-lmdb', required=True)
    ap.add_argument('--out', required=True)
    args = ap.parse_args()

    print("val/test에서 쓰이고 있는 번호판 값 확인 중...")
    val_values = reserved_values(args.val_lmdb)
    test_values = reserved_values(args.test_lmdb)
    overlap = val_values & test_values
    print(f"val: {len(val_values)}종류, test: {len(test_values)}종류"
          + (f" (겹치는 값 {len(overlap)}개 있음 - 원래 val/test 자체가 겹치면 안 되니 확인 필요)" if overlap else ""))
    reserved = val_values | test_values

    print("\n전체 소스 폴더 수집 중...")
    all_pairs = collect(args.sources)
    print(f"\n전체 이미지: {len(all_pairs)}장")

    train_pairs = [(p, v) for p, v in all_pairs if v not in reserved]
    skipped = len(all_pairs) - len(train_pairs)
    print(f"val/test에 이미 쓰인 값이라 제외: {skipped}장")
    print(f"train으로 갈 이미지: {len(train_pairs)}장")

    os.makedirs(args.out, exist_ok=True)
    counters = {}
    gt_lines = []
    for img_path, value in train_pairs:
        counters[value] = counters.get(value, 0) + 1
        idx = counters[value] - 1
        ext = os.path.splitext(img_path)[1] or '.jpg'
        new_name = f"{value}_{idx}{ext}"
        dst = os.path.join(args.out, new_name)
        if not os.path.exists(dst):
            import shutil
            shutil.copy(img_path, dst)
            gt_lines.append(f"{new_name}\t{value}")

    with open(os.path.join(args.out, 'gt.txt'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(gt_lines))

    print(f"\n=== 완료 ===")
    print(f"Train: {len(gt_lines)}장 (고유 번호판 {len(counters)}종류) -> {args.out}")
    print(f"Val/Test는 건드리지 않았습니다. (val_v6, test_v6 그대로 사용)")
    print(f"\n다음: create_lmdb_dataset.py --inputPath {args.out}/ --gtFile {args.out}/gt.txt --outputPath <새 train LMDB 경로>")


if __name__ == '__main__':
    main()
