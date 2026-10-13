# S카고 AMR 관제 앱 (관리자용 · 로그인 없음)

물류센터 AMR 예지보전 상태와 화물 흐름을 휴대폰에서 보는 안드로이드 앱입니다.
서버 없이 앱 안에서 시뮬레이션이 돌아가므로 와이파이가 없어도 동작합니다.

| 탭 | 내용 |
|---|---|
| 홈 | 현재 단계(STEP), 핵심 숫자, 위험 알림, 3D 미니뷰, 로봇 목록 |
| 로봇 | 건강점수, AI 남은 수명, 센서 6종, 진동 그래프 + 예측선, 정비 진행 |
| 3D | 전체 화면 3D (따라가기 · 전체 · 냉장 창고 · 자동 연출), 화질 · 배속 · 일시정지 |
| 화물 | 입고 → 보관 → 이동 → 출고 숫자, 허브 택배차, 보관 현황, 움직이는 화물, 최근 OCR |
| 알림 | 예지보전 · 화물 · 지게차 이벤트, 위험 알림은 화면 위 배너로도 표시 |

## APK 만들기

1. `app_build.bat` 더블클릭 (npm install → 빌드 → 안드로이드 프로젝트 생성 → Android Studio 열림)
2. Android Studio에서 Gradle Sync가 끝나면 **Build → Generate App Bundles or APKs → Generate APKs**
3. APK 위치: `android\app\build\outputs\apk\debug\app-debug.apk`
4. USB 케이블로 휴대폰 `Download` 폴더에 복사 → 휴대폰 파일 앱에서 눌러 설치

## 코드 구조

- `src/twin/amrSim.js` · `amrScene3d.js` — 웹 관제 화면(frontend/src/components/amr)과 같은 시뮬레이션 · 3D 모듈 복사본
- `src/main.js` — 5탭 화면, 3D 화면을 홈 미니뷰 ↔ 3D 탭으로 옮겨 쓰기, 화면에 보일 때만 3D 렌더링
- `src/style.css` — 다크 테마, 휴대폰 안전 영역(노치 · 하단바) 반영

브라우저로 먼저 보기: `npm run dev` 후 휴대폰 크롬에서 `http://노트북IP:5175`
