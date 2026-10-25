-- S-Bot 테스트용 택배차(일반화물차) 30대 등록: 허브→센터 운송에 겹치지 않게 번호가 돌아가도록 한다
-- 여러 번 실행해도 안전합니다(이미 있는 번호는 건너뜀). 삭제: DELETE FROM trucks WHERE truck_type = '택배차(테스트)' AND vehicle_no NOT IN (SELECT vehicle_no FROM shipment_legs);
DO $$
DECLARE
  pre text[] := ARRAY['서울','경기','인천','부산','대구','광주','대전','울산','충남','충북','전남','경남'];
  chr text[] := ARRAY['바','사','아','자'];
  v text;
  i int;
BEGIN
  FOR i IN 1..30 LOOP
    v := pre[1 + (i % 12)] || (80 + (i % 20)) || chr[1 + (i % 4)] || lpad((1000 + (i * 7919) % 9000)::text, 4, '0');
    BEGIN
      INSERT INTO trucks (vehicle_no, company_id, is_semi_trailer, truck_type, status, vehicle_category, entry_approval)
      SELECT v, (SELECT company_id FROM companies ORDER BY company_id LIMIT 1), FALSE, '택배차(테스트)', 'OUTSIDE', 'GENERAL_TRUCK', 'APPROVED'
      ON CONFLICT (vehicle_no) DO NOTHING;
    EXCEPTION WHEN OTHERS THEN
      RAISE NOTICE '건너뜀 % : %', v, SQLERRM;
    END;
  END LOOP;
END $$;

SELECT vehicle_no FROM trucks WHERE truck_type = '택배차(테스트)' ORDER BY vehicle_no;
