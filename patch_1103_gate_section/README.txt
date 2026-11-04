S카고 수정 패치 1103 - 항만 게이트 출차 → 구간 관제 연동 · 과적 검사 목록 분리
=====================================================================

기준 소스: SCargo_share_1102_1408 (11/02 14:08 공유본)
※ 1102 패치(차량 진입 허가)와 겹치는 파일은 없습니다. 둘 다 적용해도 됩니다 (순서 무관).

■ 적용 방법
 1. zip 을 프로젝트 폴더(backend, frontend, toll_demo 가 있는 폴더) 안에 풀면
    patch_1103_gate_section 폴더가 생깁니다.
 2. patch_1103_gate_section\apply_patch_1103.bat 실행
    - 프로젝트 폴더를 자동으로 찾습니다. 다른 곳에 풀었으면 프로젝트 폴더를 끌어다 놓으라고 묻습니다.
    - 덮어쓰기 전에 원래 파일을 bat\backup_patch_1103_<시각>\ 에 백업합니다.
    - DB 스크립트는 없습니다. 구간 관제 서버(run_toll.bat)가 켜질 때
      departure_logs.gate_log_id 칸이 없으면 스스로 추가합니다.
 3. 백엔드 재시작 (런처가 바뀐 소스를 자동으로 다시 컴파일)
 4. run_toll.bat 창을 닫고 다시 실행 (구간 관제 서버)
 5. 브라우저 Ctrl+F5

■ 되돌리기
 bat\backup_patch_1103_<시각>\restore_patch_1103.bat 실행
 (백업한 파일을 되돌립니다. DB 칸 departure_logs.gate_log_id 는 남겨 둠 - 있어도 문제 없음)

■ 주의
 - 파일 단위로 통째로 덮어씁니다. 11/02 14:08 이후 아래 파일을 직접 고쳤다면
   백업 폴더의 내 파일과 비교해서 다시 합쳐 주세요.

■ 바뀐 동작
 1) 시연 흐름: 항만 게이트 OCR 입차 → 검사소 계량(과적 통과) → 상하차 완료 → 게이트 출차 → 구간 관제
    - 구간 관제에는 항만 게이트에서 출차(EXIT)한 차량만 나옵니다.
      구간 관제 화면이 열려 있으면 2~5초 안에 그 차량이 인천항에서 출발합니다.
    - 최근 30분 안의 게이트 출차 중 아직 구간 관제에 안 넘어간 차량을 출발시킵니다.
      서버를 다시 켜도 놓치지 않고, 같은 출차가 두 번 나오지 않습니다 (departure_logs.gate_log_id 로 확인).
      30분 기준은 환경변수 TOLL_GATE_RECENT_MIN 으로 바꿀 수 있습니다.
    - 같은 번호 차량이 이미 구간 관제에서 달리고 있으면 그 차량이 도착한 뒤 출발합니다.
 2) 구간 관제 화면
    - 사진 폴더 자동 출차(예전 '자동 운행')는 꺼짐. 화면의 버튼도 없앴고, 대신
      '● 항만 게이트 출차 연동' 상태 표시만 나옵니다.
      (개발용으로 켜려면 run_toll.bat 실행 전 환경변수 TOLL_AUTO=1 → 화면에 주황 경고 표시)
    - 차량 이동 속도: 인천항 → 곤지암 66km 를 약 30초 (예전: 공용 시계를 따라 약 5초).
      바꾸려면 환경변수 TOLL_MAX_KMPS (실제 1초당 km, 기본 2.2 / 1.0 = 약 1분)
 3) 과적 검사 관리: 두 탭으로 분리
    - [항만 검사소 계량] 검사소 계량 화면에서 잰 기록 (등록 · 수정 · 삭제 그대로)
    - [구간 관제 계측 (검문소 · 톨게이트)] 구간 관제 차량이 지점을 지날 때 자동 저장된 기록
      (check_type = HIGHWAY). 계측 지점 · 위반 사유 표시, 조회만
    - API: GET /api/overload-checks?source=PORT|ROAD , /api/overload-checks/vehicle?...&source=PORT|ROAD
      (source 를 안 주면 예전처럼 전부)
 4) 항만 게이트 OCR 검사: 출차 가능 판정은 검사소 계량 기록만 봅니다 (구간 관제 계측 제외)
 5) 사이드바 [항만 → 허브] 메뉴 순서를 시연 흐름에 맞춤
    ... 항만 게이트 OCR 검사 → 검사소 계량 → 과적 검사 관리 → 구간 관제 → 허브 입고 관리 ...

■ 바뀐 파일 (11개)
 backend\scargo\src\main\java\com\scargo\controller\OverloadController.java
 backend\scargo\src\main\java\com\scargo\dto\OverloadCheckResponse.java
 backend\scargo\src\main\java\com\scargo\entity\OverloadCheck.java
 backend\scargo\src\main\java\com\scargo\repository\OverloadCheckRepository.java
 backend\scargo\src\main\java\com\scargo\service\OverloadService.java
 frontend\src\components\admin\adminMenu.js
 frontend\src\components\admin\screens\AdminOverloadChecks.vue
 frontend\src\components\gate\GateDemo.vue
 toll_demo\common.js
 toll_demo\toll_db.py
 toll_demo\toll_web.py
