-- add_14 (26.11.02): 앱 홈 "이번 달 예지보전 성과" 시연 데이터 - 여러 번 실행해도 중복 없음
-- 이번 달 안에서 완료된 사전 정비 3건(PLANNED, 예측 연결) + 돌발 고장 1건(CORRECTIVE)
-- 날짜는 '이번 달 경과 시간'을 비율로 나눠 넣으므로 어느 날 실행해도 항상 이번 달 · 과거 시각
DO $$
DECLARE
  m0  timestamptz := date_trunc('month', now());
  span interval   := now() - date_trunc('month', now());
  r   record;
  pid bigint;
BEGIN
  IF EXISTS (SELECT 1 FROM amr_maintenance WHERE memo LIKE '%[시연-성과]%') THEN
    RAISE NOTICE '예지보전 성과 시연 데이터가 이미 있어 생략';
    RETURN;
  END IF;

  FOR r IN
    SELECT * FROM (VALUES
      (1, '구동모터 베어링', 'WARN', 40.0, 0.2),
      (2, '배터리 셀',       'WARN', 52.0, 0.5),
      (4, '주행 휠',         'CRIT', 20.0, 0.8)
    ) AS t(rid, comp, lvl, rul, frac)
  LOOP
    INSERT INTO amr_predictions (robot_id, component, risk_level, predicted_rul_hours, vibration_slope, cum_load_kg, reason, model_version, is_resolved, predicted_at)
    VALUES (r.rid, r.comp, r.lvl, r.rul, 0.0300, 9000.0, '시연 데이터 - 상승 추세 감지 후 사전 정비', 'trend-v1', TRUE, m0 + span * r.frac - interval '3 hours')
    RETURNING prediction_id INTO pid;

    INSERT INTO amr_maintenance (robot_id, prediction_id, spare_robot_id, maint_type, part_name, status, memo, scheduled_at, started_at, completed_at, approved_at, approved_by)
    VALUES (r.rid, pid, 5, 'PLANNED', r.comp, 'DONE', '예비기 AMR-05 투입 후 계획 정비 [시연-성과]',
            m0 + span * r.frac - interval '2 hours', m0 + span * r.frac - interval '90 minutes',
            m0 + span * r.frac, m0 + span * r.frac - interval '2 hours', '관제사');
  END LOOP;

  INSERT INTO amr_maintenance (robot_id, prediction_id, spare_robot_id, maint_type, part_name, status, memo, scheduled_at, started_at, completed_at)
  VALUES (2, NULL, NULL, 'CORRECTIVE', '제어 보드', 'DONE', '돌발 정지 후 사후 정비 [시연-성과]',
          m0 + span * 0.35, m0 + span * 0.35 + interval '10 minutes', m0 + span * 0.35 + interval '3 hours');
END $$;

SELECT maint_type, status, count(*) FROM amr_maintenance
 WHERE completed_at >= date_trunc('month', now()) GROUP BY 1, 2 ORDER BY 1;
