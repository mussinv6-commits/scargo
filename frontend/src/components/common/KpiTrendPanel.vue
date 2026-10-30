<template>
  <!-- 26.10.24 KPI 일별 추이: 하역 · 운송 · 인계 평균 시간(분)과 입고/점검 건수를 막대로 보여 주고 표로도 내려받는다 -->
  <div class="kp" :class="{ 'is-dark': dark }">
    <div class="kp-bar">
      <!-- 26.10.28 기간을 달력으로 (시작일 ~ 종료일) -->
      <label>기간
        <span class="kp-range">
          <input v-model="from" type="date" class="kp-in" :max="to" />
          <span>~</span>
          <input v-model="to" type="date" class="kp-in" :min="from" :max="today" />
        </span>
      </label>
      <button class="kp-btn" :disabled="loading" @click="load">조회</button>
      <button class="kp-btn" :disabled="!rows.length" @click="exportRows">Excel 내보내기</button>
      <span v-if="message" class="kp-msg">{{ message }}</span>
    </div>

    <!-- 26.10.28 QA#5 KPI 지표 5개 + 하단 목록을 탭으로 전환 -->
    <nav class="kp-tabs">
      <button v-for="m in METRICS" :key="m.key" type="button" :class="{ on: view === m.key }" @click="view = m.key">{{ m.short }}</button>
      <button type="button" :class="{ on: view === 'list' }" @click="view = 'list'">목록</button>
    </nav>

    <section v-for="m in METRICS.filter((x) => x.key === view)" :key="m.key" class="kp-card">
      <h3>{{ m.label }} <small>{{ m.unit }}</small> <span v-if="summary(m.key)" class="kp-sum">{{ summary(m.key) }}</span></h3>
      <div v-if="rows.length" class="kp-chart">
        <div v-for="r in rows" :key="r.day" class="kp-col" :title="`${r.day}: ${show(r[m.key])}${m.unit}`">
          <div class="kp-val">{{ show(r[m.key]) }}</div>
          <div class="kp-barfill" :class="m.cls" :style="{ height: height(m.key, r[m.key]) }"></div>
          <div class="kp-day" :class="{ today: isToday(r.day), wkend: isWeekend(r.day) }">{{ md(r.day) }}<br>{{ wd(r.day) }}</div>
        </div>
      </div>
      <p v-else-if="!loading" class="kp-muted">데이터가 없습니다.</p>
    </section>

    <!-- 하단 목록: 별도 탭, 표 10건 페이징 -->
    <section v-if="view === 'list'" class="kp-card">
      <table v-if="rows.length" class="kp-table">
        <thead><tr><th class="no">번호</th><th>날짜</th><th>입고</th><th>평균 하역(분)</th><th>평균 운송(분)</th><th>평균 인계 대기(분)</th><th>점검</th><th>불합격</th></tr></thead>
        <tbody>
          <tr v-for="(r, i) in pg.rows" :key="r.day">
            <td class="no">{{ pg.no(i) }}</td>
            <td>{{ r.day }} ({{ wd(r.day) }})</td><td>{{ r.arrivals }}</td><td>{{ show(r.avgUnloadMin) }}</td><td>{{ show(r.avgTransitMin) }}</td>
            <td>{{ show(r.avgHandoverMin) }}</td><td>{{ r.inspections }}</td><td :class="{ bad: r.failed > 0 }">{{ r.failed }}</td>
          </tr>
        </tbody>
      </table>
      <AdminPager v-model="pg.page" :page-count="pg.pageCount" />
      <p v-if="!rows.length && !loading" class="kp-muted">데이터가 없습니다.</p>
    </section>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { kpiTrend } from '@/utils/opsApi.js'
import { exportExcel } from '@/utils/exportExcel.js'
import { friendlyError } from '@/utils/apiHelpers.js'
import AdminPager from '@/components/admin/AdminPager.vue'
import { usePaged } from '@/utils/usePaged.js'

defineProps({ dark: { type: Boolean, default: false } })

const METRICS = [
  { key: 'arrivals', short: '허브 입고 건수', label: '허브 입고 건수', unit: '건', cls: 'c1' },
  { key: 'avgUnloadMin', short: '평균 하역 시간', label: '평균 하역 시간 (도착 → 하역 완료)', unit: '분', cls: 'c2' },
  { key: 'avgTransitMin', short: '평균 운송 시간', label: '평균 운송 시간 (출발 → 도착)', unit: '분', cls: 'c3' },
  { key: 'avgHandoverMin', short: '평균 인계 대기', label: '평균 인계 대기 (도착 → 인계)', unit: '분', cls: 'c4' },
  { key: 'failed', short: '점검 불합격 건수', label: '점검 불합격 건수', unit: '건', cls: 'c5' },
]

// 26.10.28 기간 달력: 서버는 '오늘부터 며칠 전까지'만 받으므로 시작일까지 받아 온 뒤 종료일 이후는 화면에서 잘라 낸다
const ymd = (d) => new Date(d.getTime() + 9 * 3600 * 1000).toISOString().slice(0, 10) // 한국 날짜
const today = ymd(new Date())
const from = ref(ymd(new Date(Date.now() - 13 * 86400000)))
const to = ref(today)
const dayDiff = (a, b) => Math.round((new Date(b + 'T00:00:00') - new Date(a + 'T00:00:00')) / 86400000)
const rows = ref([])
const view = ref('arrivals') // 지금 보고 있는 탭 (지표 key 또는 'list')
// 목록 탭: 최근 날짜부터 10건씩
const pg = usePaged(() => [...rows.value].reverse(), 10)
const loading = ref(false)
const message = ref('')

const num = (v) => (v === null || v === undefined || v === '' ? null : Number(v))
const WD = ['일', '월', '화', '수', '목', '금', '토']
const dateOf = (d) => new Date(String(d).slice(0, 10) + 'T00:00:00')
const md = (d) => { const x = dateOf(d); return (x.getMonth() + 1) + '/' + x.getDate() }
const wd = (d) => WD[dateOf(d).getDay()]
const isWeekend = (d) => [0, 6].includes(dateOf(d).getDay())
const isToday = (d) => { const t = new Date(); return dateOf(d).toDateString() === t.toDateString() }
// 지표별 한 줄 요약: 기간 평균 · 최고 · 오늘(마지막 날)
function summary(key) {
  const vals = rows.value.map((r) => num(r[key])).filter((v) => v !== null)
  if (!vals.length) return ''
  const avg = vals.reduce((a, b) => a + b, 0) / vals.length
  const last = num(rows.value[rows.value.length - 1][key])
  const lastLabel = to.value === today ? '오늘' : '마지막 날'
  const f = (v) => (Math.round(v * 10) / 10)
  return `· 평균 ${f(avg)} · 최고 ${f(Math.max(...vals))} · ${lastLabel} ${last === null ? '-' : f(last)}`
}
const show = (v) => (num(v) === null ? '-' : String(num(v)))
function height(key, v) {
  const n = num(v)
  if (n === null || n <= 0) return '2px'
  const max = Math.max(...rows.value.map((r) => num(r[key]) || 0), 1)
  return Math.max(4, Math.round((n / max) * 90)) + 'px'
}

async function load() {
  loading.value = true
  message.value = ''
  try {
    if (!from.value || !to.value || from.value > to.value) { rows.value = []; message.value = '시작일이 종료일보다 늦습니다.'; return }
    const list = await kpiTrend(dayDiff(from.value, today) + 1)
    rows.value = (Array.isArray(list) ? list : []).filter((r) => { const d = String(r.day).slice(0, 10); return d >= from.value && d <= to.value })
  } catch (err) {
    rows.value = []
    message.value = friendlyError(err, 'KPI를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

function exportRows() {
  exportExcel('KPI추이', [
    { key: 'day', label: '날짜' }, { key: 'arrivals', label: '입고 건수' },
    { key: 'avgUnloadMin', label: '평균 하역(분)' }, { key: 'avgTransitMin', label: '평균 운송(분)' },
    { key: 'avgHandoverMin', label: '평균 인계 대기(분)' }, { key: 'inspections', label: '점검 확정' }, { key: 'failed', label: '불합격' },
  ], rows.value)
}

onMounted(load)
</script>

<style scoped>
.kp { display: flex; flex-direction: column; gap: 12px; }
.kp-bar { display: flex; gap: 10px; align-items: flex-end; flex-wrap: wrap; font-size: 13px; }
.kp-bar label { display: flex; flex-direction: column; gap: 4px; }
.kp-in, .kp-btn { border: 1px solid #c9d3e0; background: #fff; color: inherit; border-radius: 6px; padding: 6px 10px; font-size: 13px; }
.kp-btn { cursor: pointer; } .kp-btn:disabled { opacity: .5; cursor: default; }
.kp-msg { color: #c0392b; }
.kp-range { display: inline-flex; align-items: center; gap: 6px; } /* 26.10.28 */
.kp-card { background: #fff; border: 1px solid #d5dbe5; border-radius: 10px; padding: 10px 14px; overflow-x: auto; }
.kp-card h3 { margin: 0 0 6px; font-size: 14px; } .kp-card h3 small { color: #6b7a90; font-weight: 400; }
.kp-chart { display: flex; align-items: flex-end; gap: 6px; min-height: 130px; }
.kp-col { display: flex; flex-direction: column; align-items: center; justify-content: flex-end; min-width: 34px; flex: 1; }
.kp-val { font-size: 11px; color: #6b7a90; font-variant-numeric: tabular-nums; }
.kp-barfill { width: 70%; border-radius: 3px 3px 0 0; background: #5b8def; margin: 2px 0; }
.kp-barfill.c2 { background: #ff9f1c; } .kp-barfill.c3 { background: #2fb57a; } .kp-barfill.c4 { background: #9b6bd6; } .kp-barfill.c5 { background: #e5484d; }
.kp-day { font-size: 11px; color: #6b7a90; text-align: center; line-height: 1.25; }
.kp-day.wkend { color: #d9534f; } .kp-day.today { font-weight: 700; color: #1d6fe0; text-decoration: underline; }
.kp-sum { font-size: 12px; color: #6b7a90; font-weight: 400; margin-left: 6px; }
.kp-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.kp-table th, .kp-table td { padding: 6px 8px; border-bottom: 1px solid #e8ecf2; text-align: right; white-space: nowrap; }
.kp-table th:nth-child(2), .kp-table td:nth-child(2) { text-align: left; } .kp-table .no { text-align: center; width: 52px; color: #6b7a90; }
.kp-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
/* 26.10.28 탭 모양 통일: 지역센터 인수 현황 탭(.crp-tabs)과 같은 모양 (회색 버튼 + 선택 탭은 파란 채움) */
.kp-tabs button { border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 8px; padding: 7px 14px; font-size: 13.5px; font-weight: 600; cursor: pointer; }
.kp-tabs button:hover:not(.on) { border-color: #1d4ed8; color: #1d4ed8; }
.kp-tabs button.on { background: #1d4ed8; border-color: #1d4ed8; color: #fff; font-weight: 700; }
.is-dark .kp-tabs button { background: #0b1d36; border-color: #2a4d76; color: #e6eff7; } .is-dark .kp-tabs button.on { background: #ff9f1c; color: #0b1d36; } .kp-table th { color: #6b7a90; font-weight: 600; }
.bad { color: #e5484d; font-weight: 700; } .kp-muted { color: #6b7a90; font-size: 13px; }
.is-dark .kp-card { background: #0f2a4a; border-color: #1d3b60; color: #e6eff7; }
.is-dark .kp-in, .is-dark .kp-btn { background: #0b1d36; border-color: #2a4d76; color: #e6eff7; }
.is-dark .kp-table th, .is-dark .kp-table td { border-bottom-color: #1d3b60; }
.is-dark .kp-day.today { color: #5aa8ff; } .is-dark .kp-day.wkend { color: #ff8a80; }
.is-dark .kp-val, .is-dark .kp-day, .is-dark .kp-sum, .is-dark .kp-table th, .is-dark .kp-card h3 small, .is-dark .kp-muted { color: #9fb0c6; }
</style>
