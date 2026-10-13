# 🚛 S-CARGO

> 항만에서 허브까지, 화물차와 물류센터를 잇는 **화물 물류 컨트롤타워 · AMR 관제 시스템**
> 팀 **못먹어도 S카고**

| 구분 | 역할 |
|---|---|
| 컨트롤타워 | 항만 게이트 → 톨게이트 → 검사소 → 물류센터 → 택배차까지 전 구간 판단 · 지시 |
| AMR 관제 | 물류센터 내부 AMR · 무인 지게차 실시간 감시, 예지보전 (3D 디지털 트윈) |

---

## 📌 업무 흐름

```
[1차] 게이트인(OCR) → 상하차 → 검사소 계량 · 과적 판정 → 게이트아웃(OCR)
[2차] 항만 게이트 → 톨게이트 IN → 검사소 1 · 2 → 톨게이트 OUT → 물류센터(AMR 관제) → 택배차 출발
```

| 단계 | 내용 |
|---|---|
| 게이트 | 번호판 OCR → 등록 차량 매칭 → 차단기 개방 |
| 검사소 | 축별 중량 계측 → 축하중 · 총중량 · 적재중량 기준 과적 판정 |
| 과적 | 재계량 통과 전까지 출차 불가 (화면 + OCR 서버 이중 차단) |
| 물류센터 | AMR이 입고 · 보관 · 출고, 무인 지게차가 위 단 보충 |
| 예지보전 | 진동 추세로 남은 수명 예측 → 계획 정비 · 예비기 교대 |

---

## ✨ 주요 기능

| 기능 | 내용 |
|---|---|
| 게이트 OCR | YOLO11 번호판 탐지 + CRNN(Attention) 문자 인식 |
| 검사소 계량 | 카메라 영상 + 축중 판독, 컨테이너 정보 자동 반영 |
| 과적 판정 | 축하중 10t / 총중량 40t / 최대적재량 110% 초과 |
| 이동 경로 지도 | OpenStreetMap(Leaflet) 경로 표시 |
| AMR 3D 관제 | Three.js 디지털 트윈 — AMR 5대 + 무인 지게차, 냉장 창고, 현장 음향, 일반/저사양 모드 |
| 모바일 앱 | 화물차 기사 앱, AMR 관리자 앱 (Capacitor · Android) |
| 관리자 화면 | 회원 · 업체 · 차량 · 컨테이너 · 야드 · 과적 검사 관리, 대시보드 |

---

## 🛠 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Frontend | Vue 3, Vite, Axios, Leaflet / OpenStreetMap, Three.js |
| Backend | Spring Boot 3.3, Spring Security(세션), JPA, QueryDSL, PostgreSQL |
| AI / OCR | Python, FastAPI, YOLO11, CRNN(Attention), OpenCV, PyTorch |
| Mobile | Capacitor 7 (Android) |
| DB | PostgreSQL (`scargo`) |

---

## 📁 폴더 구조

```
scargo/
├─ backend/        Spring Boot 백엔드 (8080)
├─ frontend/       Vue 웹 (5173) + 화물차 기사 앱 (driver-app, src/driverApp)
│  └─ src/components/amr/   AMR 3D 관제 (웹 /admin/amr-twin)
├─ amr-app/        AMR 관리자 앱 (Capacitor, 로그인 없음)
├─ ai-ocr/         게이트 OCR 서버 gate_api.py (8001) · 학습 · 진단 스크립트
└─ docs/           실행 명령어 · DB 테이블 · 병합 안내 메모
```

`ai-ocr/` 파일 구분

| 구분 | 파일 |
|---|---|
| 서버 · 실행 | `gate_api.py`, `gate_watch_service.py`, `gate_live_demo.py`, `plate_detector_gui.py`, `predict.py` |
| 인식 모델 | `plate_ocr_crnn.py`, `plate_ocr_crnn_attn.py`, `ocr_model_attn/` |
| 학습 · 실험 | `train*.py`, `run_experiment*.py`, `pretrain_synthetic.py`, `*_log*.csv` |
| 데이터 · 라벨 도구 | `auto_label.py`, `audit_*.py`, `merge_*.py`, `split_*.py`, `prepare_*.py`, `generate_synthetic_plates.py` |
| 진단 · 검증 | `diagnose_*.py`, `verify_*.py`, `check_*.py`, `batch_test_images.py` |

> 발표자료 · PPT · 영상은 저장소에 올리지 않습니다 (`.gitignore`).

---

## ▶ 실행 방법

**순서: 백엔드 → OCR 서버 → 프론트엔드**

### 1. 백엔드 (8080)
STS에서 `backend` 우클릭 → Run As → Spring Boot App
```bat
cd backend
gradlew.bat bootRun
```

### 2. 게이트 OCR 서버 (8001)
```bat
conda activate yolo
cd ai-ocr
set GATE_API_ID=admin
set GATE_API_PW=admin1234
uvicorn gate_api:app --host 0.0.0.0 --port 8001
```

### 3. 프론트엔드 (5173)
```bat
cd frontend
npm install
npm run dev
```
AMR 3D 관제: 관리자 로그인 → `/admin/amr-twin`

### 4. 모바일 앱
| 앱 | 명령 |
|---|---|
| 화물차 기사 앱 | `cd frontend` → `npm run app:sync` → `npm run app:open` (처음 한 번 `npx cap add android`) |
| AMR 관리자 앱 | `amr-app\app_build.bat` 실행 → Android Studio에서 APK 생성 |

### 처음 세팅
| 항목 | 내용 |
|---|---|
| JDK | 17 (Android Studio 빌드는 JDK 21) |
| PostgreSQL | DB `scargo` 생성 (접속 정보는 `application.yml`), 테이블은 첫 실행 시 자동 생성 |
| Python | `conda create -n yolo python=3.10` → `pip install -r ai-ocr/requirements.txt` |

---

## 🔧 트러블슈팅

| 증상 | 해결 |
|---|---|
| 수정한 코드가 반영 안 됨 (STS) | F5 → Project ▸ Clean → 재실행 |
| 8080 포트 사용 중 | `Stop-Process -Id (Get-NetTCPConnection -LocalPort 8080 -State Listen).OwningProcess -Force` |
| 재시작 후 세션 오류 | `server.servlet.session.persistent: false` |
| 게이트아웃 버튼 비활성 | 상하차 COMPLETED + 이번 방문 과적 통과 여부 확인 |
| 안드로이드 빌드 경로 오류 (한글 경로) | `gradle.properties`에 `android.overridePathCheck=true` |

---

## 🏷 버전

| 태그 | 내용 |
|---|---|
| `v1.0` | 1차 프로젝트 최종 (항만 게이트 OCR · 검사소 과적) |
| `main` | 2차 진행 중 (컨트롤타워 · AMR 관제 · 모바일 앱) |

## 👥 Team

**못먹어도 S카고** — 도로교통 예지보전 팀 프로젝트
