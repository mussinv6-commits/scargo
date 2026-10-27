"""
같은 평가셋에서 OCR 모델 두 개를 짝지어 비교한다.

입력: full_predict.py가 만든 파일 두 개 (한 줄: 정답<TAB>예측<TAB>True/False). 두 파일의 줄 순서가 같아야 한다.
  python compare_models.py full_predictions_old.txt full_predictions_new.txt

출력: 각 모델 정확도와 95% 신뢰구간, 두 모델이 다르게 맞힌 건수(b, c), McNemar 검정 p값, 예시
  p값이 작으면(보통 0.05 미만) 두 모델의 차이가 우연이 아닐 가능성이 높다.
"""
import sys
import math


def read_pred(path):
    raw = open(path, 'rb').read()
    for enc in ('utf-8-sig', 'cp949'):
        try:
            text = raw.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    rows = []
    for line in text.splitlines():
        parts = line.rstrip('\r\n').split('\t')
        if len(parts) >= 3:
            rows.append((parts[0], parts[1], parts[2].strip() == 'True'))
    return rows


def wilson(k, n, z=1.96):
    if n == 0:
        return 0.0, 0.0
    p = k / n
    d = 1 + z * z / n
    c = (p + z * z / (2 * n)) / d
    h = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d
    return (c - h) * 100, (c + h) * 100


def mcnemar_exact(b, c):
    """b: 기존만 맞힘, c: 새 모델만 맞힘. 양측 정확 검정(이항)"""
    n = b + c
    if n == 0:
        return 1.0
    k = min(b, c)
    p = sum(math.comb(n, i) for i in range(k + 1)) / (2 ** n) * 2
    return min(1.0, p)


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return
    a, b_ = read_pred(sys.argv[1]), read_pred(sys.argv[2])
    if len(a) != len(b_):
        print(f"두 파일의 줄 수가 다릅니다 ({len(a)} vs {len(b_)}). 같은 평가셋(같은 LMDB, shuffle 없음)에서 만든 파일이어야 합니다.")
        return
    mism = sum(1 for x, y in zip(a, b_) if x[0] != y[0])
    if mism:
        print(f"정답 열이 서로 다른 줄이 {mism}개 있습니다. 같은 순서의 평가셋이 아닐 수 있어 비교할 수 없습니다.")
        return

    n = len(a)
    ka, kb = sum(x[2] for x in a), sum(y[2] for y in b_)
    for name, k, path in (('모델 A', ka, sys.argv[1]), ('모델 B', kb, sys.argv[2])):
        lo, hi = wilson(k, n)
        print(f"{name}: {k}/{n} = {k / n * 100:.2f}%  (95% 신뢰구간 {lo:.2f}~{hi:.2f}%)  [{path}]")

    only_a = [(x, y) for x, y in zip(a, b_) if x[2] and not y[2]]     # A만 맞힘
    only_b = [(x, y) for x, y in zip(a, b_) if y[2] and not x[2]]     # B만 맞힘
    both_wrong = sum(1 for x, y in zip(a, b_) if not x[2] and not y[2])
    print(f"\nA만 맞힘 {len(only_a)}건 | B만 맞힘 {len(only_b)}건 | 둘 다 틀림 {both_wrong}건")
    p = mcnemar_exact(len(only_a), len(only_b))
    print(f"McNemar 정확 검정 p = {p:.4f}  ->  " +
          ("차이가 우연이 아닐 가능성이 높음" if p < 0.05 else "이 표본으로는 두 모델의 차이를 구분할 수 없음"))
    print(f"둘 다 틀린 {both_wrong}건은 모델 문제가 아니라 라벨 오류이거나 읽을 수 없는 이미지일 수 있으니 눈으로 확인해보세요.")

    def show(title, items):
        print(f"\n{title} (최대 15건)")
        for x, y in items[:15]:
            print(f"  정답 {x[0]}  | A 예측 {x[1]}  | B 예측 {y[1]}")
    show("A만 맞힌 예", only_a)
    show("B만 맞힌 예", only_b)
    both = [(x, y) for x, y in zip(a, b_) if not x[2] and not y[2]]
    show("둘 다 틀린 예", both)


if __name__ == '__main__':
    main()
