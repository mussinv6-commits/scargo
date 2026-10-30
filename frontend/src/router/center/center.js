// 26.10.23 지역센터 관제사(CENTER_OP) 화면: 지선 운행 센터 인수 확정
//  접근 제어는 router/index.js 의 beforeEach 가드에서 meta.requiresCenterOp 로 처리 (서버도 소속 센터를 다시 확인)
// import CenterReceipts from "@/views/CenterReceipts.vue"; // 26.10.29 /w/receipts 에서 CenterReceiptPanel 을 바로 씀

export default [
  // 26.10.29 센터 인수 확정은 사이드바가 있는 화면(/w/receipts)으로. 예전 주소는 그쪽으로 연결
  { path: "/center", redirect: "/w/receipts" },
  { path: "/center/receipts", name: "center-receipts", redirect: "/w/receipts" },
  { path: "/app-only", name: "app-only", component: () => import("@/scargo2/AppOnly.vue"), meta: { requiresAuth: true } }, // 26.10.29 지선 운송기사 웹 안내
];
