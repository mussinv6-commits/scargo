"""정답 번호(labels.csv)로 가상 회사/차량/제원을 DB 에 넣는다. 여러 번 실행해도 안전. 이미 있는 차량은 그대로 두고, 없는 차량과 비어 있는 제원만 채운다.
  python toll\\seed_virtual_data.py labels.csv --db "postgresql://user:pw@host:5432/dbname" [--seed 1]
--unregistered N : 번호 중 N대는 일부러 등록하지 않는다 ('미등록 차량' 시연용). 빠지는 번호가 매번 같도록 seed 고정.
이 스크립트는 companies / trucks / vehicle_specs 에만 쓴다.
"""
import csv, random, argparse, sys
import psycopg2

COMPANIES = [('한빛물류', '서울특별시 강서구 공항대로 100', '물류·운송', '김한빛'),
             ('대한로지스', '부산광역시 강서구 녹산산단 20', '화물운송', '박대한'),
             ('서해운수', '인천광역시 중구 항동 7가 11', '화물운송', '이서해'),
             ('동진컨테이너', '경기도 평택시 포승읍 평택항로 55', '컨테이너운송', '최동진'),
             ('남도화물', '광주광역시 광산구 평동산단 8', '화물운송', '정남도'),
             ('태광트랜스', '대전광역시 대덕구 문평동 33', '물류·운송', '윤태광')]
# (truck_type, category, semi, empty_kg, gross_kg, payload_kg, axles, tires, chassis_m)
SPECS = [('카고트럭', 'GENERAL_TRUCK', False, 5200, 16000, 10800, 3, 10, 1.10),
         ('윙바디', 'GENERAL_TRUCK', False, 7800, 25000, 17200, 3, 10, 1.20),
         ('덤프트럭', 'GENERAL_TRUCK', False, 11500, 25000, 13500, 3, 10, 1.30),
         ('컨테이너 트랙터', 'CONTAINER_TRUCK', True, 8600, 44000, 35400, 5, 18, 1.25),
         ('탑차', 'GENERAL_TRUCK', False, 4300, 12000, 7700, 2, 6, 1.05)]

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('labels'); ap.add_argument('--db', required=True)
    ap.add_argument('--unregistered', type=int, default=0); ap.add_argument('--seed', type=int, default=1)
    a = ap.parse_args()
    with open(a.labels, encoding='utf-8-sig', newline='') as f:
        plates = sorted({r['plate'].strip() for r in csv.DictReader(f) if r['plate'].strip()})
    rnd = random.Random(a.seed)
    skip = set(rnd.sample(plates, min(a.unregistered, len(plates))))
    con = psycopg2.connect(a.db); cur = con.cursor()
    cids = []
    for i, (n, ad, ind, rep) in enumerate(COMPANIES):
        biz = f"{110 + i}-81-{10000 + i * 137:05d}"
        cur.execute("""INSERT INTO companies(company_name,address,business_no,industry_type,representative_name)
                       VALUES(%s,%s,%s,%s,%s) ON CONFLICT (company_name,address) DO UPDATE SET industry_type=EXCLUDED.industry_type
                       RETURNING company_id""", (n, ad, biz, ind, rep))
        cids.append(cur.fetchone()[0])
    n_ok = 0
    for p in plates:
        if p in skip: continue
        rnd2 = random.Random(f"{a.seed}{p}")                   # 번호마다 항상 같은 가상 정보
        cid = rnd2.choice(cids); s = rnd2.choice(SPECS)
        trailer = f"트{rnd2.randint(1000, 9999)}" if s[2] else None
        cur.execute("SAVEPOINT sp")
        try:
            # 이미 등록된 차량(실제 시연 차량 포함)은 덮어쓰지 않는다. 없는 차량만 새로 넣는다.
            cur.execute("""INSERT INTO trucks(vehicle_no,company_id,is_semi_trailer,trailer_no,truck_type,max_load_ton,status,vehicle_category,entry_approval)
                           VALUES(%s,%s,%s,%s,%s,%s,'OUTSIDE',%s,'APPROVED') ON CONFLICT (vehicle_no) DO NOTHING""",
                        (p, cid, s[2], trailer, s[0], round(s[5] / 1000, 2), s[1]))
            # 제원은 비어 있는 값(없음/0)만 채운다. 이미 값이 있으면 그대로 둔다.
            cur.execute("""INSERT INTO vehicle_specs(vehicle_no,registration_category,usage_purpose,empty_weight_kg,gross_weight_kg,max_payload_kg,tire_count,axle_count,chassis_height_m)
                           VALUES(%s,'화물','사업용',%s,%s,%s,%s,%s,%s)
                           ON CONFLICT (vehicle_no) DO UPDATE SET
                             empty_weight_kg=COALESCE(NULLIF(vehicle_specs.empty_weight_kg,0),EXCLUDED.empty_weight_kg),
                             gross_weight_kg=COALESCE(NULLIF(vehicle_specs.gross_weight_kg,0),EXCLUDED.gross_weight_kg),
                             max_payload_kg=COALESCE(NULLIF(vehicle_specs.max_payload_kg,0),EXCLUDED.max_payload_kg),
                             tire_count=COALESCE(NULLIF(vehicle_specs.tire_count,0),EXCLUDED.tire_count),
                             axle_count=COALESCE(NULLIF(vehicle_specs.axle_count,0),EXCLUDED.axle_count),
                             chassis_height_m=COALESCE(NULLIF(vehicle_specs.chassis_height_m,0),EXCLUDED.chassis_height_m)""",
                        (p, s[3], s[4], s[5], s[7], s[6], s[8]))
            cur.execute("RELEASE SAVEPOINT sp")
        except psycopg2.Error as e:
            cur.execute("ROLLBACK TO SAVEPOINT sp")
            print(f"건너뜀 {p}: {str(e).splitlines()[0]}")
            continue
        n_ok += 1
    con.commit(); con.close()
    print(f"등록 {n_ok}대 / 일부러 미등록 {len(skip)}대: {sorted(skip)}")

if __name__ == '__main__':
    main()