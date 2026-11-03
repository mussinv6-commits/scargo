<template>
  <div class="phone">
    <!-- fix_1517: 공통 상단 바 (홈: ☰ 메뉴 · 그 외: ← 뒤로 + 화면 이름) -->
    <header v-if="showHeader" class="appbar">
      <!-- fix_1540: 모든 화면 ☰ 메뉴 (뒤로가기는 하드웨어·브라우저 뒤로) -->
      <button class="ab-btn" aria-label="메뉴 열기" @click="drawer = true">☰</button>
      <span class="ab-title">{{ title }}</span>
    </header>
    <div class="app-body">
      <RouterView v-slot="{ Component }">
        <component :is="Component" />
      </RouterView>
    </div>
    <TabBar v-if="showTab" />
    <!-- 사이드 메뉴 -->
    <div v-if="drawer" class="dr-bg" @click.self="drawer = false">
      <div class="dr-col" @click.self="drawer = false">
      <aside class="drawer">
        <div class="dr-head"><div class="dr-av">{{ (session.me?.userName || '기')[0] }}</div><div class="dr-who"><b>{{ session.me?.userName || '기사' }}</b><div class="dr-role">{{ roleName }}</div></div><button class="dr-x" aria-label="메뉴 닫기" @click="drawer = false">✕</button></div>
        <nav class="dr-menu">
          <RouterLink v-for="m in menu" :key="m.key" :to="m.to" @click="drawer = false" :style="{ '--c': m.color, '--b': m.bg }"><span class="dr-ic"><Icon :name="m.icon" /></span>{{ m.label }}</RouterLink>
        </nav>
        <button class="dr-out" @click="askOut = true">로그아웃</button>
      </aside>
      </div>
    </div>
    <!-- fix_app_1026: 서버 연결 실패 화면 (저장된 내 정보만으로 로그인된 것처럼 보이지 않게) -->
    <div v-if="session.token && session.conn !== 'ok'" class="conn-bg">
      <div class="conn-box">
        <template v-if="session.conn === 'checking'"><div class="spinner"></div><b>서버 연결 확인 중…</b></template>
        <template v-else>
          <div class="conn-ic">!</div>
          <b class="conn-t">서버에 연결할 수 없습니다</b>
          <p class="conn-m">서버 주소: {{ getApiBase() || '개발 프록시' }}<br />백엔드가 켜져 있는지, 휴대폰과 PC가 같은 Wi-Fi인지 확인하세요.</p>
          <button class="btn primary" @click="checkServer">다시 시도</button>
          <button class="btn" @click="session.serverSheet = true">서버 주소 설정</button>
          <button class="conn-out" @click="doLogout">로그아웃하고 로그인 화면으로</button>
        </template>
      </div>
    </div>
    <AppAsk /><!-- 26.10.29 앱 입력 팝업 (개봉 승인 비밀번호 · 롤테이너 바코드 등) -->
    <ServerSheet v-if="session.serverSheet" @close="session.serverSheet = false" @saved="onServerSaved" />
    <!-- 26.10.29 허브 화재 경보: 진행 중이면 위쪽 붉은 띠(누르면 안내 다시 보기), 처음 한 번 경고 창 + 진동. 화면 전체를 가리지 않음(운전 중) -->
    <button v-if="inbox.fire && !inbox.firePopup" type="button" class="fire-bar" :class="inbox.fire.kind" @click="inbox.firePopup = true">
      🔥 {{ FIRE_TEXT[inbox.fire.kind].title }} · {{ FIRE_TEXT[inbox.fire.kind].sub }}
    </button>
    <div v-if="inbox.fire && inbox.firePopup" class="dr-bg center fire-bg">
      <div class="conn-box fire-box" :class="inbox.fire.kind" role="alertdialog" aria-label="화재 경보">
        <div class="fire-ic">🚨</div>
        <b class="fire-t">{{ FIRE_TEXT[inbox.fire.kind].title }}</b>
        <p v-if="inbox.fire.label" class="fire-l">{{ inbox.fire.label }}</p>
        <p class="fire-s">{{ FIRE_TEXT[inbox.fire.kind].sub }}</p>
        <ul class="fire-steps"><li v-for="t in FIRE_TEXT[inbox.fire.kind].steps" :key="t">{{ t }}</li></ul>
        <p class="fire-n">관제에서 화재 종료를 하면 이 안내가 사라집니다</p>
        <button class="btn primary fire-ok" @click="ackFire()">확인</button>
      </div>
    </div>
    <!-- 26.10.28: 새 알림 팝업 -->
    <div v-if="inbox.popup" class="dr-bg center" @click.self="dismissPopup()">
      <div class="conn-box" style="text-align:left" @click.stop>
        <b class="conn-t">{{ inbox.popup.title }}</b>
        <p class="conn-m">{{ inbox.popup.message }}</p>
        <button class="btn primary" @click="openNotif">확인</button>
        <button class="btn" style="margin-top:8px" @click="dismissPopup()">닫기</button>
      </div>
    </div>
    <div v-if="askOut" class="dr-bg center" @click.self="askOut = false"><div class="mdl"><b>로그아웃할까요?</b><div class="mdl-btns"><button class="m-no" @click="askOut = false">취소</button><button class="m-yes" @click="doLogout">로그아웃</button></div></div></div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, computed, ref, watch } from 'vue'
import Icon from './components/Icon.vue'
import { driverMenu, DRIVER_HOME } from './lib/menu'
import { Capacitor } from '@capacitor/core'
import { App as CapApp } from '@capacitor/app'
import { useRouter, useRoute } from 'vue-router'
import { APP_HOME } from './mockup/store.js'
import { session } from './lib/session'
import TabBar from './components/TabBar.vue'
import { setUnauthorizedHandler, getApiBase, isNative } from './lib/api'
import ServerSheet from './components/ServerSheet.vue'
import AppAsk from './components/AppAsk.vue'
import { Api } from './lib/api'
import { clearSession, isLoggedIn, logout, verifySession } from './lib/session'
import { startTrip, resetTrip } from './lib/tracker'
import { inbox, startInbox, stopInbox, dismissPopup, ackFire, FIRE_TEXT } from './lib/inbox'

const router = useRouter()
const route = useRoute()
// fix_1517: 첫 화면(ROOTS)에서는 뒤로 대신 앱 종료. 기사 홈(D-00)·허브 H-01·로그인·승인 대기
const ROOTS = ['/', '/m/A-01', '/m/D-00', '/m/H-01', '/m/A-03', '/login']
const NOHDR = ['/m/A-01', '/login'] // A-02 가입 단계는 아래에서 숨김
const DRIVER_TAB = ['/m/C-00', '/m/C-01', '/m/C-02', '/m/D-00', '/m/T-01', '/m/P-01', '/m/F-01', '/alerts', '/me', '/navi', '/history']
const NAMES = { 'C-00': '출발 전 점검', 'C-01': '차량 점검표', 'C-02': '컨테이너 점검표', 'D-00': 'S Cargo', 'T-01': '운송 오더', 'T-02': '인수 확인', 'T-03': '운송 진행', 'P-01': '지선 운행', 'P-03': '지선 운행', 'P-04': '센터 도착', 'F-01': '통행 요금', 'H-01': '허브 관제', 'A-03': '승인 대기', 'A-02-1': '회원가입', 'A-02-2': '회원가입', 'A-02-3': '회원가입', 'A-02-4': '회원가입' }
const PATHS = { '/alerts': '알림', '/me': '내 정보', '/navi': '내비게이션', '/history': '운송 이력', '/arrive': '도착 처리' }
const isDriver = computed(() => ['CONTAINER_DRIVER', 'BRANCH_DRIVER'].includes(session.me?.userType))
const isHome = computed(() => route.path === DRIVER_HOME)
const showHeader = computed(() => !NOHDR.includes(route.path) && !route.path.startsWith('/ops/') && !route.path.startsWith('/m/A-02')) // 26.10.28: 가입 화면은 메뉴(로그인 상태 사이드바) 숨김
const showBack = computed(() => !ROOTS.includes(route.path))
const showTab = computed(() => isDriver.value && (DRIVER_TAB.includes(route.path) || route.path.startsWith('/navi/'))) // 26.10.24: /navi/1 처럼 오더 번호가 붙어도 하단 탭 (빈 띠 없앰)
const title = computed(() => NAMES[route.meta.sid] || PATHS['/' + route.path.split('/')[1]] || '')
const menu = computed(() => driverMenu())
const roleName = computed(() => ({ CONTAINER_DRIVER: '컨테이너 운송기사', BRANCH_DRIVER: '지선 운송기사', HUB_OP: '허브 관제사' })[session.me?.userType] || '')
const drawer = ref(false), askOut = ref(false)
function openNotif() { const id = inbox.popup?.id; dismissPopup(); router.push(id != null ? '/alerts/' + id : '/alerts') } // 26.10.29 팝업 확인 → 그 알림 상세
watch(() => route.fullPath, () => { drawer.value = false; const b = document.querySelector('.app-body'); if (b) b.scrollTop = 0 }) // fix_1517: 화면 바뀌면 맨 위부터
async function doLogout() { askOut.value = false; drawer.value = false; stopInbox(); resetTrip(); await logout(); router.replace('/login') } // fix_app_1027a
const home = () => APP_HOME[session.me?.userType] || '/m/A-01'
// fix_1502: 안드로이드 하드웨어 뒤로가기 — 하위 화면은 뒤로, 첫 화면(로그인·T-01·P-01·H-01)에서는 앱 종료. 웹(dev)에서는 등록 안 함
let backSub = null
if (Capacitor.isNativePlatform()) {
  CapApp.addListener('backButton', () => { if (drawer.value || askOut.value) { drawer.value = askOut.value = false; return } if (ROOTS.includes(route.path)) CapApp.exitApp(); else goBack() }).then(h => (backSub = h)).catch(() => {})
}
onBeforeUnmount(() => { backSub && backSub.remove() })
function goBack() { if (window.history.state && window.history.state.back) router.back(); else router.replace(home()) }

// 토큰이 만료되면 로그인 화면으로
setUnauthorizedHandler(() => {
  stopInbox(); resetTrip() // fix_app_1027a
  clearSession()
  // 26.10.28: 회원가입 화면에서는 로그인으로 튕기지 않음 (공개 API·토큰 없음)
  if (!route.path.startsWith('/m/A-02') && route.path !== '/m/A-01' && route.path !== '/login') router.replace('/login')
})

// fix_app_1026: 앱 시작 시 서버·토큰 확인 → 성공해야 운행 이어하기
let resumed = false
async function checkServer() {
  if (!isLoggedIn()) return
  const ok = await verifySession()
  if (!ok || resumed) return
  resumeTrip()
}
// 앱을 다시 켰을 때(또는 로그인 직후) 내 오더가 운행 중이면 위치 전송·음성 안내를 이어서 시작
function resumeTrip() {
  resumed = true
  const token = session.token
  Api.home().then(h => {
    if (session.token === token && h.current?.status === 'IN_TRANSIT') startTrip(h.current.orderId)
  }).catch(() => {})
}
function onServerSaved() { if (isLoggedIn() && session.conn === 'down') checkServer() }
onMounted(async () => { if (isNative) { try { const { ensureApiBase } = await import('./lib/discover.js'); await ensureApiBase() } catch { /* 무시 */ } } checkServer(); if (isLoggedIn()) startInbox() })
// 26.10.27: 로그아웃 → 다른 기사로 로그인하면 그 기사 기준으로 다시 확인 (예전엔 첫 기사 한 번만 이어하기)
watch(() => session.token, (t, old) => {
  drawer.value = false; askOut.value = false
  if (!t) { resumed = false; stopInbox(); return }
  startInbox()
  if (t !== old && old !== undefined) resumeTrip()
})
</script>
<style>
/* fix_1517: 공통 상단 바·본문 스크롤·사이드 메뉴 */
.appbar{display:flex;align-items:center;gap:4px;height:calc(52px + var(--safe-t));padding:var(--safe-t) 6px 0;background:#102644;color:#fff;flex:0 0 auto}
.ab-btn{width:44px;height:44px;border:0;background:transparent;color:#EECC83;font-size:22px;cursor:pointer;border-radius:10px;line-height:1}
.ab-btn:active{background:rgba(255,255,255,.12)}
.ab-title{font-weight:700;font-size:17px;padding-left:4px}
.app-body{flex:1;min-height:0;overflow-y:auto;display:flex;flex-direction:column;-webkit-overflow-scrolling:touch}
.app-body > .screen{flex:1;min-height:0}
/* 화면마다 있던 옛 머리줄은 공통 바와 겹치므로 숨김 (보조 버튼만 남김) */
.app-body .topbar > .icon-btn:first-child,.app-body .topbar > h1,.app-body .topbar > span:first-child{display:none}
.app-body .topbar{min-height:0;padding:4px 8px;border-bottom:0;background:transparent;justify-content:flex-end}
.app-body .topbar:not(:has(> :not(h1):not(.icon-btn:first-child):not(span:first-child))){display:none}
.app-body .ap{min-height:0;flex:1 0 auto}
.app-body .ap .top{display:none}
/* 26.10.28 QA: 회원가입(A-02) 단계는 상단 바가 없어 화면 제목 · 단계(1/4)가 보이지 않던 문제 → 이 화면들만 제목 줄 표시 */
.app-body .ap section[id^="A-02"] .top{display:flex}
/* 26.10.27: 목업 머리줄을 숨긴 뒤 첫 줄이 상단 바에 붙어 잘려 보이던 문제 + 고정 높이(732px) 대신 본문 스크롤 */
.app-body .ap .scr{padding-top:14px;height:auto;min-height:0;overflow:visible;padding-bottom:24px}
.app-body .ap .tab{display:none}
.app-body .ap .nodata{position:static;margin:8px 0}
.dtab{display:grid;grid-template-columns:repeat(5,1fr)}
.dtab a{color:#6b7590}
.dtab a.on{font-weight:700}
.dtab .ti{width:40px;height:26px;border-radius:13px;display:flex;align-items:center;justify-content:center}
.dr-bg{position:fixed;inset:0;z-index:1000;background:rgba(16,38,68,.45);display:flex;justify-content:center}
.dr-bg.center{align-items:center}
/* fix_1550: 480px 칼럼 안에서 서랍 바깥(dr-col) 누르면 닫힘 */
.dr-col{position:relative;width:100%;max-width:480px;height:100%;cursor:pointer}
.drawer{position:absolute;left:0;top:0;bottom:0;width:min(62%,240px);background:#fff;display:flex;flex-direction:column;box-shadow:4px 0 18px rgba(0,0,0,.2);cursor:default;animation:drIn .18s ease-out}
@keyframes drIn{from{transform:translateX(-100%)}to{transform:none}}
/* 26.10.27: 서랍 머리줄 높이 = 상단 바 높이 (예전엔 더 높아서 메뉴를 열면 상단 바 위로 계단처럼 겹쳐 보였음) */
.dr-head{background:#102644;color:#fff;display:flex;gap:10px;align-items:center;height:calc(52px + var(--safe-t));padding:var(--safe-t) 6px 0 12px;flex:0 0 auto;box-sizing:border-box}
.dr-who{flex:1;min-width:0}.dr-who b{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.dr-x{width:40px;height:40px;border:0;border-radius:8px;background:rgba(255,255,255,.1);color:#EECC83;font-size:17px;cursor:pointer;flex:0 0 auto}
.dr-av{width:34px;height:34px;border-radius:50%;background:#EECC83;color:#102644;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:15px;flex:0 0 auto}
.dr-who b{font-size:15px;line-height:1.25}
.dr-role{font-size:11.5px;color:#cfd6e6;line-height:1.3;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.dr-menu{flex:1;overflow:auto;display:flex;flex-direction:column;padding:6px 0}
.dr-menu a{display:flex;align-items:center;gap:10px;padding:10px 14px;color:#1d2433;text-decoration:none;font-weight:600;font-size:14.5px}
.dr-menu a.router-link-active{background:#FBF1DA}
.dr-ic{width:32px;height:32px;border-radius:9px;background:var(--b);color:var(--c);display:flex;align-items:center;justify-content:center;flex:0 0 auto}
/* 26.10.24: 로그아웃 버튼 크게 · 가운데 정렬 */
.dr-out{display:flex;align-items:center;justify-content:center;gap:8px;margin:10px 12px calc(14px + var(--safe-b));height:54px;border:1.5px solid #F2B8B5;border-radius:12px;background:#FFF5F4;color:#C62828;font-weight:800;font-size:17px;cursor:pointer;text-align:center;padding:0 16px}
.dr-out:active{background:#FDE4E4}
.dr-bg .mdl{background:#fff;border-radius:14px;padding:20px;width:290px}
.mdl-btns{display:flex;justify-content:flex-end;gap:8px;margin-top:16px}
.m-no,.m-yes{border:0;border-radius:8px;padding:9px 16px;font-weight:700;cursor:pointer}
.m-no{background:#eceff4;color:#102644}.m-yes{background:#102644;color:#EECC83}
.conn-bg{position:fixed;inset:0;z-index:1500;background:#f4f6fa;display:flex;justify-content:center;align-items:center;padding:24px}
.conn-box{width:100%;max-width:360px;background:#fff;border-radius:16px;padding:24px 20px;display:flex;flex-direction:column;gap:10px;align-items:stretch;text-align:center;box-shadow:0 4px 18px rgba(16,38,68,.12)}
.conn-ic{width:52px;height:52px;border-radius:50%;background:#FDECEC;color:#C62828;font-size:28px;font-weight:800;display:flex;align-items:center;justify-content:center;margin:0 auto}
.conn-t{font-size:18px;color:#102644}.conn-m{margin:0 0 6px;font-size:13px;color:#6b7590;line-height:1.5;word-break:break-all}
.conn-out{border:0;background:transparent;color:#6b7590;font-size:13px;text-decoration:underline;cursor:pointer;padding:6px}
/* 26.10.29 허브 화재 경보 */
.fire-bar{position:fixed;left:56px;right:8px;top:calc(env(safe-area-inset-top,0px) + 7px);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;z-index:2600;border:0;border-radius:12px;padding:10px 14px;font-size:14px;font-weight:800;color:#fff;background:#D32F2F;box-shadow:0 4px 16px rgba(211,47,47,.45);text-align:left;animation:fire-blink 1.2s ease-in-out infinite alternate;cursor:pointer}
.fire-bar.batt{background:#E65100;box-shadow:0 4px 16px rgba(230,81,0,.45)}
@keyframes fire-blink{from{filter:brightness(.86)}to{filter:brightness(1.08)}}
.fire-bg{z-index:2700}
.fire-box{border:3px solid #D32F2F}.fire-box.batt{border-color:#E65100}
.fire-ic{font-size:44px;line-height:1}
.fire-t{font-size:22px;color:#D32F2F}.fire-box.batt .fire-t{color:#E65100}
.fire-l{margin:0;font-size:13px;color:#6b7590}
.fire-s{margin:2px 0;font-size:18px;font-weight:800;color:#102644}
.fire-steps{margin:0;padding:10px 14px 10px 28px;background:#F6F7F9;border-radius:10px;text-align:left;font-size:14px;line-height:1.7;color:#2b3550}
.fire-n{margin:0;font-size:12px;color:#8a93a8}
.fire-box .fire-ok{background:#D32F2F!important;border-color:#D32F2F!important;color:#fff!important}.fire-box.batt .fire-ok{background:#E65100!important;border-color:#E65100!important}
</style>
