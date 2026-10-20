-- ① 운영 기준 (앱/웹에서 바꾼 값 + 누가 바꿨는지)
SELECT rul_threshold_hours AS 정비기준_시간, charge_at_pct AS 충전기준_pct, updated_by AS 변경자, updated_at AS 변경시각
FROM amr_settings;

-- ② 이벤트 로그 (기준 변경 · 충전 · 정비 기록) 최근 20건
SELECT created_at, event_level, phase_no, message
FROM hub_events ORDER BY created_at DESC LIMIT 20;

-- ③ 로봇 현재 상태
SELECT robot_code, robot_role, state, ROUND(battery_pct::numeric,1) AS 배터리, ROUND(health_score::numeric,1) AS 건강점수,
       ROUND(cum_load_kg::numeric) AS 누적kg, updated_at
FROM amr_robots ORDER BY robot_code;

-- ④ 센서 기록 최근 20건 (2초마다 쌓임)
SELECT r.robot_code, t.vibration_mm_s, t.motor_temp_c, t.battery_pct, t.state, t.recorded_at
FROM amr_telemetry t JOIN amr_robots r USING (robot_id)
ORDER BY t.recorded_at DESC LIMIT 20;

-- ⑤ 고장 예측
SELECT r.robot_code, p.risk_level, p.predicted_rul_hours AS 남은수명_h, p.is_resolved, p.predicted_at
FROM amr_predictions p JOIN amr_robots r USING (robot_id)
ORDER BY p.predicted_at DESC LIMIT 10;

-- ⑥ 정비 기록 (자동 승인 · 예비 AMR 투입 · 완료)
SELECT m.maint_id, r.robot_code, m.status, m.approved_by, s.robot_code AS 예비AMR,
       m.scheduled_at, m.approved_at, m.started_at, m.completed_at
FROM amr_maintenance m
JOIN amr_robots r ON r.robot_id = m.robot_id
LEFT JOIN amr_robots s ON s.robot_id = m.spare_robot_id
ORDER BY m.maint_id DESC LIMIT 10;

-- ⑦ 테이블별 쌓인 건수
SELECT 'telemetry' AS 테이블, COUNT(*) FROM amr_telemetry
UNION ALL SELECT 'predictions', COUNT(*) FROM amr_predictions
UNION ALL SELECT 'maintenance', COUNT(*) FROM amr_maintenance
UNION ALL SELECT 'events', COUNT(*) FROM hub_events;
