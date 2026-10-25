# SCARGO 병합본 검증 · 남은 문제 (2026-10-24)

모든 DB 쓰기 시험은 임시 DB `scargo_rbtest_merge`(이 패키지의 설치 SQL 로 새로 만든 DB)에서만 했고, 끝난 뒤 삭제했습니다. 백엔드가 실제로 연결한 DB 는 매번 프로세스 연결로 확인했습니다. `scargo` · `scargo_it` 에는 쓰지 않았습니다.

## 1. PostgreSQL 접속 오류 (`The server requested SCRAM-based authentication, but no password was provided.`)
**원인 (확인함)**
1. 팀 공유본 `application.yml` 은 `password: ${SCARGO_DB_PASSWORD:}` — 환경변수가 없으면 빈 값
2. Eclipse/STS 실행 설정 `scargo_0930 - ScargoApplication`(A 의 `.metadata`)에 환경변수가 하나도 없음. 이 프로젝트는 `Desktop\Scargo_최종팀플(2차)_합본\backend\scargo` 를 가리키고, 그 폴더에 팀 공유본 소스가 복사돼 있음
3. → JDBC 드라이버가 비밀번호 없이 접속 → 서버(SCRAM 인증)가 거부. 프로필 · URL 문제가 아님 (A 사본은 이후 비밀번호를 `application.yml` 기본값에 직접 적고 기본 DB 를 `scargo` 로 바꾼 상태)

**해결 (병합본)**: 비밀번호는 여전히 코드에 없음. ① 실행 폴더의 `application-local.yml`(선택, 공유 금지) 또는 ② 환경변수 `SCARGO_DB_*` 로 지정. 지정하지 않거나 틀리면 원인 안내가 나오고 기동이 멈춤. PostgreSQL 인증 방식(SCRAM)은 그대로

| 시험 (임시 DB) | 결과 |
|---|---|
| 비밀번호 없음 | 기동 중단 · "PostgreSQL 이 비밀번호 인증(SCRAM)을 요구하지만 … 비밀번호가 설정되지 않았습니다 [url=…, username=postgres, password=설정 안 됨]" + 설정 방법 |
| 틀린 비밀번호 | 기동 중단 · "비밀번호 인증에 실패했습니다 (28P01)" + 안내 |
| 없는 DB | 기동 중단 · "접속하려는 데이터베이스가 없습니다 (3D000)" + 설치 안내 |
| `application-local.yml` 방식 | 정상 기동, 연결 DB = 임시 DB 확인 |
| 환경변수 방식 | 정상 기동, 연결 DB = 임시 DB 확인, 4개 계정 로그인 200 |
| 비밀번호 출력 여부 | 안내문에 비밀번호 값 없음(설정 여부만) |

실제 팀원 PC 의 비밀번호로 접속하는 것은 이 PC 에서 확인할 수 없음 — 각자 PC 에서 1회 확인 필요.

## 2. 빌드 · 기동
| 항목 | 결과 |
|---|---|
| 백엔드 컴파일 · 단위 테스트(`./gradlew --offline test`) | **106/106** |
| 백엔드 기동(`bootRun`, dev 없이, AMR 시뮬레이터 끔) | 성공, 스키마 오류 없음 |
| `bootJar` · `build` (Gradle Wrapper 8.10.2) | **성공** — `clean build --stacktrace` 테스트 106/106, `scargo_0930-0.0.1-SNAPSHOT.jar`(61,934,606 B). 9.7.1 에서는 Spring Boot 플러그인 3.3.0 과 비호환으로 실패했음. jar 실행은 기동 시작 · DB 비밀번호 미설정 안내 출력까지만 확인(DB 연결 · API 는 `bootRun` 으로 확인한 결과) |
| 프론트 `npm ci` · 웹 빌드 · 기사 앱 빌드 | 성공 |
| Python 문법 컴파일(`gate_api.py`, `plate_ocr_crnn*.py`, `gate_watch_service.py`) | 성공 |

## 3. 기능 (실제 API · 화면, 임시 DB)
| 항목 | 결과 |
|---|---|
| 14 · 15장 확인 스크립트(센터 인수 RECEIVED · 확정자 · 권한, 알림 v16 유형 만들기 · 필터, AMR 코드 → 앱 로그인 → 로그아웃 → 감사 · 실패 기록 · 권한) | **32/32** |
| 역할별 API(로그인 7계정, 기사 오더 · 점검 목록 · 이력, 허브 도착 후보, CONTROL IN_TRANSIT 200 · ARRIVED 403, CENTER_OP 점검 403, 운송사 자기 업체 점검, ADMIN 감사, 비로그인 401) | **17/17** |
| 로그인 화면(팀 반영) | 아이디 저장 → 로그아웃 후 아이디 채워짐, 비밀번호 저장 안 됨(localStorage · 쿠키 확인), 비밀번호 보기 토글 동작 |
| 공지 날짜(팀 반영) | 숫자 → `2026-10-24` 표시 |
| P0~P4 전체 흐름(점검 → 출발 → 씰 → 도착 → 재점검 → 인계 → 예외 취소) | 이번 병합으로 해당 코드가 바뀌지 않아 재실행하지 않음. 같은 코드로 팀 공유본 검증 때 31/31 (10-24 오전) |

## 4. 12~15장 보존
점검표 · 도착/인계 · 센터 인수 · AMR 감사 관련 백엔드 · 프론트 · SQL 파일은 C 와 **바이트 동일**(병합 변경은 설정 · 로그인 화면 · 게시판 날짜뿐). 7항목 · PASS/FAIL/PENDING · DRAFT/FINALIZED/CANCELLED · 재점검 연결 · 확정본 보호 · 감사 · 허브 범위 · RECEIVED 확정자 · 출입증 감사 보존 · 권한(ADMIN · CONTROL · HUB_OP · CENTER_OP · CORPORATE_APPROVED · 기사)은 C 와 같음. `transport_orders.precheck` 는 기존처럼 건드리지 않음(K1 미결정)

## 5. DB 스키마
- 병합본 설치 SQL = C 와 동일(표 63 · 함수 33 · 트리거 53 · FK 156 · CHECK 139). 백엔드 엔티티 · SQL 과 맞음(기동 · API 시험으로 확인)
- 팀 v16 SQL 과의 차이(별도 결정 필요): 팀 쪽에 승인 전 표 11개(`branch_units`, `branch_seal_checks`, `center_inbound_logs`, `empty_container_inspections`, `exceptions`, `ops_settings`, `return_attachments`, `return_cys`, `return_plan_changes`, `seal_unregistered_inputs`, `toll_fee_rules`), 역할 `BRANCH_DRIVER`; 우리 쪽에만 `container_inspections`, `container_inspection_audits`, `amr_auth_token_audits`(P0 · 15장)

## 6. Python OCR
- 소스는 C 와 동일. 백엔드 → OCR `/hub/scan`(direction · kind · vehicleNo · exclude) 요청과 응답 필드(photoPlate · recognizedPlate · engine · ocrConfidence · imageUrl)가 서로 맞음(코드 대조). OCR → 백엔드 `/api/accounts/login`, `/api/v1/gate-logs` 존재
- **실제 모델 추론 · OCR 서버 실행은 이번에 하지 않음** (모델 파일 2개 포함, 10-23 원 프로젝트에서 실제 OCR 시연 성공 기록만 있음)

## 7. 남은 문제 · 확인하지 못한 것
- 실행 jar 를 실제 DB 에 연결해 API 까지 돌린 시험은 하지 않음(`bootRun` 으로만 확인)
- 안드로이드 APK 빌드 · 실기기, 실제 게이트 카메라 · AMR 하드웨어 연동: 확인 못 함
- OCR 실제 추론: 이번 미실행
- 팀 v16 신규 기능 · 디자인 변경: 승인 · 결정 대기(비교 보고서 5 · 6장)
- 작업 DB `scargo` 현재 내용: 이번 작업 전에 이미 팀 공유 패키지의 시연 데이터(driver02 · driver03, 합성 입차 기록 등)가 들어 있었음 — 이번 작업에서 바꾸지 않음
