// src/auth/authState.js
import { reactive } from "vue";

export const authState = reactive({
  user: JSON.parse(sessionStorage.getItem("login") || "null"),
});

export function setLogin(userData) {
  console.log("setLogin 호출됨, 저장할 데이터:", userData);
  sessionStorage.setItem("login", JSON.stringify(userData));
  console.log("저장 직후 sessionStorage 값:", sessionStorage.getItem("login"));
  authState.user = userData;
}

// 26.10.22 통합: 기사 계정 = 회원가입 일반회원(GENERAL) + 구간별 권한의 컨테이너 운송기사 · 지선 운송기사
export const DRIVER_TYPES = ["GENERAL", "CONTAINER_DRIVER", "BRANCH_DRIVER"];
export function isDriverType(userType) {
  return DRIVER_TYPES.includes(userType);
}

export function clearLogin() {
  console.log("clearLogin 호출됨!!"); // 이게 찍히면 범인 찾음
  sessionStorage.removeItem("login");
  authState.user = null;
}