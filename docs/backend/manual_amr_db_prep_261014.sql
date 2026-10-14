-- 26.10.14 AMR 연동 준비
-- 백엔드(ddl-auto: update)가 SQL보다 먼저 켜져서 hubs · amr_* 테이블을 "컬럼이 빠진 채로" 만들었으면 지우고 다시 만든다.
-- (그 테이블들은 허브 데이터가 없어서 비어 있다 → 지워도 잃는 데이터 없음)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'hubs')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'hubs' AND column_name = 'address') THEN
    RAISE NOTICE '백엔드가 만든 불완전한 hubs 테이블 삭제';
    DROP TABLE IF EXISTS hub_events, amr_maintenance, amr_predictions, amr_tasks, amr_telemetry, amr_robots, hubs CASCADE;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'amr_robots')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'amr_robots' AND column_name = 'home_x') THEN
    RAISE NOTICE '백엔드가 만든 불완전한 amr_* 테이블 삭제';
    DROP TABLE IF EXISTS hub_events, amr_maintenance, amr_predictions, amr_tasks, amr_telemetry, amr_robots CASCADE;
  END IF;
END $$;
