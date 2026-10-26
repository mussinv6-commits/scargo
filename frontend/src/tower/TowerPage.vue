<template>
  <div class="tower">
    <div class="top">
      <div class="brand">SCARGO<small>컨트롤타워</small></div>
      <label for="role">{{ fromLogin ? '로그인 권한' : '권한 (시연 전환)' }}</label>
      <select id="role" v-model="role" :disabled="fromLogin" aria-label="권한 선택">
        <option value="CONTROL">통합 관제사 (CONTROL)</option>
        <option value="ADMIN">총관리자 (ADMIN)</option>
        <option value="HUB_OP">허브 관제사 (HUB_OP · 곤지암 허브)</option>
        <option value="CENTER_OP">지역센터 관제사 (CENTER_OP · 서울센터)</option>
        <option value="CARRIER">운송사 담당자 (역할 · 자기 운송사만)</option>
      </select>
      <div class="poll">다음 갱신 {{ left }}s · 폴링</div>
    </div>
    <div class="wrap">
      <nav aria-label="메뉴">
        <template v-for="g in menu" :key="g.group">
          <h4>{{ g.group }}</h4>
          <a v-for="it in g.items" :key="it[2]" href="#" :class="{ on: state.route === it[2] }" @click.prevent="go(it[2])">
            <span class="id">{{ it[0] }}</span>{{ it[1] }}<span class="perm">{{ PERM_LABEL[it[3][role]] }}</span></a>
        </template>
      </nav>
      <main v-html="html" @click="onClick" @input="onInput"></main>
    </div>
    <div class="toast" :class="{ show: msg }">{{ msg }}</div>
  </div>
</template>

<script setup>
// 기획 v2 웹 W-02/W-04/W-05/W-09/W-12 (권한별 메뉴·403 처리 포함)
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import './tower.css'
// import './tower-theme.css' // 26.10.21 v4: 기획 HTML 그대로(색·글꼴) 맞추기 위해 디자이너 S GROUP 테마 덮어쓰기 해제 (파일은 보존)
import { MENU, PERM_LABEL, FIRST, permOf, SCREENS, state, act, toastBus, MOCK } from './engine'
import { refresh, advanceException } from './api'
import { authState } from '@/auth/authState.js'

// 로그인 계정의 userType → 화면 권한. 운송사 담당자 = CORPORATE_APPROVED (+ companies.is_carrier, TODO: 로그인 응답에 isCarrier 포함)
const ROLE_OF = { ADMIN: 'ADMIN', CONTROL: 'CONTROL', HUB_OP: 'HUB_OP', CENTER_OP: 'CENTER_OP', CORPORATE_APPROVED: 'CARRIER' }
const loginRole = ROLE_OF[authState.user?.userType]
const fromLogin = !!loginRole
if (loginRole) state.role = loginRole

const tick = ref(0), msg = ref(''), left = ref(5), role = ref(state.role)
let th; toastBus.fn = (m) => { msg.value = m; clearTimeout(th); th = setTimeout(() => (msg.value = ''), 2200) }
const menu = computed(() => MENU.map(g => ({ group: g.group, items: g.items.filter(it => it[3][role.value]) })).filter(g => g.items.length))
const html = computed(() => { tick.value; return permOf(state.route, state.role) ? SCREENS[state.route]() : '<div class="denied"><b>403</b>이 권한으로는 볼 수 없는 화면입니다.</div>' })
watch(role, (r) => { state.role = r; if (r === 'CENTER_OP') state.center = 'SEOUL'; go(FIRST[r]) }) // TODO: 소속 센터 = accounts.site_id
function go(r) { state.route = r; tick.value++ }
function onClick(e) {
  const b = e.target.closest('[data-center],[data-filter],[data-pass],[data-act],a[href^="#"]')
  if (!b) return
  if (b.dataset.center) { e.preventDefault(); if (state.role === 'CENTER_OP' && b.tagName !== 'A') return; // CENTER_OP 는 소속 센터만
    state.center = b.dataset.center; if (b.tagName === 'A') state.route = 'center'; tick.value++; return }
  if (b.tagName === 'A') { e.preventDefault(); const r = b.getAttribute('href').slice(1); if (SCREENS[r]) go(r); return }
  if (b.dataset.filter) { state.exFilter = b.dataset.filter; tick.value++; return }
  if (b.dataset.pass !== undefined) { state.passIdx = +b.dataset.pass; tick.value++; return }
  if (b.dataset.act === 'advance') {
    const ex = MOCK.exceptions.find(x => x.id === +b.dataset.ex)
    let note = null
    if (ex && ex.status === 'IN_PROGRESS') { note = window.prompt('처리 내용을 입력하세요 (필수)'); if (!note || !note.trim()) return }
    advanceException(ex.id, note).then(ok => { if (ok) { act('advance', b); tick.value++ } })
    return
  }
  if (b.dataset.act) { act(b.dataset.act, b); tick.value++ }
}
function onInput(e) { if (e.target.id === 'km') { state.km = Math.max(0, +e.target.value || 0); const out = e.target.closest('.panel').querySelector('b.mono[style*="18px"]'); tick.value++; setTimeout(() => { const k = document.getElementById('km'); k && k.focus() }) } }
let timer
onMounted(() => { state.route = FIRST[state.role]; refresh().then(() => tick.value++); timer = setInterval(async () => { if (--left.value <= 0) { left.value = 5;  /* 26.10.20: 앱 씰 입력이 바로 보이도록 30초 → 5초 */ await refresh(); if (document.activeElement?.id !== 'km') tick.value++ } }, 1000) })
onUnmounted(() => clearInterval(timer))
</script>
