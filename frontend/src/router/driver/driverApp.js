// 26.09.16: 기존 /driver 대시보드(driver.vue, 실제 백엔드 연동)의 기능을
// MY/차량 화면(screens/MyPage.vue)으로 통합했다. /driver 는 router/member/member.js 에서
// /driver/app/my-page 로 리다이렉트되며, 아래 4개 화면이 실제 진입점이 된다.
import DriverAppLayout from "@/components/driver/DriverAppLayout.vue";
import TransportStatus from "@/components/driver/screens/TransportStatus.vue";
import DispatchList from "@/components/driver/screens/DispatchList.vue";
import Settlement from "@/components/driver/screens/Settlement.vue";
import MyPage from "@/components/driver/screens/MyPage.vue";
// import DriverInfo from "@/components/driver/screens/DriverInfo.vue"; // 26.10.28 FAQ·고객센터·약관 삭제로 사용 안 함
import ContainerInspection from "@/components/driver/screens/ContainerInspection.vue"; // 26.10.23 컨테이너 점검표

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
      { path: "inspection", name: "driver-app-inspection", component: ContainerInspection }, // 26.10.23 컨테이너 기사 7-point 점검
      // 26.10.28 자주 묻는 질문 · 고객센터 문의 · 약관 및 정책 삭제 (내용 없는 DriverInfo 화면이었음)
    ],
  },
];
