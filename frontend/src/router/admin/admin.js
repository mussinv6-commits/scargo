import AdminLayout from "@/components/admin/AdminLayout.vue";
import AdminDashboard from "@/components/admin/screens/AdminDashboard.vue";
import AdminAccounts from "@/components/admin/screens/AdminAccounts.vue";
import AdminCompanies from "@/components/admin/screens/AdminCompanies.vue";
import AdminTrucks from "@/components/admin/screens/AdminTrucks.vue";
import AdminContainers from "@/components/admin/screens/AdminContainers.vue";
import AdminContainerLocations from "@/components/admin/screens/AdminContainerLocations.vue";
import GateDemo from "@/components/gate/GateDemo.vue"; // 26.10.01: 게이트 OCR 검사를 관리자 메뉴로 이동
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
    ],
  },
];