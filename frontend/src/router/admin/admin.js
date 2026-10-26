import AdminLayout from "@/components/admin/AdminLayout.vue";
import AdminDashboard from "@/components/admin/screens/AdminDashboard.vue";
import AdminAccounts from "@/components/admin/screens/AdminAccounts.vue";
import AdminCompanies from "@/components/admin/screens/AdminCompanies.vue";
import AdminTrucks from "@/components/admin/screens/AdminTrucks.vue";
import AdminContainers from "@/components/admin/screens/AdminContainers.vue";
import AdminContainerLocations from "@/components/admin/screens/AdminContainerLocations.vue";
import GateDemo from "@/components/gate/GateDemo.vue"; // 26.10.01: 게이트 OCR 검사를 관리자 메뉴로 이동
import AdminMenuGuide from "@/components/admin/screens/AdminMenuGuide.vue"; // 26.10.26 메뉴 안내
import AdminGateSeal from "@/components/admin/screens/AdminGateSeal.vue"; // 26.10.26 S-Bot 에서 이동: 게이트 OCR · 씰 확인
import SbotInspections from "@/views/SbotInspections.vue"; // 26.10.26 S-Bot 에서 이동: 도착 · 점검
import SbotFrame from "@/views/SbotFrame.vue"; // 26.10.26 S-Bot 에서 이동: 택배 이동 · 물류센터 관제
import AmrTokenAdmin from "@/components/amr/AmrTokenAdmin.vue"; // 26.10.26 S-Bot 에서 이동: 앱 출입증
import AdminWeighbridge from "@/components/admin/screens/AdminWeighbridge.vue"; // 26.10.01 추가: 검사소 계량 콘솔
import AdminYards from "@/components/admin/screens/AdminYards.vue";
import AdminLoadingLocations from "@/components/admin/screens/AdminLoadingLocations.vue";
import AdminLoadingRecords from "@/components/admin/screens/AdminLoadingRecords.vue";
import AdminOverloadChecks from "@/components/admin/screens/AdminOverloadChecks.vue";
import AdminGates from "@/components/admin/screens/AdminGates.vue"; // 🛡️ 게이트(검문소) 관리 화면 추가
import AdminProfile from "@/components/admin/screens/AdminProfile.vue";
import AdminNoticeList from "@/components/admin/screens/AdminNoticeList.vue";
import AdminNoticeDetail from "@/components/admin/screens/AdminNoticeDetail.vue";
import AdminNoticeForm from "@/components/admin/screens/AdminNoticeForm.vue";
import AdminInspections from "@/components/admin/screens/AdminInspections.vue"; // 26.10.23 컨테이너 점검표
import AdminDailyReport from "@/components/admin/screens/AdminDailyReport.vue"; // 26.10.24 일일 처리량 보고서
import AdminOpsBoard from "@/components/admin/screens/AdminOpsBoard.vue"; // 26.10.24 운영 현황판
import AdminIncidents from "@/components/admin/screens/AdminIncidents.vue"; // 26.10.24 예외·사고 접수
import AdminOpsCenter from "@/components/admin/screens/AdminOpsCenter.vue"; // 26.10.24 운영 센터(탭 묶음)
import AdminKpi from "@/components/admin/screens/AdminKpi.vue"; // 26.10.24
import AdminGateReservations from "@/components/admin/screens/AdminGateReservations.vue"; // 26.10.24
import AdminCenterReceipts from "@/components/admin/screens/AdminCenterReceipts.vue"; // 26.10.23 지역센터 인수 확정
import AdminAmrTwin from "@/components/admin/screens/AdminAmrTwin.vue"; // 26.10.08 추가: 허브 AMR 3D 관제

// 관리자 전용 화면 묶음. 실제 접근 제어(로그인/ADMIN 권한 체크)는
// router/index.js 의 beforeEach 가드에서 meta.requiresAdmin 을 보고 처리한다.
export default [
  {
    path: "/admin",
    component: AdminLayout,
    meta: { requiresAdmin: true },
    children: [
      { path: "", name: "admin-dashboard", component: AdminDashboard },
      { path: "accounts", name: "admin-accounts", component: AdminAccounts },
      { path: "companies", name: "admin-companies", component: AdminCompanies },
      { path: "trucks", name: "admin-trucks", component: AdminTrucks },
      { path: "containers", name: "admin-containers", component: AdminContainers },
      { path: "container-locations", name: "admin-container-locations", component: AdminContainerLocations },
      { path: "inspections", name: "admin-inspections", component: AdminInspections },
      { path: "daily-report", name: "admin-daily-report", component: AdminDailyReport }, // 26.10.24
      { path: "ops-board", name: "admin-ops-board", component: AdminOpsBoard }, // 26.10.24
      { path: "incidents", name: "admin-incidents", component: AdminIncidents }, // 26.10.24
      { path: "ops-center", name: "admin-ops-center", component: AdminOpsCenter }, // 26.10.24
      { path: "kpi", name: "admin-kpi", component: AdminKpi }, // 26.10.24
      { path: "gate-reservations", name: "admin-gate-reservations", component: AdminGateReservations }, // 26.10.24
      { path: "center-receipts", name: "admin-center-receipts", component: AdminCenterReceipts },
      { path: "yards", name: "admin-yards", component: AdminYards },
      { path: "loading-locations", name: "admin-loading-locations", component: AdminLoadingLocations },
      { path: "loading-records", name: "admin-loading-records", component: AdminLoadingRecords },
      { path: "overload-checks", name: "admin-overload-checks", component: AdminOverloadChecks },
      { path: "gates", name: "admin-gates", component: AdminGates }, // 🛡️ 게이트 관리 라우터 경로 추가
      { path: "profile", name: "admin-profile", component: AdminProfile },
      { path: "notices", name: "admin-notices", component: AdminNoticeList },
      { path: "notices/write", name: "admin-notice-write", component: AdminNoticeForm },
      { path: "notices/update/:id", name: "admin-notice-update", component: AdminNoticeForm },
      { path: "notices/:id", name: "admin-notice-detail", component: AdminNoticeDetail },
      { path: "gate-ocr", name: "admin-gate-ocr", component: GateDemo }, // 26.10.01: 기존 /gate-demo
      { path: "weighbridge", name: "admin-weighbridge", component: AdminWeighbridge }, // 26.10.01 추가: 게이트 OCR → 검사소 계량
      { path: "menu-guide", name: "admin-menu-guide", component: AdminMenuGuide }, // 26.10.26 메뉴 안내
      { path: "gate-seal", name: "admin-gate-seal", component: AdminGateSeal }, // 26.10.26 S-Bot 허브 관제에서 이동
      { path: "arrivals", name: "admin-arrivals", component: SbotInspections }, // 26.10.26 도착 · 점검 (S-Bot 에서 이동)
      { path: "move", name: "admin-move", component: SbotFrame, props: { page: "move", title: "택배 이동" } }, // 26.10.26 (S-Bot 에서 이동)
      { path: "center", name: "admin-center", component: SbotFrame, props: { page: "center", title: "물류센터 관제" } }, // 26.10.26 (S-Bot 에서 이동)
      { path: "amr-tokens", name: "admin-amr-tokens", component: AmrTokenAdmin }, // 26.10.26 앱 출입증 (S-Bot 에서 이동)
      { path: "amr-twin", name: "admin-amr-twin", redirect: "/sbot" }, // 26.10.20: S-Bot 전용 화면(/sbot)으로 이동 // 26.10.08 추가: 허브 AMR 3D 디지털 트윈
    ],
  },
];