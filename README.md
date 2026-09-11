# 못먹어도 S카고 — AI 기반 화물차 항만 예지보전 시스템 (Backend)

## 한눈에 보기

인천항을 드나드는 화물차(트랙터-트레일러)의 번호판을 AI가 인식하고 → 업체·차량 정보와 대조해서 → 컨테이너 적재/하역 기록을 남기고 → 축중·VGM 대조로 과적 여부를 판정한 뒤 → 관리자 대시보드에 실시간으로 전달한다. 이 저장소는 그 중 **회원/업체 관리, 차량·적재·과적 데이터, API 서버** 부분을 담당하는 백엔드다.

```
[번호판 인식(AI)] → [업체/차량 조회] → [적재 기록] → [과적 판정] ─┬→ [DB 저장] → [관리자 대시보드]
                                                              └→ [재검증 요청]
```

기술적으로는 **Spring Boot + Gradle** 위에 **Spring Security(인증)** + **Spring Data JPA(영속성)** + **PostgreSQL**을 얹은 구조다. 아래는 그 안을 이루는 파일들의 상세 역할이다.

---

## 상세: 패키지 구조 (src/main/java/com/scargo)

### 핵심 설정

| 파일 | 역할 |
|---|---|
| `ScargoApplication.java` | Spring Boot 애플리케이션 진입점 |
| `SecurityConfig.java` | Spring Security 기본 설정 (인증/인가, 비밀번호 암호화 등) |

### controller — API 엔드포인트

| 파일 | 역할 |
|---|---|
| `AccountController.java` | 회원가입 · 로그인 등 계정 관련 API |
| `CompanyController.java` | 업체 등록 · 조회 등 업체 관련 API |

### dto — 요청/응답 객체

| 파일 | 역할 |
|---|---|
| `AccountCreateRequest.java` | 회원가입 요청 값 (아이디, 비밀번호 등) |
| `AccountResponse.java` | 계정 조회 응답 값 |
| `CompanyCreateRequest.java` | 업체 등록 요청 값 (사업자번호, 업체명 등) |
| `CompanyResponse.java` | 업체 조회 응답 값 |

### entity — DB 테이블 매핑

| 파일 | 역할 |
|---|---|
| `Account.java` | 사용자 계정 (아이디/비밀번호/권한) |
| `Company.java` | 물류·운송 업체 (사업자등록번호 기준) |
| `Truck.java` | 화물차(트랙터) 정보 |
| `Container.java` | 컨테이너 적재 정보 |
| `LoadingLocation.java` | 적재/하역 위치 |
| `LoadingRecord.java` | 적재/하역 기록 |
| `OverloadCheck.java` | 과적 판정 결과 |

### repository — JPA 데이터 접근

| 파일 | 역할 |
|---|---|
| `AccountRepository.java` | Account 테이블 CRUD |
| `CompanyRepository.java` | Company 테이블 CRUD |
| `TruckRepository.java` | Truck 테이블 CRUD |
| `ContainerRepository.java` | Container 테이블 CRUD |
| `LoadingLocationRepository.java` | LoadingLocation 테이블 CRUD |
| `LoadingRecordRepository.java` | LoadingRecord 테이블 CRUD |
| `OverloadCheckRepository.java` | OverloadCheck 테이블 CRUD |

### service — 비즈니스 로직

| 파일 | 역할 |
|---|---|
| `AccountService.java` | 회원가입 시 비밀번호 암호화, 중복 체크, 계정 조회 로직 |
| `CompanyService.java` | 업체 등록 시 사업자번호 중복 체크, 업체 등록/조회 로직 |

---

## 실행 방법

```bash
./gradlew bootRun
```

PostgreSQL 접속 정보는 `application.yml`(또는 `application.properties`)에서 설정한다.

---

못먹어도 S카고 · 도로교통 예지보전 프로젝트
