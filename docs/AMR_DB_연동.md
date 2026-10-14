# AMR DB 연동 · 앱/웹 정비 승인 연동 (26.10.14)

## 한 줄 요약
정비 · 충전은 시스템이 자동으로 하고, 관리자는 앱 · 웹에서 "운영 기준"(정비 기준 시간 · 충전 기준 %)만 정한다. 바꾼 기준은 DB를 거쳐 2초 안에 3D 로봇 움직임에 반영된다.

## 흐름
| 단계 | 내용 | DB |
|---|---|---|
| 1 | 2초마다 로봇 센서 저장 | amr_robots(현재) · amr_telemetry(기록) |
| 2 | 누적 운반량이 가장 많은 AMR 진동 상승 → AI 고장 예측 | amr_predictions · amr_maintenance(SCHEDULED) |
| 3 | 남은 수명 ≤ 정비 기준 → 자동 정비 예약 (approved_by = 자동) | amr_maintenance.approved_at / approved_by |
| 3' | 배터리 ≤ 충전 기준 → 작업 후 충전소 이동 | hub_events · amr_robots.state = CHARGING |
| 관리자 | 운영 기준 변경 (앱 · 웹) | amr_settings (허브별 1줄) |
| 4 | 예비기 AMR-05 투입 → 정비 시작 · 완료 | amr_maintenance(IN_PROGRESS → DONE) |
| - | 모든 단계 이벤트 | hub_events |

## 실행 순서
1. DB: `docs/backend/manual_merge_amr_hub_261014.sql` 실행  
   (백엔드를 먼저 켜서 테이블이 불완전하게 생겼으면 `manual_amr_db_prep_261014.sql` 먼저)
2. 백엔드 실행 → `http://localhost:8080/api/amr/state` 에 JSON 나오면 정상
3. 웹: 관리자 → 허브 AMR 관제 (오른쪽 위 "DB 연결 · 앱 연동")
4. 앱: 휴대폰 USB 연결 → `adb reverse tcp:8080 tcp:8080` → 앱 서버 칸 `127.0.0.1:8080`

## API (/api/amr, 공개)
`GET /state` · `GET|PUT /settings` · `POST /telemetry` · `POST /predictions` · `POST /maintenance/{id}/approve|start|done` · `GET /maintenance` · `POST /events` · `GET /robots/{code}/telemetry`
