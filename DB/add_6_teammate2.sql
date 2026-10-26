-- add_6_teammate2.sql : 팀원 새 통합본(26.10.26) 코드를 우리 DB 에 맞추는 추가분 (여러 번 실행해도 안전)
-- 1) 간선(LINEHAUL_DRIVER) -> 지선(BRANCH_DRIVER) 이름 통일: 트리거 함수 · 제약 · 계정 데이터
BEGIN;
CREATE OR REPLACE FUNCTION public.scargo_check_account_role()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE v_type TEXT; v_cat TEXT;
BEGIN
 IF TG_TABLE_NAME = 'accounts' THEN
  IF NEW.center_id IS NOT NULL AND NOT EXISTS (
      SELECT 1 FROM logistics_centers WHERE center_id = NEW.center_id AND center_type = 'DISTRIBUTION_CENTER' FOR SHARE) THEN
   RAISE EXCEPTION '지역센터 관제사의 소속 센터는 지역 물류센터(DISTRIBUTION_CENTER)여야 합니다 (center_id=%)', NEW.center_id
    USING ERRCODE = '23514';
  END IF;
  IF TG_OP = 'UPDATE' AND NEW.user_type IS DISTINCT FROM OLD.user_type THEN
   IF NEW.user_type <> 'CONTAINER_DRIVER' AND EXISTS (SELECT 1 FROM transport_orders WHERE driver_account_id = NEW.account_id) THEN
    RAISE EXCEPTION '앱 오더에 배차된 기사는 CONTAINER_DRIVER 권한을 유지해야 합니다 (account_id=%)', NEW.account_id
     USING ERRCODE = '23514';
   END IF;
   SELECT vehicle_category INTO v_cat FROM trucks WHERE assigned_account_id = NEW.account_id
    AND vehicle_category IS DISTINCT FROM CASE NEW.user_type WHEN 'CONTAINER_DRIVER' THEN 'CONTAINER_TRUCK'
                                                            WHEN 'BRANCH_DRIVER' THEN 'GENERAL_TRUCK' END
    AND NOT (vehicle_category IS NULL AND NEW.user_type IN ('CONTAINER_DRIVER', 'BRANCH_DRIVER'))
   LIMIT 1;
   IF FOUND THEN
    RAISE EXCEPTION '배정 차량(%)과 맞지 않는 권한(%)으로 바꿀 수 없습니다. 차량 배정을 먼저 바꾸세요 (account_id=%)',
     coalesce(v_cat, '구분 없음'), NEW.user_type, NEW.account_id USING ERRCODE = '23514';
   END IF;
  END IF;

 ELSIF TG_TABLE_NAME = 'transport_orders' THEN
  IF NEW.driver_account_id IS NOT NULL THEN
   SELECT user_type INTO v_type FROM accounts WHERE account_id = NEW.driver_account_id FOR SHARE;
   IF v_type IS DISTINCT FROM 'CONTAINER_DRIVER' THEN
    RAISE EXCEPTION '앱 오더(항만 → 허브) 배차 기사는 컨테이너 운송기사(CONTAINER_DRIVER)여야 합니다 (account_id=%, 권한=%)',
     NEW.driver_account_id, v_type USING ERRCODE = '23514';
   END IF;
  END IF;

 ELSIF TG_TABLE_NAME = 'trucks' THEN
  IF NEW.assigned_account_id IS NOT NULL THEN
   SELECT user_type INTO v_type FROM accounts WHERE account_id = NEW.assigned_account_id FOR SHARE;
   IF v_type IS DISTINCT FROM (CASE NEW.vehicle_category WHEN 'CONTAINER_TRUCK' THEN 'CONTAINER_DRIVER'
                                                         WHEN 'GENERAL_TRUCK' THEN 'BRANCH_DRIVER' END)
      AND NOT (NEW.vehicle_category IS NULL AND v_type IN ('CONTAINER_DRIVER', 'BRANCH_DRIVER')) THEN
    RAISE EXCEPTION '차량 구분(%)에 맞는 기사 권한이 아닙니다 (vehicle_no=%, 권한=%). 컨테이너 트랙터 = CONTAINER_DRIVER, 일반 화물차 = BRANCH_DRIVER',
     coalesce(NEW.vehicle_category, '구분 없음'), NEW.vehicle_no, v_type USING ERRCODE = '23514';
   END IF;
  END IF;

 ELSIF TG_TABLE_NAME = 'logistics_centers' THEN
  IF NEW.center_type <> 'DISTRIBUTION_CENTER' AND EXISTS (SELECT 1 FROM accounts WHERE center_id = NEW.center_id) THEN
   RAISE EXCEPTION '지역센터 관제사가 소속된 센터는 유형을 바꿀 수 없습니다 (center_id=%)', NEW.center_id
    USING ERRCODE = '23514';
  END IF;
 END IF;
 RETURN NEW;
END $function$;
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_user_type;
UPDATE accounts SET user_type = 'BRANCH_DRIVER' WHERE user_type = 'LINEHAUL_DRIVER';
COMMIT;
