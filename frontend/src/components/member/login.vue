<template>
  <div class="login-page">
    <div class="login-box">
      <div class="card">
        <div class="card-header">로그인</div>

        <div class="card-body p-4">
          <div class="form-row mb-3">
            <label class="form-label fw-bold">아이디</label>
            <input class="form-control" v-model="id" placeholder="아이디 입력" />
            <label class="save-id-label">
              <input type="checkbox" v-model="sid" @click="saveId()" class="form-check-input" />
              <span>아이디 저장</span>
            </label>
          </div>

          <div class="form-row mb-4">
            <label class="form-label fw-bold">비밀번호</label>
            <div class="password-wrapper">
              <input
                :type="pwVisible ? 'text' : 'password'"
                class="form-control"
                v-model="pw"
                placeholder="비밀번호 입력"
              />
              <button
                type="button"
                class="pw-toggle-btn"
                @click="pwVisible = !pwVisible"
                :aria-label="pwVisible ? '비밀번호 숨기기' : '비밀번호 보기'"
              >
                <!-- 보이는 상태: 눈 아이콘 -->
                <svg v-if="pwVisible" viewBox="0 0 24 24" width="18" height="18">
                  <path
                    fill="currentColor"
                    d="M12 5c-7 0-10 7-10 7s3 7 10 7 10-7 10-7-3-7-10-7zm0 12a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-2a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"
                  />
                </svg>
                <!-- 숨긴 상태: 눈에 슬래시 아이콘 -->
                <svg v-else viewBox="0 0 24 24" width="18" height="18">
                  <path
                    fill="currentColor"
                    d="M3.28 2.22 2.22 3.28l4.02 4.02C4.16 8.6 2.6 10.53 2 12c0 0 3 7 10 7 1.8 0 3.36-.46 4.68-1.14l3.04 3.04 1.06-1.06L3.28 2.22zM12 17c-4.42 0-6.86-3.44-7.62-5C5.03 10.7 6.22 9.24 7.7 8.4l1.6 1.6a3 3 0 0 0 4.7 3.7l1.24 1.24C14.36 15.7 13.24 17 12 17zm.02-9.98c.98.05 1.92.24 2.78.55l-1.6 1.6a3 3 0 0 0-1.18-.15l-2-2c.66-.02 1.32 0 2 0zM22 12s-3-7-10-7c-.63 0-1.23.06-1.8.15l1.72 1.72c.03 0 .06-.01.08-.01a3 3 0 0 1 3 3c0 .03 0 .06-.01.08l3.15 3.15C19.9 11.94 20.9 10.24 22 12z"
                  />
                </svg>
              </button>
            </div>
          </div>

          <button @click="login()" class="btn btn-primary btn-lg btn-login">
            로그인하기
          </button>

          <div class="signup">
            아직 회원이 아니신가요?
            <a :href="'/regi'" class="text-decoration-none fw-bold"> 회원가입 </a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { useCookies } from "vue3-cookies";
const { cookies } = useCookies();

import axios from "axios";
import { setLogin } from "@/auth/authState.js";   // 추가

export default {
  data() {
    return {
      id: "",
      pw: "",
      sid: false,
      pwVisible: false,
    };
  },
  mounted() {
    let userId = cookies.get("userId");
    if (userId !== null) {
      this.sid = true;
      this.id = userId;
    } else {
      this.sid = false;
      this.id = "";
    }
  },
  methods: {
    saveId() {
      if (this.sid === false && this.id.trim() !== "") {
        cookies.set("userId", this.id);
      } else {
        cookies.remove("userId");
      }
    },
    login() {
      let param = {
        userId: this.id,
        userPw: this.pw,
      };

      axios
        // 26.09.21 수정: withCredentials가 없으면 서버가 내려주는 JSESSIONID 쿠키가
        // 브라우저에 저장되지 않아, 이후 관리자 전용 API가 전부 403(익명 처리)이 됨.
        .post("http://localhost:8080/api/accounts/login", param, { withCredentials: true })
        .then((resp) => {
          let mem = resp.data;
          console.log("로그인 응답 데이터:", mem);
          console.log("JSON 변환 결과:", JSON.stringify(mem));
          if (mem.userId === undefined) {
            alert("id나 password를 확인해 주십시오");
            return;
          }

          setLogin(resp.data);   // sessionStorage.setItem(...) 대신 이걸로 교체

          let location = sessionStorage.getItem("location");
          sessionStorage.removeItem("location"); // 26.09.15 추가
          if (location === null || location === "") {
            location = "/";
          }

          this.$router.push(location);
        })
        .catch((err) => {
          console.log(err);
          if (err.response) {
            const msg = err.response.data?.message || "로그인에 실패했습니다.";
            alert(msg);
          } else {
            alert("서버에 연결할 수 없습니다. 백엔드 서버가 실행 중인지 확인해주세요.");
          }
        });
    },
  },
};
</script>

<style src="@/components/CSS/login.css" scoped></style>