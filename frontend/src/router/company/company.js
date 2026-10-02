import CompanyLayout from "@/components/company/CompanyLayout.vue";
import CompanyDashboard from "@/components/company/CompanyDashboard.vue";
import CompanyInfo from "@/components/company/CompanyInfo.vue";
import CompanyMapping from "@/components/company/CompanyMapping.vue";
import CompanyTruckRegister from "@/components/company/CompanyTruckRegister.vue";
import CompanyDrivers from "@/components/company/CompanyDrivers.vue";
import CompanyContainerLocations from "@/components/company/CompanyContainerLocations.vue";

// 사업자(승인된 기업 회원) 전용 화면 묶음.
// 접근 제어(로그인 여부 + CORPORATE_APPROVED 여부)는 router/index.js 의
// beforeEach 가드에서 meta.requiresCorporate 를 보고 일괄 처리한다.
export default [
  {
    path: "/company",
    component: CompanyLayout,
    meta: { requiresCorporate: true },
    children: [
      { path: "", name: "company", component: CompanyDashboard },
      { path: "info", name: "company-info", component: CompanyInfo },
      { path: "mapping", name: "company-mapping", component: CompanyMapping },
      { path: "trucks/new", name: "company-truck-register", component: CompanyTruckRegister },
      { path: "drivers", name: "company-drivers", component: CompanyDrivers },
      { path: "container-locations", name: "company-container-locations", component: CompanyContainerLocations },
    ],
  },
];