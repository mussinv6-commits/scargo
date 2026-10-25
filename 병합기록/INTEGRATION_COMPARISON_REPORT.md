# SCARGO 3개 프로젝트 비교 · 병합 결정 (2026-10-24)

## 1. 비교 대상
| 이름 | 파일 | 실제 내용 |
|---|---|---|
| A | `Scargo_2차(백업용).zip` (315,657,667 B) | `Desktop\Scargo_최종팀플(2차)_합본` 폴더 + Eclipse 작업공간(`.metadata`). backend/frontend/python/DB 는 C(팀 공유 통합본)를 복사한 것이고, **`backend/scargo/src/main/resources/application.yml` 만 다름**(기본 DB `scargo`, 비밀번호 기본값을 직접 적음). frontend 에 `android/local.properties` · 웹 자산, python 에 `.spec` · `.pptx` 추가(생성물 · 문서) |
| B | `scargo (예지님).zip` (78,905,688 B) | 팀원 프론트 프로젝트(Vue, v7~v13 병합 내역) + `백엔드/` 안의 백엔드 zip 9개(v13, fix_1223~1600, full_fix_1345) + 팀 DB SQL `scargo_db_최종통합_20261023_1553.sql` |
| C | `SCARGO_Final_Team_Integrated_20261024.zip` | 승인된 P0~P4 · 12~15장이 들어간 검증된 통합본 (**병합 기준**) |

B 백엔드는 가장 최신 전체본 `scargo_backend_full_fix_1345.zip`(10-23 13:02)과 마지막 부분 수정 `fix_1600` 을 기준으로 비교했습니다.

## 2. 비교 결과 요약
| 구분 | A | B | C |
|---|---|---|---|
| 백엔드 Java 파일 | 208 (C 와 동일) | 271 | 208 |
| P0~P2 점검표(`container_inspections` · 감사 · 보호 트리거, 7항목 · PASS/FAIL/PENDING · DRAFT/FINALIZED/CANCELLED · 재점검 연결) | ✅ (C 와 같음) | ❌ 별도 구현(`ops/AppInspectionController`, 다른 표 구조) | ✅ |
| P1 허브 도착 · 인계(OCR 근거 · 씰 대조 · 상태 전이 · 중복 방지 · 허브 범위) | ✅ | ❌ 다른 구현(`app/`, `ops/`) | ✅ |
| 14장 RECEIVED · 알림 유형 · AMR 감사 컬럼/실패 기록 | ✅ | 부분(알림 유형 · `amr_auth_failures` 엔티티) | ✅ |
| 15장 `received_by_account_id` · 출입증 감사 보존 | ✅ | ❌ | ✅ |
| 팀 v16 신규 기능(운송사 승인 · 지선(BRANCH) · 예외 · 반납 · 공컨 점검 · 씰 분실/보류 · 통행료 규칙 · 앱 토큰 API) | ❌ | ✅ (승인 전 기능) | ❌ |
| DB | C 와 같음 | 팀 v16 SQL: 표 71개(우리 63 + 승인 전 11, **`container_inspections` · `container_inspection_audits` · `amr_auth_token_audits` 없음**), 역할 `BRANCH_DRIVER` | 승인 통합 스키마 63표 |
| 프론트 | C 와 같음(+생성물) | 공통 199 · B 전용 38(`scargo2/` 16화면, `tower/`, `plan/`, `views/group`) · C 전용 20(점검 · 도착 · 센터 인수 · AMR 감사 화면) · 공통 중 45개 내용 다름 | 기준 |
| Python OCR | C 와 같음 | 없음 | 기준 |

## 3. 각 프로젝트에 없는 기능
- **A**: 없음(C 와 같음). 다만 `application.yml` 에 비밀번호를 직접 넣고 기본 DB 를 작업 DB `scargo` 로 되돌려 둠 — 보안 · DB 보호 규칙 위반이라 채택 안 함
- **B**: P0 점검표 · 감사 표, P1 도착/인계(우리 방식), 12~15장 전체(RECEIVED 확정자, 출입증 감사 보존), CENTER_OP 인수 확정 화면, S-Bot 점검/AMR 감사 화면, 서버 시각(epoch 초) 공용 처리
- **C**: 팀 v16 신규 기능 전체(승인 전), 로그인 "아이디 저장"(localStorage) · 비밀번호 보기, 공지 날짜 epoch 수정, Gradle 8.10.2(아래 6장)

## 4. 반영한 팀 변경 (B → 병합본)
| 변경 | 원본 | 병합본 파일 | 근거 |
|---|---|---|---|
| 로그인 "아이디 저장"을 localStorage 로(아이디만, 비밀번호 저장 안 함, 예전 쿠키 값 1회 이전) + 아이디칸 안 체크박스 + 비밀번호 보기/숨기기 | B `views/GroupMain.vue`, `utils/savedId.js` (v7) | `frontend/src/views/GroupMain.vue`, `frontend/src/utils/savedId.js`(그대로) | 프론트 전용, API · 권한 변화 없음. 실제 로그인 화면(`/login` = GroupMain)에서 동작 확인 |
| 공지 · 게시판 날짜가 epoch 초 숫자로 보이던 문제 | B `utils/postsApi.js` (fix_1630) | `frontend/src/utils/postsApi.js` — B 의 `ymd` 대신 C 의 공용 `serverTime.toDate` 사용 | C 에서 실제 재현: 공지 등록 후 `createdAt: 1792814482.68…` → 목록에 숫자 표시. 수정 후 `2026-10-24` |

B 의 같은 기능 중 다음 부분은 **빼고** 반영: `homeOf`(scargo2 권한별 첫 화면 이동 — B 전용 화면으로 이동), S-Bot 로그인 조건 변경(C 의 CONTROL/HUB_OP → 허브 관제 이동 유지), `/api/accounts/{id}/display-name`(B 백엔드에만 있음).

## 5. 반영하지 않은 변경 (충돌 · 구버전 · 승인 전)
| 항목 | 이유 |
|---|---|
| B 백엔드 전체(`ops/`, `app/` 105개 파일, 공통 74개 파일의 다른 구현 — SecurityConfig · AccountService · Account 엔티티 등) | 다른 계열 구현. 승인 통합 스키마에 없는 표 11개 필요, `BRANCH_DRIVER` 역할 · 운송사 승인 정책 변경 포함(D/R/K 정책 · "역할 이름 변경 · 승인 정책 변경 금지" 위반), C 의 P0~P4 · 12~15장을 덮어씀 |
| B 팀 SQL(`00_scargo_db_최종통합.sql`, `scargo_db_최종통합_20261023_1553.sql`) | 위와 같음. 점검표 · 감사 · 15장 표 없음 |
| B 프론트 `scargo2/` · `tower/` · `plan/` · `views/group` · `router/{scargo2,tower,group}.js` | 호출하는 `/api/web/*` 38개가 C 백엔드에 없음(B 백엔드 전용) |
| `AdminCompanies.vue` 업체 승인 · 반려 | `/api/companies/{id}/approve|reject` · `approval_status` 필요 — 승인 전 정책 |
| `regi.vue` GENERAL 가입 제거 · `/api/companies/signup` | GENERAL 제거는 승인 전, API 없음 |
| AMR 화면 문구 "택배차 → 지선차량" | LINEHAUL→BRANCH 용어 변경(승인 전). C 쪽이 더 새로움(지게차 운반 기록 등) |
| 테마 색 · 화면 비율 축소(`viewportScale`, `sg-vars.css`, `App.css`, `theme.css`) | 전 화면 디자인 · 레이아웃 변경 — 필수 아님, 검증된 화면에 영향 → 팀 디자인 결정 필요(수동 검토) |
| B `application.yml`(dev 기본 프로필, DB 비밀번호 기본값, H2 · pgbox 프로필) | 기본 프로필 dev 는 시드 비밀번호를 바꾸는 동작과 충돌, 비밀번호 기본값 금지 |
| A `application.yml`(비밀번호 기본값 · 기본 DB `scargo`) | 비밀번호 하드코딩 · 작업 DB 를 기본 대상으로 — 금지 |
| 공통 프론트 파일의 나머지 차이(`router/index.js`, `App.vue`, `NotificationBell.vue`, `amrApi.js`, `SbotLayout.vue`, 날짜 처리 파일 등) | C 쪽에 12~15장 화면 · 메뉴 · 날짜 처리가 들어간 새 버전이고, B 쪽 고유 변경은 위의 B 전용 화면 연결 · 용어 변경뿐 |
| 날씨 위젯 API 키(B 원본에 하드코딩) | C 의 환경변수 방식 유지 |

## 6. 수동 검토 필요
- **Gradle Wrapper 8.10.2 (B 의 방법)**: Spring Boot 3.3 플러그인 + Gradle 9.7.1 조합에서 `bootJar` · `build` 실패(C 의 알려진 문제). B 는 Wrapper 를 8.10.2 로 낮춰 해결. **26.10.24 사용자 승인으로 반영** — Wrapper 를 8.10.2 로 다시 만들고(`gradlew wrapper --gradle-version 8.10.2`) `bootJar` · `build`(테스트 106/106) 성공. 이전 Wrapper(9.7.1) 파일은 패키지 밖 `_wrapper_backup_gradle_9.7.1/` 에 보관
- 팀 v16 신규 기능 묶음(운송사 승인 · 지선 · 예외 · 반납 · 통행료 · `/api/web` · `/api/app` 화면)은 요구사항 · 스키마(버전별 마이그레이션) · 권한 승인 후 별도 단계로
- 디자인 변경(브랜드 색 · 화면 비율 축소)은 팀 결정 후

## 7. 수정 · 추가 파일 (C 대비)
| 파일 | 내용 |
|---|---|
| `backend/scargo/src/main/java/com/scargo/config/DatabaseConnectionFailureAnalyzer.java` (새) | DB 접속 실패 시 원인 · 대상(url · username · 비밀번호 설정 여부만) · 설정 방법을 출력 |
| `backend/scargo/src/main/resources/META-INF/spring.factories` (새) | 위 분석기 등록 |
| `backend/scargo/src/main/resources/application.yml` | `spring.config.import: optional:file:./application-local.yml` 추가 (비밀번호 기본값 없음 유지) |
| `backend/scargo/application-local.yml.example` (새), `backend/scargo/.gitignore` (새) | 로컬 접속 설정 예시, `application-local.yml` 공유 차단 |
| `backend/scargo/gradle/wrapper/gradle-wrapper.properties` · `gradle-wrapper.jar`, `backend/scargo/gradlew` · `gradlew.bat` | Gradle Wrapper 9.7.1 → 8.10.2 (`bootJar` · `build` 실패 해결, B 의 방법) |
| `frontend/src/views/GroupMain.vue` | 팀 "아이디 저장" · 비밀번호 보기 반영 |
| `frontend/src/utils/savedId.js` (새, B 그대로) | 아이디만 localStorage 저장 |
| `frontend/src/utils/postsApi.js` | 게시판 날짜 epoch 초 처리 |
| `README.md`, 이 문서, `VERIFICATION_REPORT.md` | 설정 · Eclipse/STS 안내, 비교 · 검증 보고 |

Python · DB 설치 SQL 은 C 와 같습니다(변경 없음).
