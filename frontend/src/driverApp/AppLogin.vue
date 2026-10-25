<template>
  <!-- 26.10.08 추가: 기사 앱 전용 로그인 화면 (웹 login.vue와 같은 /api/accounts/login 사용) -->
  <div class="app-login">
    <header class="app-login-hero">
      <img :src="logo" alt="S Cargo" class="app-login-logo" />
      <p class="app-login-tag">화물차 기사 전용</p>
    </header>

    <form class="app-login-card" @submit.prevent="login">
      <h1>로그인</h1>

      <label class="field">
        <span>아이디</span>
        <input id="drv-login-id" v-model.trim="id" type="text" autocomplete="username" autocapitalize="off" placeholder="아이디" required />
      </label>

      <label class="field">
        <span>비밀번호</span>
        <div class="pw-wrap">
          <input id="drv-login-pw" v-model="pw" :type="pwVisible ? 'text' : 'password'" autocomplete="current-password" placeholder="비밀번호" required />
          <button type="button" class="pw-toggle" :aria-label="pwVisible ? '비밀번호 숨기기' : '비밀번호 보기'" @click="pwVisible = !pwVisible">
            <i :class="pwVisible ? 'bi bi-eye-slash' : 'bi bi-eye'"></i>
          </button>
        </div>
      </label>

      <label class="save-id">
        <input id="drv-login-save" v-model="saveId" type="checkbox" />
        <span>아이디 저장</span>
      </label>

      <p v-if="error" class="app-login-error" role="alert">{{ error }}</p>

      <button type="submit" class="app-login-btn" :disabled="busy">
        {{ busy ? '로그인 중…' : '로그인' }}
      </button>
    </form>

    <section class="server-box">
      <button type="button" class="server-toggle" @click="serverOpen = !serverOpen">
        <i class="bi bi-hdd-network"></i>
        <span>서버 주소 · <b>{{ apiBase }}</b></span>
        <i :class="serverOpen ? 'bi bi-chevron-up' : 'bi bi-chevron-down'"></i>
      </button>
      <div v-if="serverOpen" class="server-edit">
        <p>백엔드(Spring Boot)가 켜진 노트북 주소를 넣으세요. 휴대폰과 노트북이 같은 와이파이에 있어야 합니다. 에뮬레이터는 <code>http://10.0.2.2:8080</code></p>
        <div class="server-row">
          <input id="drv-server-url" v-model.trim="serverInput" type="url" inputmode="url" autocapitalize="off" placeholder="http://192.168.0.10:8080" />
          <button type="button" @click="saveServer">저장</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { setLogin, isDriverType } from '@/auth/authState.js'
import { API_BASE, setApiBase } from '@/utils/apiBase.js'
import logo from '@/assets/scargo_logo_4x.png'

const SAVED_ID_KEY = 'scargo_driver_saved_id'
const router = useRouter()

const savedId = (() => { try { return localStorage.getItem(SAVED_ID_KEY) || '' } catch { return '' } })()
const id = ref(savedId)
const pw = ref('')
const saveId = ref(!!savedId)
const pwVisible = ref(false)
const busy = ref(false)
const error = ref('')

const apiBase = API_BASE
const serverOpen = ref(false)
const serverInput = ref(API_BASE)

function rememberId(userId) {
  try {
    if (saveId.value && userId) localStorage.setItem(SAVED_ID_KEY, userId)
    else localStorage.removeItem(SAVED_ID_KEY)
  } catch { /* 저장 불가 환경은 무시 */ }
}

async function login() {
  error.value = ''
  busy.value = true
  try {
    const resp = await axios.post(`${API_BASE}/api/accounts/login`, { userId: id.value, userPw: pw.value }, { withCredentials: true })
    const mem = resp.data
    if (!mem || mem.userId === undefined) {
      error.value = '아이디나 비밀번호를 확인해 주세요.'
      return
    }
    // 기사 앱은 화물차 기사 계정만 사용한다. (26.10.22 통합: GENERAL · CONTAINER_DRIVER · LINEHAUL_DRIVER)
    if (mem.userType && !isDriverType(mem.userType)) {
      axios.post(`${API_BASE}/api/accounts/logout`, {}, { withCredentials: true }).catch(() => {})
      error.value = '화물차 기사 계정으로 로그인해 주세요. 관리자·사업자는 웹에서 이용할 수 있습니다.'
      return
    }
    rememberId(mem.userId)
    setLogin(mem)
    router.replace('/driver/app/status')
  } catch (err) {
    if (err.response) error.value = err.response.data?.message || '아이디 또는 비밀번호를 확인해 주세요.'
    else error.value = `서버(${API_BASE})에 연결할 수 없습니다. 아래 서버 주소와 와이파이를 확인해 주세요.`
  } finally {
    busy.value = false
  }
}

function saveServer() {
  setApiBase(serverInput.value)
  // 모든 화면이 새 주소를 쓰도록 앱을 다시 불러온다.
  window.location.reload()
}
</script>

<style scoped>
.app-login {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, var(--color-primary, #0a2540) 0 260px, var(--color-bg, #f4f6f8) 260px);
  padding: calc(env(safe-area-inset-top, 0px) + 28px) 20px calc(env(safe-area-inset-bottom, 0px) + 24px);
  font-family: 'Inter', system-ui, sans-serif;
  color: var(--color-text, #0a2540);
}
.app-login-hero { text-align: center; padding: 18px 0 26px; }
.app-login-logo { height: 48px; width: auto; max-width: 70%; }
.app-login-tag { margin: 10px 0 0; color: rgba(255, 255, 255, 0.72); font-size: 13px; font-weight: 600; letter-spacing: 0.04em; }
.app-login-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px 20px 22px;
  box-shadow: 0 12px 32px rgba(10, 37, 64, 0.14);
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.app-login-card h1 { font-family: 'Barlow Condensed', 'Inter', sans-serif; font-size: 26px; font-weight: 700; margin: 0 0 2px; }
.field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; font-weight: 600; color: var(--color-subtext, #64748b); margin: 0; }
.field input, .server-row input {
  width: 100%;
  min-height: 50px;
  border: 1px solid #d9e0e8;
  border-radius: 10px;
  padding: 0 14px;
  font-size: 16px;
  color: var(--color-text, #0a2540);
  background: #f9fafb;
}
.field input:focus, .server-row input:focus { outline: 2px solid var(--color-accent, #ff6b00); outline-offset: 0; border-color: transparent; background: #fff; }
.pw-wrap { position: relative; }
.pw-wrap input { padding-right: 48px; }
.pw-toggle { position: absolute; right: 4px; top: 50%; transform: translateY(-50%); width: 42px; height: 42px; border: 0; background: none; color: var(--color-subtext, #64748b); font-size: 18px; }
.save-id { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--color-subtext, #64748b); margin: 0; }
.save-id input { width: 18px; height: 18px; accent-color: var(--color-accent, #ff6b00); }
.app-login-error { margin: 0; padding: 10px 12px; border-radius: 8px; background: rgba(239, 68, 68, 0.08); color: #c62828; font-size: 13.5px; line-height: 1.45; }
.app-login-btn {
  min-height: 52px;
  border: 0;
  border-radius: 10px;
  background: var(--color-accent, #ff6b00);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  margin-top: 4px;
}
.app-login-btn:disabled { opacity: 0.7; }
.server-box { margin-top: auto; padding-top: 22px; }
.server-toggle {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: none;
  color: var(--color-subtext, #64748b);
  font-size: 12.5px;
  padding: 8px 4px;
  text-align: left;
}
.server-toggle span { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.server-toggle b { color: var(--color-text, #0a2540); font-weight: 600; }
.server-edit { background: #fff; border-radius: 12px; padding: 14px; border: 1px solid #e3e8ef; }
.server-edit p { margin: 0 0 10px; font-size: 12.5px; color: var(--color-subtext, #64748b); line-height: 1.5; }
.server-edit code { font-size: 12px; color: var(--color-text, #0a2540); }
.server-row { display: flex; gap: 8px; }
.server-row input { min-height: 44px; font-size: 14px; }
.server-row button { flex: 0 0 auto; min-height: 44px; padding: 0 16px; border: 0; border-radius: 10px; background: var(--color-primary, #0a2540); color: #fff; font-weight: 700; }
</style>
