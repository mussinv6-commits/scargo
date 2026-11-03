S카고 수정 패치 1102 - 차량 진입 허가 확인 (허브 게이트 입차 · 배차)
=====================================================================
기준 소스: SCargo_share_1102_1408 (11/02 14:08 공유본)

■ 적용 방법
 1. zip 을 프로젝트 폴더(backend, frontend, DB 가 있는 폴더) 안에 풀면
    patch_1102_vehicle_approval 폴더가 생깁니다.
 2. patch_1102_vehicle_approval\apply_patch_1102.bat 실행
    - 프로젝트 폴더를 자동으로 찾습니다. 다른 곳에 풀었으면 프로젝트 폴더를 끌어다 놓으라고 묻습니다.
    - 덮어쓰기 전에 원래 파일을 bat\backup_patch_1102_<시각>\ 에 백업합니다.
    - 마지막에 로컬 DB 에 DB\add_14_게이트_진입허가.sql 을 적용할지 묻습니다 (Y 권장, 로컬 DB 만).
 3. 백엔드 재시작 (런처가 바뀐 소스를 자동으로 다시 컴파일) → 브라우저 Ctrl+F5
 4. amr-app 을 따로 띄워 쓰는 경우 amr-app 도 다시 실행

■ 되돌리기
 bat\backup_patch_1102_<시각>\restore_patch_1102.bat 실행
 (백업한 파일을 되돌리고 이 패치가 새로 추가한 파일을 지웁니다. DB 칸 block_reason 은 남겨 둠 - 있어도 문제 없음)

■ 주의
 - 파일 단위로 통째로 덮어씁니다. 11/02 14:08 이후 아래 파일을 직접 고쳤다면
   백업 폴더의 내 파일과 비교해서 다시 합쳐 주세요.
 - 운영 DB 에는 적용하지 마세요 (bat 는 localhost DB 만 대상).

■ 바뀐 동작 (정책)
 1) 차량 진입 허가와 기사 배정은 따로
    - '허가 + 기사 미배정' = 정상(배차 대기). 기사 배정 · 변경이 허가 상태를 바꾸지 않음
    - 기사 가입 승인 · 반려가 더 이상 차량 허가를 같이 바꾸지 않음 (예전: 승인 → 차량 허가, 반려 → 차량 불허)
      차량은 [차량 관리 → 진입 허가 심사]에서 따로 허가해야 함
 2) 허브 게이트 입차
    - S카고 차량(trucks)에 등록되고 진입 허가(APPROVED)인 차량만 통과
      (예전: hub_vehicles 에 있으면 심사대기여도 통과 / S카고 허가 차량도 hub_vehicles 에 없으면 '미등록' 차단)
    - 화물차 · 택배차 레인은 trucks.vehicle_category 기준 (CONTAINER_TRUCK → 화물차, GENERAL_TRUCK → 택배차)
      hub_vehicles 의 차종은 판정에 안 씀
    - 차단 사유 표시: 판독 불가 / 미등록 / 심사대기 / 불허 (hub_vehicle_logs.block_reason)
    - 출차는 예전 그대로 (번호 일치면 통과, 허가 상태와 무관)
 3) 배차 (서버에서 확인 - API 를 직접 불러도 우회 안 됨)
    - 지선 배차 생성 · 기사 변경 (W-08): 허가 차량 + 지선 기사 필수 (기사 없이 만드는 '미배정' 배차 없음)
    - 운송오더 등록 (W-07): 차량 · 기사 둘 다 비우면 배차 전 초안(CREATED)만,
      하나라도 넣으면 허가 차량 + 승인된 컨테이너 기사 둘 다 필수
    - 컨테이너-차량 매핑 생성 · 변경: 허가 차량 + 그 차량 전담 기사 필요
    - S-Bot 시뮬레이터 배차: 허가 차량 + 쉬는 지선 기사가 있을 때만, 기사도 같이 배정
      (일반화물차를 가진 운송사마다 시연용 지선 기사 simlh<회사ID>_1~6 / demo1234 자동 생성)
    - 반납 지시, 기존 운행의 조회 · 출발 · 도착 · 인수는 그대로
 4) 화면: W-08 차량 목록은 허가 차량만, 기사 선택 전 [배차 지시] 비활성.
    W-07 배차 기사는 차량이 허가된 기사만. 게이트 OCR 화면 · AMR 앱 · 3D 관제에 차단 사유 표시

■ 시연 전에 확인할 것
 - 심사대기 차량은 게이트 입차 · 신규 배차가 막힙니다. 시연에 쓸 차량만 [진입 허가 심사]에서 허가하세요.
 - hub_vehicles 와 trucks 의 차종이 달라 레인이 바뀌는 차량 (허가하면 도크가 바뀜)
     울산81사5366, 인천98바2578 : 화물차(입고) → 택배차(출고)
     경남99바4892, 부산90배8407 : 택배차(출고) → 화물차(입고)
 - 기사 없이 '배차 지시' 상태로 남은 예전 지선 운행은 차량을 허가한 뒤 기사를 배정해야 진행됩니다.

■ 포함 파일 (23개)
 backend  : SbotController, AmrDtos, HubVehicleLog, AmrService, HubVehicleService,
            VehicleUsePolicy(신규), CarrierController, V9WebController, MappingService
 test     : HubGateEntryExitTests, DriverApprovalVehicleIndependenceTests, OrderDispatchValidationTests,
            VehicleUsePolicyTests (이상 신규), BackendMergeTests (생성자 인자 1개 보완)
 frontend : AdminGateSeal.vue, amr/amrScene3d.js, amr/amrSim.js, W07.vue, W08.vue
 amr-app  : src/main.js, src/twin/amrScene3d.js, src/twin/amrSim.js
 DB       : add_14_게이트_진입허가.sql (신규)
 백엔드 테스트 43개 통과 (gradlew test)

■ 포함하지 않은 것
 - 로컬 DB 데이터 정리 (시뮬레이터 미배정 지선 운행 취소, 반납 기한 채우기 등)는 각자 DB 상태가 달라 넣지 않았습니다.
