import CompanyLayout from "@/components/company/CompanyLayout.vue";
import CompanyInfo from "@/components/company/CompanyInfo.vue";
import CompanyMapping from "@/components/company/CompanyMapping.vue";
import CompanyTruckRegister from "@/components/company/CompanyTruckRegister.vue"; // 26.09.21 추가

// 사업자(승인된 기업 회원) 전용 화면 묶음.
// 접근 제어(로그인 여부 + CORPORATE_APPROVED 여부)는 router/index.js 의
// beforeEach 가드에서 meta.requiresCorporate 를 보고 일괄 처리한다.
export default [
  {
    path: "/company",
    component: CompanyLayout,
    meta: { requiresCorporate: true },
    children: [
      { path: "", name: "company", component: CompanyInfo },
      { path: "mapping", name: "company-mapping", component: CompanyMapping },
      { path: "trucks/new", name: "company-truck-register", component: CompanyTruckRegister }, // 26.09.21 추가
    ],
  },
];