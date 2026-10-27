"""사진 폴더 전체를 인식해서 CSV 로 저장하고, labels.csv(정답)가 있으면 맞는지 비교한다.
  python toll\\recognize_all.py <사진폴더> [--labels labels.csv] [--out recognize_result.csv]
labels.csv 형식:  file,plate   (헤더 포함, UTF-8)
정답 파일이 없으면 결과 CSV 를 손으로 고쳐서 labels.csv 로 쓰면 된다 (초안 생성용).
"""
import os, sys, csv, argparse
from pathlib import Path
import cv2, numpy as np
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE)); sys.path.insert(0, str(HERE.parent))
if os.environ.get('GATE_PIPELINE_DIR'):
    sys.path.insert(0, os.environ['GATE_PIPELINE_DIR'])
sys.path.insert(0, os.getcwd())
EXT = {'.jpg', '.jpeg', '.png', '.bmp', '.webp'}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('folder'); ap.add_argument('--labels', default='')
    ap.add_argument('--out', default='recognize_result.csv')
    a = ap.parse_args()
    import toll_recognize as tr
    tr.load()
    files = sorted(p for p in Path(a.folder).rglob('*') if p.suffix.lower() in EXT)
    labels = {}
    if a.labels:
        with open(a.labels, encoding='utf-8-sig', newline='') as f:
            for r in csv.DictReader(f):
                labels[r['file'].strip()] = r['plate'].strip()
    rows, ok_n, wrong, fail = [], 0, [], []
    for i, p in enumerate(files, 1):
        img = cv2.imdecode(np.fromfile(str(p), np.uint8), cv2.IMREAD_COLOR)
        if img is None:
            r = dict(status='FAILED', reason='UNREADABLE', plate='', confidence=0, ms=0)
        else:
            r = tr.recognize(img)
        truth = labels.get(p.name, '')
        match = '' if not truth else ('O' if r['plate'] == truth else 'X')
        if r['status'] != 'SUCCESS': fail.append((p.name, r['reason'], r['plate']))
        if match == 'O' and r['status'] == 'SUCCESS': ok_n += 1
        if match == 'X': wrong.append((p.name, truth, r['plate'], r['status'], r['confidence']))
        rows.append([p.name, r['status'], r['reason'], r['plate'], f"{r['confidence']:.1f}", r['ms'], truth, match])
        print(f"[{i}/{len(files)}] {p.name}  {r['status']}  {r['plate']}  {r['confidence']:.1f}%", flush=True)
    with open(a.out, 'w', encoding='utf-8-sig', newline='') as f:
        w = csv.writer(f); w.writerow(['file', 'status', 'reason', 'plate', 'confidence', 'ms', 'truth', 'match']); w.writerows(rows)
    print('\n' + '=' * 60)
    print(f"사진 {len(files)}장 | 인식 성공 {len(files) - len(fail)} | 실패 {len(fail)}")
    if labels:
        print(f"정답 일치(성공 판정 포함) {ok_n} / 정답 있는 {sum(1 for r in rows if r[6])}")
        for w_ in wrong: print("  틀림:", w_)
    for f_ in fail: print("  실패:", f_)
    print(f"결과 저장: {a.out}")

if __name__ == '__main__':
    main()
