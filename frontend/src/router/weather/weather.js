import WeatherCalendar from "@/components/weather/WeatherCalendar.vue";

// 26.09.21 수정: component 에 문자열 "weather" 가 잘못 들어가 있던 버그 수정.
// 야드 운영/배차 판단에 참고할 수 있는 날씨 캘린더 - 로그인 여부와 무관하게 공용으로 제공한다.
export default [
  {
    path: "/weather",
    name: "weather",
    component: WeatherCalendar,
  },
];
