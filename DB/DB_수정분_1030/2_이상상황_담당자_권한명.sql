-- 26.10.29 QA: 시연 데이터의 예외·사고 담당 문구를 실제 권한명으로 교체
UPDATE ops_incidents SET assignee = '통합 관제사'      WHERE assignee = '관제 담당';
UPDATE ops_incidents SET assignee = '곤지암허브 관제사' WHERE assignee = '허브 담당';
