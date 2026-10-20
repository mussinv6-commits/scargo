<template>
  <!-- 26.10.20 추가: S그룹 메인(계열사 선택). S Cargo / S-Bot 으로 나뉘어 들어간다. -->
  <div class="grp">
    <div class="topbar">
      <template v-if="authState.user">
        <span class="who">{{ authState.user.userName }}님<em v-if="scopeLabel"> · {{ scopeLabel }}</em></span>
        <a href="#" @click.prevent="logout">로그아웃</a>
      </template>
    </div>
    <svg class="emb" viewBox="-150 -150 300 300" aria-label="S GROUP">
      <defs>
        <linearGradient id="gg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f6e4b0"/><stop offset=".45" stop-color="#d8a94e"/><stop offset="1" stop-color="#8f6420"/></linearGradient>
        <linearGradient id="gg2" x1="1" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff3cf"/><stop offset=".5" stop-color="#e5b95e"/><stop offset="1" stop-color="#a77a2a"/></linearGradient>
      </defs>
      <g class="ring"><animateTransform attributeName="transform" type="rotate" from="0 0 0" to="360 0 0" dur="70s" repeatCount="indefinite"/>
        <circle r="112" fill="none" stroke="url(#gg)" stroke-width="1.4"/>
        <circle r="104" fill="none" stroke="url(#gg)" stroke-width=".6" opacity=".7"/>
        <g stroke="url(#gg)" stroke-width="1"><line x1="104.0" y1="0.0" x2="98.0" y2="0.0"/><line x1="103.1" y1="13.6" x2="100.1" y2="13.2"/><line x1="100.5" y1="26.9" x2="97.6" y2="26.1"/><line x1="96.1" y1="39.8" x2="90.5" y2="37.5"/><line x1="90.1" y1="52.0" x2="87.5" y2="50.5"/><line x1="82.5" y1="63.3" x2="80.1" y2="61.5"/><line x1="73.5" y1="73.5" x2="69.3" y2="69.3"/><line x1="63.3" y1="82.5" x2="61.5" y2="80.1"/><line x1="52.0" y1="90.1" x2="50.5" y2="87.5"/><line x1="39.8" y1="96.1" x2="37.5" y2="90.5"/><line x1="26.9" y1="100.5" x2="26.1" y2="97.6"/><line x1="13.6" y1="103.1" x2="13.2" y2="100.1"/><line x1="0.0" y1="104.0" x2="0.0" y2="98.0"/><line x1="-13.6" y1="103.1" x2="-13.2" y2="100.1"/><line x1="-26.9" y1="100.5" x2="-26.1" y2="97.6"/><line x1="-39.8" y1="96.1" x2="-37.5" y2="90.5"/><line x1="-52.0" y1="90.1" x2="-50.5" y2="87.5"/><line x1="-63.3" y1="82.5" x2="-61.5" y2="80.1"/><line x1="-73.5" y1="73.5" x2="-69.3" y2="69.3"/><line x1="-82.5" y1="63.3" x2="-80.1" y2="61.5"/><line x1="-90.1" y1="52.0" x2="-87.5" y2="50.5"/><line x1="-96.1" y1="39.8" x2="-90.5" y2="37.5"/><line x1="-100.5" y1="26.9" x2="-97.6" y2="26.1"/><line x1="-103.1" y1="13.6" x2="-100.1" y2="13.2"/><line x1="-104.0" y1="0.0" x2="-98.0" y2="0.0"/><line x1="-103.1" y1="-13.6" x2="-100.1" y2="-13.2"/><line x1="-100.5" y1="-26.9" x2="-97.6" y2="-26.1"/><line x1="-96.1" y1="-39.8" x2="-90.5" y2="-37.5"/><line x1="-90.1" y1="-52.0" x2="-87.5" y2="-50.5"/><line x1="-82.5" y1="-63.3" x2="-80.1" y2="-61.5"/><line x1="-73.5" y1="-73.5" x2="-69.3" y2="-69.3"/><line x1="-63.3" y1="-82.5" x2="-61.5" y2="-80.1"/><line x1="-52.0" y1="-90.1" x2="-50.5" y2="-87.5"/><line x1="-39.8" y1="-96.1" x2="-37.5" y2="-90.5"/><line x1="-26.9" y1="-100.5" x2="-26.1" y2="-97.6"/><line x1="-13.6" y1="-103.1" x2="-13.2" y2="-100.1"/><line x1="-0.0" y1="-104.0" x2="-0.0" y2="-98.0"/><line x1="13.6" y1="-103.1" x2="13.2" y2="-100.1"/><line x1="26.9" y1="-100.5" x2="26.1" y2="-97.6"/><line x1="39.8" y1="-96.1" x2="37.5" y2="-90.5"/><line x1="52.0" y1="-90.1" x2="50.5" y2="-87.5"/><line x1="63.3" y1="-82.5" x2="61.5" y2="-80.1"/><line x1="73.5" y1="-73.5" x2="69.3" y2="-69.3"/><line x1="82.5" y1="-63.3" x2="80.1" y2="-61.5"/><line x1="90.1" y1="-52.0" x2="87.5" y2="-50.5"/><line x1="96.1" y1="-39.8" x2="90.5" y2="-37.5"/><line x1="100.5" y1="-26.9" x2="97.6" y2="-26.1"/><line x1="103.1" y1="-13.6" x2="100.1" y2="-13.2"/></g>
        <g class="badge" @click="go('/scargo')" style="cursor:pointer"><animateTransform attributeName="transform" type="rotate" from="0 72.3 -80.3" to="-360 72.3 -80.3" dur="70s" repeatCount="indefinite"/>
          <g transform="translate(72.3,-80.3)"><circle r="24" fill="#0b1d36" stroke="url(#gg)" stroke-width="1.6"/><g transform="translate(-17,-13) scale(.34)"><path d="M0 8 H62 V52 H0Z" fill="#fff"/><path d="M62 20 H88 L100 36 V52 H62Z" fill="#fff"/><path d="M70 26 H84 L92 36 H70Z" fill="#0b1d36"/><circle cx="22" cy="56" r="10" fill="#fff" stroke="#0b1d36" stroke-width="4"/><circle cx="80" cy="56" r="10" fill="#fff" stroke="#0b1d36" stroke-width="4"/><path d="M-4 4 A52 52 0 0 1 104 4" fill="none" stroke="#ff9f1c" stroke-width="7" stroke-linecap="round" transform="translate(0,2) scale(.8) translate(10,4)"/></g></g>
        </g>
        <g class="badge" @click="go('/sbot')" style="cursor:pointer"><animateTransform attributeName="transform" type="rotate" from="0 -72.3 80.3" to="-360 -72.3 80.3" dur="70s" repeatCount="indefinite"/>
          <g transform="translate(-72.3,80.3)"><circle r="24" fill="#0b1d36" stroke="url(#gg)" stroke-width="1.6"/><g transform="translate(-15,-14) scale(.26)"><path d="M18 52 A47 47 0 0 1 112 52" fill="none" stroke="#ff9f1c" stroke-width="9" stroke-linecap="round" transform="translate(0,-4)"/><rect x="46" y="30" width="38" height="26" rx="4" fill="#fff"/><path d="M46 43h38M65 30v26" stroke="#0b1d36" stroke-width="3"/><rect x="22" y="58" width="86" height="30" rx="9" fill="#fff"/><circle cx="40" cy="96" r="11" fill="#fff"/><circle cx="90" cy="96" r="11" fill="#fff"/></g></g>
        </g>
      </g>
      <path d="M 30.6 -65.7 A 40 40 0 1 0 0 0 A 40 40 0 1 1 -30.6 65.7" fill="none" stroke="url(#gg2)" stroke-width="11" stroke-linecap="round"/>
      <path d="M 30.6 -65.7 A 40 40 0 1 0 0 0 A 40 40 0 1 1 -30.6 65.7" fill="none" stroke="#0b1d36" stroke-width="1.6" stroke-linecap="round"/>
      <circle cx="30.6" cy="-65.7" r="4.2" fill="#0b1d36" stroke="url(#gg2)" stroke-width="1.6"/>
      <circle cx="-30.6" cy="65.7" r="4.2" fill="#0b1d36" stroke="url(#gg2)" stroke-width="1.6"/>
    </svg>
    <p v-if="msg" class="msg">{{ msg }}</p>
    <h1 class="name">S&nbsp;GROUP</h1>
    <p class="desc">화물 운송부터 허브 자동화까지, 물류의 모든 흐름을 하나로 잇습니다</p>
    <p class="en">CONNECTING EVERY FLOW OF LOGISTICS</p>
    <form v-if="!authState.user" class="login" @submit.prevent="login">
      <label><span>아이디</span><input v-model.trim="uid" type="text" autocomplete="username" placeholder="ID" /></label>
      <label><span>비밀번호</span><input v-model="upw" type="password" autocomplete="current-password" placeholder="PASSWORD" /></label>
      <button type="submit" :disabled="busy">{{ busy ? "확인 중…" : "로그인" }}</button>
      <p v-if="err" class="err">{{ err }}</p>
      <RouterLink to="/regi" class="join">회원가입</RouterLink>
    </form>
    <p v-else class="hint">들어갈 곳을 선택하세요</p>
    <div class="cards">
      <button type="button" class="card" @click="go('/scargo')">
        <b>S CARGO</b><span>항만·화물 운송 플랫폼</span>
      </button>
      <button type="button" class="card" @click="go('/sbot')">
        <b>S-BOT</b><span>허브 자율 이송 로봇 관제</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import axios from "axios";
import { authState, clearLogin, setLogin } from "@/auth/authState.js";
import { API_BASE } from "@/utils/apiBase.js";

const router = useRouter();
const msg = ref("");
const uid = ref("");
const upw = ref("");
const err = ref("");
const busy = ref(false);

function login() {
  err.value = "";
  if (!uid.value || !upw.value) { err.value = "아이디와 비밀번호를 입력해 주세요."; return; }
  busy.value = true;
  axios.post(`${API_BASE}/api/accounts/login`, { userId: uid.value, userPw: upw.value }, { withCredentials: true })
    .then((resp) => {
      if (!resp.data || resp.data.userId === undefined) { err.value = "아이디나 비밀번호를 확인해 주세요."; return; }
      setLogin(resp.data);
      upw.value = "";
      const back = sessionStorage.getItem("location");
      sessionStorage.removeItem("location");
      // 어디로 가려던 중이었다면 그곳으로, 아니면 S그룹에 머물며 직접 선택
      if (back && back !== "/" && back !== "/login") router.push(back);
    })
    .catch((e) => { err.value = e.response?.data?.message || "아이디 또는 비밀번호를 확인해 주세요."; })
    .finally(() => { busy.value = false; });
}

const scopeLabel = computed(() => {
  const u = authState.user;
  if (!u || u.userType !== "ADMIN") return "";
  return u.adminScope === "SBOT" ? "S-Bot 관리자" : "S Cargo 관리자";
});

function go(p) {
  msg.value = "";
  if (p === "/sbot") {
    const u = authState.user;
    if (!u) {
      msg.value = "S-Bot 은 로그인 후 이용할 수 있습니다. 위에서 S-Bot 관리자 계정으로 로그인해 주세요.";
      return;
    }
    if (u.userType !== "ADMIN" || u.adminScope !== "SBOT") {
      msg.value = "S-Bot 은 S-Bot 관리자 계정(sbot)으로 로그인해야 합니다. 오른쪽 위 '로그아웃' 후 다시 로그인해 주세요.";
      return;
    }
  }
  if (p === "/scargo") {
    const u = authState.user;
    if (u && u.userType === "ADMIN" && u.adminScope === "SBOT") {
      msg.value = "S Cargo 는 S Cargo 관리자 계정(admin)으로 로그인해야 합니다. 오른쪽 위 '로그아웃' 후 다시 로그인해 주세요.";
      return;
    }
  }
  router.push(p);
}

function logout() {
  axios.post(`${API_BASE}/api/accounts/logout`, {}, { withCredentials: true })
    .catch(() => {})
    .finally(() => { clearLogin(); msg.value = ""; });
}
</script>

<style scoped>
.grp { position: relative; height: 100vh; overflow: hidden; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 2vh 16px; box-sizing: border-box;
  background: radial-gradient(circle at 50% 38%, #173258 0%, #0b1d36 55%, #071426 100%); color: #e9d9ad; text-align: center; }
.emb { height: min(36vh, 78vw); width: auto; aspect-ratio: 1; overflow: visible; flex-shrink: 1; min-height: 0; }
.topbar { position: absolute; top: 14px; right: 26px; display: flex; gap: 14px; align-items: center; font-size: 14px; color: #b9a574; }
.topbar a { color: #f1e2b8; text-decoration: none; border: 1px solid rgba(216,169,78,.5); padding: 6px 14px; border-radius: 999px; }
.topbar a:hover { background: rgba(216,169,78,.18); }
.topbar em { font-style: normal; color: #8f9cb0; }
.msg { position: absolute; top: 58px; left: 50%; transform: translateX(-50%); z-index: 5; width: max-content; max-width: min(560px, 90vw); margin: 0; padding: 10px 16px; border-radius: 10px; background: rgba(40,48,60,.92); border: 1px solid rgba(216,169,78,.5); color: #f1e2b8; font-size: 14px; line-height: 1.5; }
.name { font-family: "Cormorant Garamond", "GFS Didot", "Times New Roman", serif; font-weight: 500; letter-spacing: .6em; margin: 1.5vh 0 0 .6em; font-size: clamp(26px, 5.2vh, 48px); color: #f1e2b8; line-height: 1.1; }
.desc { margin: 1.6vh 0 0; font-size: clamp(13px, 2vh, 18px); font-weight: 300; letter-spacing: .12em; color: #e0cf9f; }
.en { margin: 1vh 0 0; font-size: 11px; letter-spacing: .38em; color: #8f9cb0; }
.login { width: min(340px, 88vw); margin-top: 2.4vh; display: flex; flex-direction: column; gap: 8px; padding: 14px 20px 10px; border-radius: 16px; border: 1px solid rgba(216,169,78,.45); background: rgba(11,29,54,.55); backdrop-filter: blur(4px); }
.login label { display: flex; flex-direction: column; gap: 4px; text-align: left; font-size: 11px; letter-spacing: .2em; color: #b9a574; }
.login input { height: 36px; padding: 0 14px; border-radius: 10px; border: 1px solid rgba(216,169,78,.35); background: rgba(7,20,38,.8); color: #f1e2b8; font-size: 14px; letter-spacing: .04em; outline: none; }
.login input:focus { border-color: #f6e4b0; box-shadow: 0 0 0 2px rgba(216,169,78,.25); }
.login button { height: 38px; border: 0; border-radius: 10px; cursor: pointer; font-weight: 700; letter-spacing: .3em; color: #0b1d36; background: linear-gradient(135deg, #f6e4b0, #d8a94e 55%, #a77a2a); }
.login button:disabled { opacity: .6; cursor: default; }
.login .err { margin: 0; color: #ff9a8a; font-size: 12px; }
.login .join { color: #9fb0c6; font-size: 12px; text-decoration: none; }
.login .join:hover { color: #f1e2b8; }
.hint { margin: 2.4vh 0 0; color: #b9a574; letter-spacing: .25em; font-size: 13px; }
.cards { display: flex; gap: 16px; margin-top: 2.4vh; flex-wrap: wrap; justify-content: center; }
.card { width: 240px; padding: 12px 16px; border-radius: 14px; border: 1px solid rgba(216,169,78,.55); background: rgba(11,29,54,.7); color: #f1e2b8; cursor: pointer; transition: .2s; }
.card b { display: block; letter-spacing: .3em; font-size: 14px; }
.card span { display: block; margin-top: 6px; font-size: 12px; color: #9fb0c6; }
.card:hover { transform: translateY(-3px); border-color: #f6e4b0; background: rgba(23,50,88,.9); }
</style>
