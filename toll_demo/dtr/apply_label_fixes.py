"""
LMDB의 라벨을 CSV 목록대로 고치거나 학습/평가에서 제외한다. (원본은 자동으로 백업한다)

  python apply_label_fixes.py --lmdb C:/plate_dataset/lmdb/val_v6 --fixes val_v6_fixes.csv

fixes CSV (UTF-8, 첫 줄은 머리글)
  old_label,new_label            <- 라벨로 찾기. 그 라벨을 가진 샘플이 정확히 1개일 때만 적용
  old_label,new_label,lmdb_index <- 인덱스로 찾기 (find_label_suspects.py의 '#번호'). old_label이 현재 라벨과 일치해야 적용
  new_label에 DELETE 를 쓰면 그 샘플을 제외한다 (라벨 앞에 #DELETED# 를 붙여서 LmdbDataset의 문자셋 필터에 걸리게 함.
  이미지는 지우지 않으므로 나중에 되돌릴 수 있다)

적용한 뒤에는 full_predict.py로 평가를 다시 돌려야 새 기준 정확도를 알 수 있다.
"""
import os
import re
import csv
import shutil
import argparse

import lmdb

CHARACTER = "0123456789가나다라마바사아자거너더러머버서어저고노도로모보소오조구누두루무부수우주하허호배충육기북합남울강원경산전대국해광천종세인제"
MAX_LEN = 25
DELETE_PREFIX = '#DELETED#'


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
                         idx=(r.get('lmdb_index') or '').strip().lstrip('#')))
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--lmdb', required=True)
    ap.add_argument('--fixes', required=True)
    ap.add_argument('--character', default=CHARACTER)
    ap.add_argument('--no-backup', action='store_true', help='백업을 만들지 않는다 (권장하지 않음)')
    ap.add_argument('--dry-run', action='store_true', help='실제로 쓰지 않고 무엇이 바뀔지만 보여준다')
    args = ap.parse_args()

    fixes = read_fixes(args.fixes)
    if not fixes:
        print("수정 목록이 비어 있습니다.")
        return

    lmdb_dir = args.lmdb.rstrip('/\\')
    if not args.dry_run and not args.no_backup:
        backup = lmdb_dir + '_backup'
        if os.path.exists(backup):
            print(f"백업 폴더가 이미 있어 그대로 둡니다 (처음 상태 보존): {backup}")
        else:
            shutil.copytree(lmdb_dir, backup)
            print(f"백업 완료: {backup}")

    # map_size는 실제 파일 크기와 무관하게 넉넉한 고정값을 쓴다. (파일 크기 * 배수로 계산하면,
    # 원래 만들 때 이미 큰 map_size로 생성된 LMDB에서 그 파일 크기 자체가 커서 결과값이
    # 비현실적으로 커지고, Windows에서 "매개변수가 틀렸습니다" 오류가 날 수 있다.)
    # 가상 주소 공간만 예약하는 것이라 실제 디스크를 그만큼 쓰지 않으니 넉넉하게 잡아도 된다.
    map_size = 2 * 1024 * 1024 * 1024  # 2GB
    env = lmdb.open(lmdb_dir, map_size=map_size, readonly=args.dry_run, lock=not args.dry_run)

    # 현재 라벨 전체를 읽어 라벨 -> 인덱스 목록 사전을 만든다
    labels, by_label = {}, {}
    with env.begin(write=False) as txn:
        n = int(txn.get('num-samples'.encode()))
        for i in range(1, n + 1):
            v = txn.get(b'label-%09d' % i)
            if v is None:
                continue
            lab = v.decode('utf-8')
            labels[i] = lab
            by_label.setdefault(lab, []).append(i)

    bad = re.compile(f'[^{re.escape(args.character)}]')
    plan, skipped = [], []
    for f in fixes:
        old, new = f['old'], f['new']
        if not old or not new:
            skipped.append((f, '라벨이 비어 있음'))
            continue
        if f['idx']:
            try:
                i = int(f['idx'])
            except ValueError:
                skipped.append((f, '인덱스가 숫자가 아님'))
                continue
            if labels.get(i) != old:
                skipped.append((f, f"인덱스 {i}의 현재 라벨은 '{labels.get(i)}'이라 old_label과 다름"))
                continue
        else:
            cands = by_label.get(old, [])
            if len(cands) != 1:
                skipped.append((f, f"이 라벨을 가진 샘플이 {len(cands)}개라 특정할 수 없음 (lmdb_index 열을 채우세요)"))
                continue
            i = cands[0]
        if new.upper() == 'DELETE':
            plan.append((i, old, DELETE_PREFIX + old))
        else:
            if len(new) > MAX_LEN or bad.search(new.lower()):
                skipped.append((f, f"새 라벨 '{new}'에 문자셋 밖 글자가 있거나 너무 김"))
                continue
            plan.append((i, old, new))

    print(f"\n적용 예정 {len(plan)}건, 건너뜀 {len(skipped)}건")
    for i, old, new in plan:
        shown = '제외(DELETE)' if new.startswith(DELETE_PREFIX) else new
        print(f"  #{i}: {old} -> {shown}")
    for f, why in skipped:
        print(f"  [건너뜀] {f['old']} -> {f['new']}: {why}")

    if args.dry_run:
        print("\n--dry-run 이라 실제로는 바꾸지 않았습니다.")
        return
    with env.begin(write=True) as txn:
        for i, old, new in plan:
            txn.put(b'label-%09d' % i, new.encode('utf-8'))
    env.close()
    print("\nLMDB에 반영했습니다. full_predict.py로 평가를 다시 돌리세요.")


if __name__ == '__main__':
    main()