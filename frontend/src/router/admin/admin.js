import AdminLayout from "@/components/admin/AdminLayout.vue";
import AdminDashboard from "@/components/admin/screens/AdminDashboard.vue";
import AdminAccounts from "@/components/admin/screens/AdminAccounts.vue";
import AdminCompanies from "@/components/admin/screens/AdminCompanies.vue";
import AdminTrucks from "@/components/admin/screens/AdminTrucks.vue";
import AdminContainers from "@/components/admin/screens/AdminContainers.vue";
import AdminYards from "@/components/admin/screens/AdminYards.vue";
import AdminLoadingLocations from "@/components/admin/screens/AdminLoadingLocations.vue";
import AdminLoadingRecords from "@/components/admin/screens/AdminLoadingRecords.vue";
import AdminOverloadChecks from "@/components/admin/screens/AdminOverloadChecks.vue";
import AdminProfile from "@/components/admin/screens/AdminProfile.vue";

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
      { path: "yards", name: "admin-yards", component: AdminYards },
      { path: "loading-locations", name: "admin-loading-locations", component: AdminLoadingLocations },
      { path: "loading-records", name: "admin-loading-records", component: AdminLoadingRecords },
      { path: "overload-checks", name: "admin-overload-checks", component: AdminOverloadChecks },
      { path: "profile", name: "admin-profile", component: AdminProfile }, // 26.09.21 추가: 관리자 전용 "내 정보"
    ],
  },
];