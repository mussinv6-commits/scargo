"""
apply_label_fixes.py가 "이 라벨을 가진 샘플이 N개라 특정할 수 없음"으로 건너뛴 DELETE 항목들을 자동으로 해결한다.

전제: 삭제하려는 라벨이 CSV(예: find_deleted_images.py의 출력)에 N번 나온다면, 그 라벨의 사본
(같은 번호판을 여러 각도로 찍은 사진들) 전체를 지운 것이다. 이 라벨이 LMDB 안에도 정확히 N개 있으면
"그 라벨 전체를 지운다"는 뜻이 확실하므로, 각 사본의 lmdb_index를 찾아 DELETE 행으로 풀어서 새 CSV를 만든다.
개수가 서로 다르면(예: 3장 중 1장만 지움) 자동으로 판단할 수 없으므로 목록만 보여주고 건너뛴다 — 이 경우
남은 사본 이미지들과 LMDB 안의 이미지를 직접 대조해서 --lmdb-index를 수동으로 채워야 한다.

  python resolve_duplicate_deletions.py --lmdb C:/plate_dataset/lmdb/val_v6 --fixes deleted_val.csv --out deleted_val_resolved.csv

--fixes 는 old_label,new_label 형식이고, DELETE가 아닌 행(재라벨)은 그대로 통과시킨다.
"""
import os
import csv
import argparse
from collections import Counter, defaultdict

import lmdb


def read_fixes(path):
    raw = open(path, 'rb').read()
    for enc in ('utf-8-sig', 'cp949'):
        try:
            text = raw.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    rows = []
    for r in csv.DictReader(text.splitlines()):
        rows.append(dict(old=(r.get('old_label') or '').strip(), new=(r.get('new_label') or '').strip(),
                         idx=(r.get('lmdb_index') or '').strip()))
    return rows


def lmdb_label_index(env):
    """라벨 -> [인덱스, ...] (LMDB 전체, 필터링 없이 원본 그대로)"""
    by_label = defaultdict(list)
    with env.begin(write=False) as txn:
        n = int(txn.get('num-samples'.encode()))
        for i in range(1, n + 1):
            v = txn.get(b'label-%09d' % i)
            if v is not None:
                by_label[v.decode('utf-8')].append(i)
    return by_label


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--lmdb', required=True)
    ap.add_argument('--fixes', required=True)
    ap.add_argument('--out', default='resolved_fixes.csv')
    args = ap.parse_args()

    rows = read_fixes(args.fixes)
    env = lmdb.open(args.lmdb, readonly=True, lock=False, readahead=False, meminit=False)
    by_label = lmdb_label_index(env)

    # 이미 lmdb_index가 채워졌거나 DELETE가 아닌 행은 그대로 통과
    passthrough = [r for r in rows if r['idx'] or r['new'].upper() != 'DELETE']
    delete_rows = [r for r in rows if not r['idx'] and r['new'].upper() == 'DELETE']

    wanted = Counter(r['old'] for r in delete_rows)   # CSV에 그 라벨이 몇 번 나왔는지(=지운 사본 수)
    resolved, unresolved, not_found = [], [], []

    for label, want_n in wanted.items():
        have = by_label.get(label, [])
        if not have:
            not_found.append((label, want_n))
        elif len(have) == want_n:
            for idx in have:
                resolved.append((label, idx))
        else:
            unresolved.append((label, want_n, len(have), have))

    print(f"통과(그대로 유지): {len(passthrough)}건")
    print(f"자동 해결(라벨 전체 삭제로 확정): {len(resolved)}건 ({len(wanted) - len(unresolved) - len(not_found)}개 라벨)")
    if not_found:
        print(f"\n[참고] 이미 처리된 것으로 보여 건드리지 않음 (LMDB에 그 라벨이 없음): {len(not_found)}개 라벨")
        for label, want_n in not_found:
            print(f"  {label} (삭제 요청 {want_n}건)")
    if unresolved:
        print(f"\n[주의] 자동으로 판단할 수 없는 라벨: {len(unresolved)}개 (삭제 요청 수와 LMDB 보유 수가 다름)")
        for label, want_n, have_n, idxs in unresolved:
            print(f"  {label}: 삭제 요청 {want_n}건, LMDB에는 {have_n}개 (인덱스: {idxs}) -> 수동으로 lmdb_index를 정해서 CSV에 넣으세요")

    with open(args.out, 'w', encoding='utf-8-sig', newline='') as f:
        w = csv.writer(f)
        w.writerow(['old_label', 'new_label', 'lmdb_index'])
        for r in passthrough:
            w.writerow([r['old'], r['new'], r['idx']])
        for label, idx in resolved:
            w.writerow([label, 'DELETE', idx])
    print(f"\n저장: {args.out}")
    print(f"  python apply_label_fixes.py --lmdb {args.lmdb} --fixes {args.out} --dry-run")


if __name__ == '__main__':
    main()
