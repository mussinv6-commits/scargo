// 26.09.16: 기존 /driver 대시보드(driver.vue, 실제 백엔드 연동)의 기능을
// MY/차량 화면(screens/MyPage.vue)으로 통합했다. /driver 는 router/member/member.js 에서
// /driver/app/my-page 로 리다이렉트되며, 아래 4개 화면이 실제 진입점이 된다.
import DriverAppLayout from "@/components/driver/DriverAppLayout.vue";
import TransportStatus from "@/components/driver/screens/TransportStatus.vue";
import DispatchList from "@/components/driver/screens/DispatchList.vue";
import Settlement from "@/components/driver/screens/Settlement.vue";
import MyPage from "@/components/driver/screens/MyPage.vue";
import DriverInfo from "@/components/driver/screens/DriverInfo.vue";

export default [
  {
    path: "/driver/app",
    component: DriverAppLayout,
    meta: { requiresDriverAuth: true }, // router/index.js의 beforeEach 가드에서 체크
    children: [
      { path: "", redirect: { name: "driver-app-status" } },
      { path: "status", name: "driver-app-status", component: TransportStatus },
      { path: "dispatch-list", name: "driver-app-dispatch-list", component: DispatchList },
      { path: "settlement", name: "driver-app-settlement", component: Settlement },
      { path: "my-page", name: "driver-app-my-page", component: MyPage },
      { path: "faq", name: "driver-app-faq", component: DriverInfo, meta: { title: "자주 묻는 질문" } },
      { path: "support", name: "driver-app-support", component: DriverInfo, meta: { title: "고객센터 문의" } },
      { path: "terms", name: "driver-app-terms", component: DriverInfo, meta: { title: "약관 및 정책" } },
    ],
  },
];
