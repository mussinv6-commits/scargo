# SCARGO 구현 보고 (팀 공유본, 2026-10-24)

## 1. 완료된 기능
| 단계 | 내용 |
|---|---|
| P0 | 컨테이너 점검표 DB — `container_inspections`(작성 중 → 확정 → 취소, 재점검 연결), `container_inspection_audits`(모든 처리 기록), 보호 트리거(확정본 수정 · 삭제 금지, 감사 행 수정 · 삭제 금지) |
| P1 | 허브 도착 확정(운송 출발 이후 OCR 입차 기록을 근거로, IN 도크 선택) · 인계 확정(컨테이너 번호 · 씰 번호 대조, 손상 여부) |
| P2 | 점검표 API — 작성 · 수정 · 확정 · 취소 · 재점검 · 이력, 역할 · 업체 · 허브 범위, 기사는 본인 배정 오더 · 본인 차량만 |
| P3 | 화면 — 기사 `/driver/app/inspection`(내 운송 오더 · 운송 출발), 운송사 `/company/inspections`, 관리자 `/admin/inspections`, 허브 `/sbot/inspections` |
| P4 | 실계정 시연 흐름(dev 프로필 없이) |
| 13장 | 기사 1명 = 차량 1대 규칙에 맞게 진행 중 오더의 배정 기사 정리 |
| 14장 | 팀 DB v16 호환 — 알림 유형 14종, 운송 구간 RECEIVED + `received_at`, AMR 출입증 감사 컬럼 · `amr_auth_failures`. 기능: 지역센터 인수 확정, 알림 유형 만들기 · 필터, AMR 출입증 발급자 · 기기 · 폐기 · 실패 기록과 감사 화면 |
| 15장 | 센터 인수 확정자 `shipment_legs.received_by_account_id`(로그인한 CENTER_OP 를 서버가 기록), AMR 출입증 감사 보존 `amr_auth_token_audits`(만료 출입증 정리 뒤에도 이력 유지, 토큰 미저장) |
| 기타 | 서버 시각 1970 표시 수정(공용 `serverTime.js`), 알림 유형 필터 API 400 수정 |

승인 정책 D1~D6 · R1~R5 · K1~K5(원 프로젝트 `SCARGO_IMPLEMENTATION_HANDOFF.md`)는 그대로입니다. 역할 이름 변경(BRANCH_DRIVER), GENERAL · CORPORATE_PENDING 제거, 팀 v16 신규 기능(예외 · 반납 · 지선 승인 · 1구간 통행료 등)은 승인 전이라 포함하지 않았습니다.

## 2. 승인된 역할 권한 (요약)
| 역할 | 점검표 | 허브 도착 · 인계 | 센터 인수 | AMR 출입증 감사 |
|---|---|---|---|---|
| CONTAINER_DRIVER | 본인 배정 오더 · 본인 차량 컨테이너 작성 · 확정, 운송 출발 | ❌ | ❌ | ❌ |
| CORPORATE_APPROVED | 자기 업체 기록 조회 | ❌ | ❌ | ❌ |
| HUB_OP | 소속 허브 재점검 · 확정 | ✅ 소속 허브 | ❌ | 소속 허브 출입증만 |
| CONTROL | 조회만 | 조회만(확정 403) | 운송 중(IN_TRANSIT)만 조회 | ❌ |
| CENTER_OP | ❌ | ❌ | 소속 센터 조회 + 인수 확정(ARRIVED → RECEIVED) | ❌ |
| ADMIN | 전체 조회, 확정본 예외 취소(사유 필수) | 허용(별도 승인) | 조회만(확정 403) | 전체 + 연결 실패 기록 |
| LINEHAUL_DRIVER · GENERAL | ❌ | ❌ | ❌ | ❌ |

## 3. 이 패키지에서 확인한 것 (2026-10-24 실제 실행)
| 항목 | 결과 |
|---|---|
| 설치 SQL → 빈 임시 DB (`psql -1 -v ON_ERROR_STOP=1`) | exit 0, 오류 0. 스키마 해시가 12~15장 배포를 마친 작업 DB 와 같음. 무효 제약 0, 꺼진 트리거 0, 시퀀스가 모든 기존 ID 보다 큼 |
| 다시 실행(빈 DB 아님) | `-1` 실행은 exit 3 으로 멈추고, 멈춤 옵션 없이 계속 실행해도 모든 문장이 실패 — 두 경우 모두 데이터 · 스키마 변경 0. **재실행 불가(멱등 아님)** — 새 DB 에서만 사용 |
| 백엔드 단위 테스트(패키지 사본) | `./gradlew test` 106/106 |
| 백엔드 기동(패키지 사본, 환경변수 설정, dev 없이) | 스키마 오류 없음, 설치 DB 에 연결 확인 |
| 인증 · 권한 · 업무 API (패키지 백엔드 + 설치 DB) | 시연 계정 8개 로그인, 시드 시나리오 조회 · P1~P4 전체 흐름(점검 → 출발 → 씰 확인 → 도착 → 재점검 → 인계 → 예외 취소) · 권한 차단 31/31, 14 · 15장 확인 스크립트 32/32(센터 인수 + 확정자, 알림 v16 유형, AMR 코드 → 앱 로그인 → 로그아웃 → 감사 · 실패 기록) |
| 프론트(패키지 사본) | `npm ci` 성공, 웹 빌드 · 기사 앱 빌드 성공, 기사 점검 화면에서 시드 데이터(인계 완료 오더, 취소 · 확정 점검) 표시 확인 |
| 원 프로젝트에서 이미 확인(10-23 ~ 10-24) | 통합 테스트 33/33(`scargo_it`), 배포 · 롤백 리허설 46/46, 실제 OCR 포함 P0~P4 시연 45/45, 15장 화면 실계정 확인 |

## 4. 알려진 문제 · 한계
- (26.10.24 병합본에서 해결) Gradle Wrapper 9.7.1 ↔ Spring Boot 플러그인 3.3.0 비호환으로 `bootJar` · `build` 가 실패하던 문제 → Wrapper 8.10.2 로 맞춰 `bootJar` · `build` 성공
- OCR: 이 패키지 상태로 OCR 서버를 실행 · 추론해 보지 않음. 모델 2개(`best.pt` 약 41MB, `best_recognizer_attn.pth` 약 35MB)는 팀 `.gitignore` 가 공유 대상으로 지정한 파일이라 포함. Python 패키지 버전은 원 개발 PC 환경 기준
- 설치 SQL 의 허브 입차 기록 1건은 OCR 없이 만든 합성 기록(`ocr_engine = 'SYNTHETIC-DEMO'`)
- 하역 완료 기능 없음 → 도착에 쓴 도크는 계속 "사용 중"
- AMR 감사 보관 기간 · 개별 폐기(ADMIN_REVOKE) · 전체 잠금(GLOBAL_LOCKED) 미결정 · 미구현
- S Cargo / S-Bot 관리자 구분은 화면에서만(백엔드는 둘 다 ADMIN)
- 날씨 위젯은 OpenWeather 키(`VITE_WEATHER_API_KEY`) 필요 — 원 소스의 키는 공유본에서 뺐음

## 5. 안드로이드 APK · 하드웨어
- APK 빌드 · 설치 · 실기기 확인: **하지 못함**(Android SDK 가 있는 PC 필요). 기사 앱 웹 자산은 `npm run app:sync` 로 만들고 Android Studio 에서 빌드
- 실제 게이트 카메라 · 허브 AMR 하드웨어 연동: 확인하지 못함. 시연은 사진 폴더 기반 OCR · AMR 시뮬레이터 기준

## 6. 공유본에서 원 프로젝트와 다르게 바꾼 것 (보안 · 설치용, 기능 변경 없음)
- `backend/scargo/src/main/resources/application.yml`: DB 접속을 환경변수(`SCARGO_DB_URL` · `SCARGO_DB_USERNAME` · `SCARGO_DB_PASSWORD`)로, 기본 대상 `scargo_demo`, 비밀번호 기본값 없음
- `python/gate_api.py`: `PGDATABASE` 기본값 `scargo_demo`, `PGPASSWORD` 기본값 없음
- `frontend/src/components/home/WeatherWidget.vue`, `frontend/src/components/weather/WeatherCalendar.vue`: OpenWeather 키를 환경변수 `VITE_WEATHER_API_KEY` 로
- 추가: `env.example` 3개, `python/requirements-gate-api.txt`, 이 문서와 README
- 넣지 않은 것: `node_modules`, 빌드 결과물(`build`, `dist`, `dist-driver`, 안드로이드 빌드 · 웹 자산 · `local.properties`), Gradle 캐시, 원 프로젝트 검증 스크립트 · 수동 SQL · 백업 · DB 덤프 · 로그, 학습 데이터셋 · 실험 결과 CSV, 1차 게이트 사진 폴더, 마이그레이션 SQL(기존 DB 업그레이드용은 원 프로젝트에 그대로 있음)
