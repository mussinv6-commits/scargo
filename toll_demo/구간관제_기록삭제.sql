-- 구간 관제(인천항 → 곤지암) 시연 기록만 DB 에서 완전히 삭제 — 시연 연습 기록을 비울 때 사용.
--   지우는 것: 출차 기록 · 경로 이벤트 · 현재 위치 · 관제 조치 · 예상 통행료 · trip_key 가 있는 계측(overload_checks)·톨게이트 통과(toll_passages)
--   지우지 않는 것: 차량 · 회사 · 제원 · 요금표 · 계량 지점 · 팀이 쌓은 다른 기록(trip_key 가 없는 행)
--   삭제 전에 대상 건수를 먼저 보여 준다. 되돌릴 수 없으니 필요하면 백업 후 실행.
BEGIN;
SELECT 'overload_checks' AS 대상, count(*) AS 건수 FROM overload_checks WHERE trip_key IS NOT NULL
UNION ALL SELECT 'toll_passages', count(*) FROM toll_passages WHERE trip_key IS NOT NULL
UNION ALL SELECT 'departure_logs', count(*) FROM departure_logs
UNION ALL SELECT 'toll_route_events', count(*) FROM toll_route_events
UNION ALL SELECT 'toll_trip_positions', count(*) FROM toll_trip_positions
UNION ALL SELECT 'toll_control_actions', count(*) FROM toll_control_actions
UNION ALL SELECT 'toll_trip_quotes', count(*) FROM toll_trip_quotes;

DELETE FROM toll_control_actions;
DELETE FROM toll_route_events;
DELETE FROM toll_trip_positions;
DELETE FROM toll_trip_quotes;
DELETE FROM departure_logs;
DELETE FROM toll_passages WHERE trip_key IS NOT NULL;
DELETE FROM overload_checks WHERE trip_key IS NOT NULL;
COMMIT;
