import GateDemo from "@/components/gate/GateDemo.vue";

// 번호판 인식(CargoScan) 게이트 통과 데모.
// 로그인 여부와 무관하게 공용으로 제공한다 (weather.js와 동일한 패턴).
export default [
  {
    path: "/gate-demo",
    name: "gate-demo",
    component: GateDemo,
  },
];
