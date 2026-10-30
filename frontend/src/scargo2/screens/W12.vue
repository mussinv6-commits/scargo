<template>
<section class="scr" id="W-12">
  <!-- 26.10.29 구간 관제 지도 · 차량 관제 · 계측 이력을 메뉴 1개로 합치고, 화면 안 버튼으로 전환 -->
  <div class="toll-tabs">
    <button v-for="t in TABS" :key="t.k" type="button" class="toll-tab" :class="{ on: cur === t.k }" @click="go(t.k)">{{ t.n }}</button>
  </div>
  <iframe ref="fr" class="toll-frame" :src="src" :style="{ height: fh + 'px' }" title="구간 관제" scrolling="no" />
</section>
</template>
<script setup>
// 26.10.26: 인천항 → 곤지암 구간 관제 화면 (run_toll.bat 서버, 기본 http://localhost:8002). 주소는 .env 의 VITE_TOLL_URL 로 바꾼다
// 26.10.29: 탭 버튼(관제 지도 / 차량 관제 / 계측 이력) + 공용 하루 시계 전달(택배 이동 · 물류센터 관제와 같은 시각)
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
const props = defineProps({ tab: { type: String, default: '' } }) // 'map' | 'ck' | 'gate' (W-13 · W-14 옛 주소가 넘겨 줌)
const route = useRoute()
const base = (import.meta.env.VITE_TOLL_URL || 'http://localhost:8002').replace(/\/$/, '')
const token = import.meta.env.VITE_TOLL_TOKEN || ''
const TABS = [{ k: 'map', n: '관제 지도', p: '/' }, { k: 'ck', n: '차량 관제', p: '/checkpoint' }, { k: 'gate', n: '계측 이력', p: '/gate' }]
const pathOf = (k) => (TABS.find((t) => t.k === k) || TABS[0]).p
const urlOf = (k) => `${base}${pathOf(k)}?embed=1${token ? '&token=' + encodeURIComponent(token) : ''}`
const first = props.tab || String(route.query.tab || 'map')
const cur = ref(TABS.some((t) => t.k === first) ? first : 'map')
const src = ref(urlOf(cur.value)) // 버튼을 눌렀을 때만 바꾼다 (안쪽 링크로 옮겨 간 경우 화면은 그대로 두고 버튼 표시만 따라감)
const fr = ref(null)
const fh = ref(640) // 안쪽 화면 높이에 맞춤 (잘림 없이 스크롤바는 바깥 하나만)
function go(k) { if (k === cur.value) return; cur.value = k; src.value = urlOf(k) }

// 공용 하루 시계: 택배 이동 · 물류센터 관제(sbot3d)가 localStorage 'sbot_clock' 에 {tod, rate, at} 를 적는다. 없으면 PC 시각
function clockNow() {
  try {
    const c = JSON.parse(localStorage.getItem('sbot_clock') || 'null')
    if (c && typeof c.tod === 'number' && typeof c.rate === 'number') return { tod: (((c.tod + ((Date.now() - c.at) / 1000) * c.rate) % 24) + 24) % 24, rate: c.rate }
  } catch { /* 무시 */ }
  const d = new Date()
  return { tod: d.getHours() + d.getMinutes() / 60 + d.getSeconds() / 3600, rate: 1 / 3600 }
}
let origin = '*'
try { origin = new URL(base).origin } catch { /* 주소 형식이 이상하면 전체 허용 */ }
function sendClock() { const w = fr.value && fr.value.contentWindow; if (w) { try { w.postMessage({ type: 'sbot-clock', ...clockNow() }, origin) } catch { /* 무시 */ } } }
function onMsg(e) {
  if (!fr.value || e.source !== fr.value.contentWindow) return
  const d = e.data || {}
  if (d.type === 'toll-page' && TABS.some((t) => t.k === d.page)) cur.value = d.page
  else if (d.type === 'toll-height' && typeof d.h === 'number') fh.value = Math.max(420, Math.min(2400, d.h))
  else if (d.type === 'toll-ready') sendClock()
}
let timer
onMounted(() => { window.addEventListener('message', onMsg); timer = setInterval(sendClock, 1000) })
onBeforeUnmount(() => { window.removeEventListener('message', onMsg); clearInterval(timer) })
</script>
<style scoped>
.toll-tabs { display: flex; gap: 8px; padding: 0 0 10px; }
.toll-tab { border: 0; cursor: pointer; font-family: inherit; font-size: 14px; padding: 8px 22px; border-radius: 99px; font-weight: 700; background: #eceff5; color: #475069; }
.toll-tab.on { background: #13203f; color: #fff; }
.toll-frame { width: 100%; border: 0; display: block; }
</style>
