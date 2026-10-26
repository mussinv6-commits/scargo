<template>
  <!-- 26.10.20 추가: S그룹 메인(계열사 선택). S Cargo / S-Bot 으로 나뉘어 들어간다. -->
  <div class="grp">
    <div class="topbar">
      <template v-if="authState.user">
        <span class="who">{{ whoName }}님<em v-if="scopeLabel"> · {{ scopeLabel }}</em></span>
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
      <circle r="74" fill="transparent" style="cursor:pointer" @click="openCredits"><title>S GROUP 연혁</title></circle>
    </svg>
    <p v-if="msg" class="msg">{{ msg }}</p>
    <h1 class="name">S&nbsp;GROUP</h1>
    <p class="desc">화물 운송부터 허브 자동화까지, 물류의 모든 흐름을 하나로 잇습니다</p>
    <p class="en">CONNECTING EVERY FLOW OF LOGISTICS</p>
    <form v-if="!authState.user" class="login" @submit.prevent="login">
      <!-- 26.10.24 팀 반영(예지님 v7): 아이디 저장(아이디칸 오른쪽) + 비밀번호 보기/숨기기 -->
      <div class="fld"><label for="gm-uid">아이디</label>
        <span class="id-wrap">
          <input id="gm-uid" v-model.trim="uid" type="text" autocomplete="username" placeholder="ID" />
          <label class="save-id"><input type="checkbox" v-model="rememberId" @change="onRememberChange" /> 아이디 저장</label>
        </span>
      </div>
      <div class="fld"><label for="gm-upw">비밀번호</label>
        <span class="pw-wrap">
          <input id="gm-upw" v-model="upw" :type="showPw ? 'text' : 'password'" autocomplete="current-password" placeholder="PASSWORD" />
          <button type="button" class="pw-eye" :aria-label="showPw ? '비밀번호 숨기기' : '비밀번호 보기'" :aria-pressed="showPw" @click="showPw = !showPw">
            <svg v-if="!showPw" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path fill="none" stroke="currentColor" stroke-width="2" d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"/><circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="2"/></svg>
            <svg v-else viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path fill="none" stroke="currentColor" stroke-width="2" d="M3 3l18 18M10.6 5.1A10.9 10.9 0 0 1 12 5c7 0 11 7 11 7a18 18 0 0 1-3.2 4.1M6.6 6.6C3.4 8.6 1 12 1 12s4 7 11 7c1.8 0 3.4-.5 4.8-1.2M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>
          </button>
        </span>
      </div>
      <button type="submit" :disabled="busy">{{ busy ? "확인 중…" : "로그인" }}</button>
      <p v-if="err" class="err">{{ err }}</p>
      <RouterLink to="/regi" class="join">회원가입</RouterLink>
    </form>
    <p v-else class="hint">들어갈 곳을 선택하세요</p>
    <div class="cards">
      <button type="button" class="card" @click="go('/scargo')">
        <div class="ti"><svg class="ic" viewBox="-24 -24 48 48"><circle r="22" fill="#0b1d36" stroke="url(#gg)" stroke-width="1.6"/><g transform="translate(-17,-13) scale(.34)"><path d="M0 8 H62 V52 H0Z" fill="#fff"/><path d="M62 20 H88 L100 36 V52 H62Z" fill="#fff"/><path d="M70 26 H84 L92 36 H70Z" fill="#0b1d36"/><circle cx="22" cy="56" r="10" fill="#fff" stroke="#0b1d36" stroke-width="4"/><circle cx="80" cy="56" r="10" fill="#fff" stroke="#0b1d36" stroke-width="4"/><path d="M-4 4 A52 52 0 0 1 104 4" fill="none" stroke="#ff9f1c" stroke-width="7" stroke-linecap="round" transform="translate(0,2) scale(.8) translate(10,4)"/></g></svg><b>S CARGO</b></div><span>항만·화물 운송 플랫폼</span>
      </button>
      <button type="button" class="card" @click="go('/sbot')">
        <div class="ti"><svg class="ic" viewBox="-24 -24 48 48"><circle r="22" fill="#0b1d36" stroke="url(#gg)" stroke-width="1.6"/><g transform="translate(-15,-14) scale(.26)"><path d="M18 52 A47 47 0 0 1 112 52" fill="none" stroke="#ff9f1c" stroke-width="9" stroke-linecap="round" transform="translate(0,-4)"/><rect x="46" y="30" width="38" height="26" rx="4" fill="#fff"/><path d="M46 43h38M65 30v26" stroke="#0b1d36" stroke-width="3"/><rect x="22" y="58" width="86" height="30" rx="9" fill="#fff"/><circle cx="40" cy="96" r="11" fill="#fff"/><circle cx="90" cy="96" r="11" fill="#fff"/></g></svg><b>S-BOT</b></div><span>허브 자율 이송 로봇 관제</span>
      </button>
    </div>
    <div v-if="credits" class="cred" @click="credits=false">
      <canvas ref="fx" class="fx"></canvas>
      <button type="button" class="cclose" @click.stop="credits=false">닫기 ✕</button>
      <div class="roll" @animationend="endCard()">
        <p class="c-logo">S&nbsp;GROUP</p>
        <p class="c-slogan">{{ SLOGAN }}</p>
        <template v-for="sec in CREDITS" :key="sec.title">
          <h3>{{ sec.title }}</h3>
          <p v-for="(l,i) in sec.lines" :key="i" :class="{ 'c-row': l.length===2 }">
            <template v-if="l.length===2"><b>{{ l[0] }}</b><span>{{ l[1] }}</span></template>
            <template v-else>{{ l[0] }}</template>
          </p>
        </template>
        <p class="c-end">S&nbsp;GROUP</p>
      </div>
      <div v-if="ended" class="endcard">
        <div class="et">
          <h2>못먹어도 S카고</h2>
          <i></i>
          <p>AI 기반 항만 화물차 예지보전 시스템</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { homeOf } from "@/scargo2/roles.js";
import { computed, ref, nextTick, onBeforeUnmount } from "vue";
import { useRouter } from "vue-router";
import axios from "axios";
import { authState, clearLogin, setLogin } from "@/auth/authState.js";
import { API_BASE } from "@/utils/apiBase.js";
import { hubAccess, isMaster } from "@/components/amr/amrAccess.js"; // 26.10.22 통합: 관제사는 허브 관제로
import { loadSavedId, storeSavedId } from "@/utils/savedId.js"; // 26.10.24 팀 반영: 아이디만 localStorage 저장


// ===== 회사 연혁(엔딩 크레딧) - 문구는 여기서 수정 =====
const SLOGAN = "CONNECTING EVERY FLOW OF LOGISTICS";
const CREDITS = [
  { title: "ABOUT", lines: [["화물 운송부터 허브 자동화까지, 물류의 모든 흐름을 하나로 잇습니다"], ["창립", "2026년 10월 1일"], ["구성", "S CARGO · S-BOT"]] },
  { title: "VISION", lines: [["항만에서 집 앞까지, 끊김 없는 스마트 물류"]] },
  { title: "CORE VALUES", lines: [["연결", "모든 흐름을 하나로"], ["정확", "데이터로 판정한다"], ["도전", "못 먹어도 고"]] },
  { title: "HISTORY", lines: [["2026.09", "프로젝트 시작 · 번호판 인식 모델 설계 · 인천신항 방문"], ["2026.10.01", "S Group 창립 · 화물차 번호판 인식 · 게이트 입차"], ["2026.10.07", "1차 발표"], ["2026.10.13", "2차 범위 확정 · 하이패스 방식 결정"], ["2026.10.15", "허브 AMR 관제 도입"], ["2026.10.20", "S Group 메인 출범 · S Cargo / S-Bot 분리"]] },
  { title: "TEAM", lines: [["CEO", "최길웅"], ["CIO", "김태수"], ["COO", "정예지"], ["CSO", "이한솔"]] },
];
const credits = ref(false);
const ended = ref(false);
const fx = ref(null);
let endTimer = null, raf = 0;
function startFx() {
  const c = fx.value; if (!c) return;
  const ctx = c.getContext("2d");
  const W = (c.width = window.innerWidth), H = (c.height = window.innerHeight);
  const ps = Array.from({ length: 120 }, () => ({ x: Math.random() * W, y: Math.random() * H, r: Math.random() * 3 + 0.8, v: Math.random() * 0.35 + 0.08, a: Math.random() * 0.6 + 0.2, t: Math.random() * 6 }));
  let tm = 0;
  const loop = () => {
    if (!fx.value) return;
    tm += 0.012; ctx.clearRect(0, 0, W, H);
    ctx.save(); ctx.globalCompositeOperation = "lighter";
    for (let i = 0; i < 5; i++) {
      const x = W * (0.1 + i * 0.22) + Math.sin(tm + i) * 40;
      const g = ctx.createLinearGradient(x, 0, x + W * 0.25, H);
      g.addColorStop(0, "rgba(70,140,210,.20)"); g.addColorStop(1, "rgba(70,140,210,0)");
      ctx.fillStyle = g; ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x + 90, 0); ctx.lineTo(x + W * 0.25 + 160, H); ctx.lineTo(x + W * 0.25, H); ctx.fill();
    }
    ctx.restore();
    for (const p of ps) {
      p.y -= p.v; p.x += Math.sin(tm + p.t) * 0.15; if (p.y < -5) { p.y = H + 5; p.x = Math.random() * W; }
      ctx.globalAlpha = p.a * (0.6 + 0.4 * Math.sin(tm * 2 + p.t));
      ctx.fillStyle = "#cfe6ff"; ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, 6.283); ctx.fill();
    }
    ctx.globalAlpha = 1; raf = requestAnimationFrame(loop);
  };
  loop();
}
function openCredits() { ended.value = false; credits.value = true; nextTick(startFx); }
function endCard() { ended.value = true; clearTimeout(endTimer); endTimer = setTimeout(() => { credits.value = false; }, 7000); }
onBeforeUnmount(() => { cancelAnimationFrame(raf); clearTimeout(endTimer); });

const router = useRouter();
const msg = ref("");
const uid = ref(loadSavedId());
const rememberId = ref(!!uid.value);
const showPw = ref(false);
function onRememberChange() { if (!rememberId.value) storeSavedId(false); }
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
      storeSavedId(rememberId.value, uid.value); // 아이디만 저장, 비밀번호는 저장 안 함
      setLogin(resp.data);
      showPw.value = false;
      upw.value = "";
      const back = sessionStorage.getItem("location");
      sessionStorage.removeItem("location");
      // 어디로 가려던 중이었다면 그곳으로, 아니면 S그룹에 머물며 직접 선택
      if (back && back !== "/" && back !== "/login") router.push(back);
    })
    .catch((e) => { err.value = e.response?.data?.message || "아이디 또는 비밀번호를 확인해 주세요."; })
    .finally(() => { busy.value = false; });
}

// 26.10.26: 통합 마스터(admin01)는 "마스터님 · S Group" 으로 표시
const whoName = computed(() => (isMaster(authState.user) ? "마스터" : authState.user?.userName || ""));
const scopeLabel = computed(() => {
  const u = authState.user;
  if (!u || u.userType !== "ADMIN") return "";
  if (isMaster(u)) return "S Group";
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
    if (u.userType !== "ADMIN") { // 26.10.24: 마스터 관리자도 S-Bot 허용
      // 26.10.22 통합: 통합 관제사(CONTROL) · 허브 관제사(HUB_OP)는 허브 AMR 관제 화면만 사용
      if (hubAccess(u).canView) {
        router.push("/sbot/hub");
        return;
      }
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
    if (u) return router.push(u.userType === "ADMIN" ? "/scargo" : homeOf(u)); // 26.10.26 총관리자는 S Cargo 메인(/scargo)으로. 나머지는 아래 권한별 첫 화면 // // v13 (scargo_1): 로그인 계정은 권한별 첫 화면으로 (관제사 → 관제, 기사 → 기사 화면, 승인대기 → 안내)
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
/* 26.10.24 팀 반영(예지님 v7): 아이디 저장 · 비밀번호 보기 */
.login .fld { display: flex; flex-direction: column; gap: 4px; text-align: left; }
.login .fld > label { font-size: 11px; letter-spacing: .2em; color: #b9a574; }
.login .id-wrap, .login .pw-wrap { position: relative; display: block; }
.login .id-wrap input[type="text"] { width: 100%; padding-right: 92px; box-sizing: border-box; }
.login .pw-wrap input { width: 100%; padding-right: 42px; box-sizing: border-box; }
.login .save-id { position: absolute; right: 10px; top: 50%; transform: translateY(-50%); display: flex; flex-direction: row; align-items: center; gap: 5px; font-size: 11px; letter-spacing: 0; color: #b9a574; cursor: pointer; white-space: nowrap; }
.login .save-id input { width: 13px; height: 13px; padding: 0; margin: 0; accent-color: #d8a94e; }
.login .pw-eye { position: absolute; right: 4px; top: 50%; transform: translateY(-50%); width: 34px; height: 30px; padding: 0; display: grid; place-items: center; background: transparent !important; color: #b9a574; letter-spacing: 0; }
.login .pw-eye:focus-visible { outline: 2px solid #f6e4b0; }
.hint { margin: 2.4vh 0 0; color: #b9a574; letter-spacing: .25em; font-size: 13px; }
.cards { display: flex; gap: 16px; margin-top: 2.4vh; flex-wrap: wrap; justify-content: center; }
.card { width: 250px; padding: 10px 16px; border-radius: 14px; border: 1px solid rgba(216,169,78,.55); background: rgba(11,29,54,.7); color: #f1e2b8; cursor: pointer; transition: .2s; }
.card .ti { display: flex; align-items: center; justify-content: center; gap: 12px; }
.card .ic { width: 36px; height: 36px; flex-shrink: 0; }
.card b { display: block; letter-spacing: .3em; font-size: 14px; }
.card span { display: block; margin-top: 6px; font-size: 12px; color: #9fb0c6; }
.card:hover { transform: translateY(-3px); border-color: #f6e4b0; background: rgba(23,50,88,.9); }

.cred { position: fixed; inset: 0; z-index: 50; overflow: hidden; background: #070f1d url('@/assets/end_scene.jpg') center / cover no-repeat; cursor: pointer; }
.fx { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.cclose { position: absolute; top: 16px; right: 22px; z-index: 2; background: transparent; color: #f1e2b8; border: 1px solid rgba(216,169,78,.5); border-radius: 999px; padding: 6px 14px; cursor: pointer; }
.roll { position: absolute; left: 50%; top: 0; width: min(640px, 90vw); transform: translate(-50%, 100vh); text-align: center; color: #e9d9ad; animation: roll 30s linear forwards; padding-bottom: 40vh; }
.endcard { position: absolute; inset: 0; z-index: 1; background: rgba(7,15,29,.55); display: flex; align-items: center; justify-content: center; animation: endIn 2s ease forwards; }
.et { text-align: center; color: #fff; opacity: 0; animation: endIn 2s ease 1.2s forwards; }
.et h2 { margin: 0; font-size: 44px; font-weight: 700; letter-spacing: .06em; }
.et i { display: block; width: 150px; height: 2px; margin: 14px auto; background: #d8a94e; }
.et p { margin: 0; font-size: 15px; color: #b9c8dc; letter-spacing: .12em; }
@keyframes endIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes roll { from { transform: translate(-50%, 100vh); } to { transform: translate(-50%, -100%); } }
.roll h3 { margin: 90px 0 22px; font-family: "Cormorant Garamond", "Times New Roman", serif; font-weight: 500; letter-spacing: .5em; font-size: 20px; color: #d8a94e; }
.roll p { margin: 10px 0; font-size: 17px; line-height: 1.7; letter-spacing: .06em; }
.c-logo, .c-end { font-family: "Cormorant Garamond", "Times New Roman", serif; font-size: 44px !important; letter-spacing: .6em !important; color: #f1e2b8; margin-top: 40px !important; }
.c-slogan { letter-spacing: .3em !important; font-size: 13px !important; color: #8f9cb0; }
.c-row { display: flex; flex-direction: column; align-items: center; gap: 2px; text-align: center; margin: 18px 0 !important; }
.c-row b { min-width: 0; font-weight: 500; letter-spacing: .25em; color: #f1e2b8; }
.c-row span { color: #c9d3e3; font-size: 15px; }
</style>
