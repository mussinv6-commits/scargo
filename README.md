# 🚛 SafeCargo (SCargo)

> 화물차 게이트 OCR · 검사소 과적 검사 · 출차 관리 통합 시스템
> 팀 **못먹어도 S카고** — 도로교통 예지보전 팀 프로젝트

게이트에서 화물차 번호판을 AI로 인식해 입차시키고, 검사소에서 축중을 계량해 과적을 판정한 뒤, **과적 검사를 통과한 차량만 출차**시키는 항만·야드용 화물차 관리 시스템입니다.

---

## 📌 업무 흐름

```
게이트인(ENTRY OCR) → 상하차 작업(PENDING → COMPLETED) → 검사소 계량(1회) → 과적 판정 → 게이트아웃(EXIT OCR)
```

| 단계 | 설명 |
|---|---|
| 게이트인 | 번호판 OCR → 등록차량(trucks) 매칭 → 차단기 개방, 검사소 대기열 등록 |
| 상하차 | PENDING 작업이 IN_PROGRESS → COMPLETED 로 자동 진행 |
| 검사소 | 축별 중량 계측 → 축하중·총중량·적재중량 기준으로 판정 → `overload_checks` 저장 |
| 과적 | 과적 차량은 재계량(감량 후) 통과 전까지 출차 불가 |
| 게이트아웃 | 입차 때 사진과 같은 사진을 다시 OCR해 차량 일치 확인 → 출차 |

---

## ✨ 주요 기능

| 기능 | 내용 |
|---|---|
| 게이트 OCR | YOLO11 번호판 탐지 + CRNN 문자 인식, 등록/미등록 자동 판정 |
| 구내 차량 목록 | 게이트인은 항상 가능, 차량별로 상하차·과적 상태와 게이트아웃 버튼 표시 |
| 검사소 계량 | 카메라 영상 + 축중 판독, 컨테이너 정보 DB 자동 반영, 자동 모드 |
| 과적 판정 | 축하중 10t 초과 / 총중량 40t 초과 / 적재중량이 최대적재량의 110% 초과 |
| 이동 경로 지도 | OpenStreetMap(Leaflet)으로 게이트 → 검사소 → 목적지 경로 표시 (미니 플레이어) |
| 출차 이중 차단 | 화면(버튼 비활성) + OCR 서버(409 거부) 모두에서 과적 차량 출차 차단 |
| 알림 | 미등록 차량·과적 발생 시 관리자/소속 기업에 알림 |
| 관리자 화면 | 회원·업체·차량·컨테이너·야드·적재위치·과적 검사 관리, 대시보드 |

---

## 🛠 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Frontend | Vue 3 (`<script setup>`), Vite, Axios, Leaflet / OpenStreetMap |
| Backend | Spring Boot 3.3, Spring Security(세션), JPA, PostgreSQL |
| AI / OCR | Python, FastAPI, YOLO11, CRNN(Attention), OpenCV, PyTorch |
| DB | PostgreSQL (`scargo`) |

---

## 📁 폴더 구조

```
SCargo/
├─ backend/scargo/   # Spring Boot 백엔드 (포트 8080)
├─ frontend/         # Vue 프론트엔드 (포트 5173)
└─ python/           # 게이트 OCR 서버 gate_api.py (포트 8001)
   └─ 게이트_수신함/   # 게이트 카메라 사진이 들어오는 폴더
```

---

## ▶ 실행 방법

**순서: 백엔드 → OCR 서버 → 프론트엔드**

### 1. 백엔드 (8080)
STS에서 `backend/scargo` 우클릭 → Run As → Spring Boot App
또는
```bat
cd backend\scargo
gradlew.bat bootRun
```

### 2. 게이트 OCR 서버 (8001)
```bat
conda activate yolo
cd python
set GATE_API_ID=admin
set GATE_API_PW=admin1234
uvicorn gate_api:app --host 0.0.0.0 --port 8001
```
> 백엔드를 재시작하면 OCR 서버도 같이 재시작하세요.

### 3. 프론트엔드 (5173)
```bat
cd frontend
npm install   (처음 한 번)
npm run dev
```

### 처음 세팅
| 항목 | 내용 |
|---|---|
| JDK | 17 |
| PostgreSQL | DB `scargo` 생성 (접속 정보는 `application.yml`) — 테이블은 첫 실행 시 자동 생성 |
| Python | `conda create -n yolo python=3.10` → `pip install -r python/requirements.txt` |

---

## 🔧 트러블슈팅

| 증상 | 해결 |
|---|---|
| 수정한 코드가 반영 안 됨 (STS) | F5 → Project ▸ Clean → 재실행 |
| 8080 포트 사용 중 | `Stop-Process -Id (Get-NetTCPConnection -LocalPort 8080 -State Listen).OwningProcess -Force` (PowerShell) |
| 재시작 후 세션 오류 | `server.servlet.session.persistent: false` |
| 게이트아웃 버튼 비활성 | 상하차 COMPLETED + 이번 방문 과적 통과 여부 확인 |

자세한 문제 해결 이력은 `SCargo_문제해결_정리.pptx` 참고.

---

## 👥 Team

**못먹어도 S카고** — 도로교통 예지보전 팀 프로젝트
