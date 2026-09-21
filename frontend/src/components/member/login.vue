<template>
  <div class="container">
    <div class="login-box">
      <div class="card">
        <div class="card-header">Login</div>

        <div class="card-body p-4">
          <div class="mb-3">
            <label class="form-label fw-bold">ID</label>
            <input class="form-control" v-model="id" placeholder="아이디 입력" />
          </div>

          <div class="mb-4">
            <label class="form-label fw-bold">Password</label>
            <input type="password" class="form-control" v-model="pw" placeholder="비밀번호" />
          </div>
          <input type="checkbox" v-model="sid" @click="saveId()" class="form-check-input" />&nbsp;id저장<br /><br />

          <button @click="login()" class="btn btn-primary btn-lg btn-login">
            Log In
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
        .post("http://localhost:8080/api/accounts/login", param)
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

<style>
.login-box {
  max-width: 450px;
  margin: 80px auto;
}

.card {
  border: none;
  border-radius: 15px;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
}

.card-header {
  background: #0d6efd;
  color: white;
  text-align: center;
  font-size: 28px;
  font-weight: bold;
  padding: 20px;
}

.btn-login {
  width: 100%;
}

.signup {
  text-align: center;
  margin-top: 15px;
}
</style>