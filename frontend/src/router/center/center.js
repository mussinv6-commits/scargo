// 26.10.23 지역센터 관제사(CENTER_OP) 화면: 지선 운행 센터 인수 확정
//  접근 제어는 router/index.js 의 beforeEach 가드에서 meta.requiresCenterOp 로 처리 (서버도 소속 센터를 다시 확인)
import CenterReceipts from "@/views/CenterReceipts.vue";

export default [
  { path: "/center", redirect: "/center/receipts" },
  { path: "/center/receipts", name: "center-receipts", component: CenterReceipts, meta: { requiresCenterOp: true } },
];
