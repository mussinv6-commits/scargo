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

export function clearLogin() {
  console.log("clearLogin 호출됨!!"); // 이게 찍히면 범인 찾음
  sessionStorage.removeItem("login");
  authState.user = null;
}