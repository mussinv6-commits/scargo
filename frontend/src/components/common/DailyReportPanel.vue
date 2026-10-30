<template>
  <!-- 26.10.24 일일 처리량 보고서: 날짜를 고르면 하루치 집계를 보여 주고 Excel(.xlsx)로 내려받는다 (읽기 전용) -->
  <div class="drp" :class="{ 'is-dark': dark }">
    <p v-if="message" class="drp-msg">{{ message }}</p>
    <!-- 26.10.28 QA#5 일일 보고서 표: 구분별로 묶어 재정렬 + 표 모양 정리 -->
    <!-- 26.10.28 빈 공간 채우기: 표 위에 구분별 합계 카드 (전체 합계 + 구분마다 항목 건수 막대) -->
    <div v-if="report && groups.length" class="drp-sum" :style="{ '--n': groups.length + 1 }">
      <div class="drp-card total">
        <span class="drp-card-h"><span class="drp-ic"><i class="bi bi-calculator"></i></span>전체 합계</span>
        <b class="drp-card-v">{{ grandTotal.toLocaleString('ko-KR') }}<small>건</small></b>
        <span class="drp-card-s">{{ report.scope || '' }} · {{ period === 'day' ? date : range.from + ' ~ ' + range.to }}</span>
      </div>
      <div v-for="(g, gi) in groups" :key="'c' + gi" class="drp-card" :style="{ '--c': COLORS[gi % COLORS.length] }">
        <span class="drp-card-h"><span class="drp-ic"><i class="bi" :class="iconOf(g.section)"></i></span>{{ gi + 1 }}. {{ g.section || '-' }}</span>
        <b class="drp-card-v">{{ g.total.toLocaleString('ko-KR') }}<small>건</small></b>
        <ul class="drp-bars">
          <li v-for="(r, i) in g.rows" :key="i">
            <span class="lb">{{ r.label }}</span>
            <span class="bar"><i :style="{ width: pct(r.count, g.max) }"></i></span>
            <span class="ct">{{ r.count }}</span>
          </li>
        </ul>
      </div>
    </div>
<!-- 26.10.28 보고 일자 · 조회 · Excel 줄은 카드 아래, 표 바로 위 -->
    <div class="drp-bar">
      <label>보고 구분
        <select v-model="period" class="drp-input" @change="load">
          <option value="day">일간</option><option value="week">주간</option><option value="month">월간</option><option value="year">연간</option>
        </select>
      </label>
      <label>{{ period === 'day' ? '보고 일자' : '기준 일자' }}
        <input v-model="date" type="date" class="drp-input" :max="today" />
      </label>
      <span v-if="period !== 'day'" class="drp-muted">{{ range.from }} ~ {{ range.to }}</span>
      <button class="drp-btn" :disabled="loading" @click="load">조회</button>
      <button class="drp-btn primary" :disabled="loading || !report" @click="download">Excel 내려받기</button>
      <span v-if="report" class="drp-muted">{{ report.scope }}</span>
    </div>
    <div v-if="report" class="drp-wrap">
      <table class="drp-table">
        <thead><tr><th class="no">번호</th><th class="sec">구분</th><th>항목</th><th class="num">건수</th></tr></thead>
        <tbody>
          <template v-for="(g, gi) in groups" :key="g.section">
            <tr v-for="(r, i) in g.rows" :key="gi + '-' + i" :class="{ first: i === 0 }">
              <!-- 26.10.28 번호는 줄마다가 아니라 구분 기준 (같은 구분은 한 번호) -->
              <td v-if="i === 0" class="no" :rowspan="g.rows.length">{{ gi + 1 }}</td>
              <td v-if="i === 0" class="sec" :rowspan="g.rows.length">{{ g.section || '-' }}</td>
              <td>{{ r.label }}</td>
              <td class="num">{{ r.count }}</td>
            </tr>
          </template>
          <tr v-if="!groups.length"><td colspan="4" class="drp-empty">집계 항목이 없습니다.</td></tr>
        </tbody>
      </table>
    </div>
    <p v-else-if="!loading" class="drp-muted">불러오는 중...</p>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { dailyReport, downloadDailyReport } from '@/utils/dailyReportApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'

defineProps({ dark: { type: Boolean, default: false } })

const today = new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10) // 한국 날짜
const date = ref(today)
const period = ref('day') // 26.10.30 보고 구분: day | week | month | year
const report = ref(null)
// 기준 일자가 속한 기간 (주간은 월~일, 오늘 이후는 잘라냄)
const range = computed(() => {
  const d = new Date(date.value + 'T00:00:00')
  const f = (x) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
  let s = new Date(d), e = new Date(d)
  if (period.value === 'week') { s = new Date(d.getFullYear(), d.getMonth(), d.getDate() - ((d.getDay() + 6) % 7)); e = new Date(s.getFullYear(), s.getMonth(), s.getDate() + 6) }
  else if (period.value === 'month') { s = new Date(d.getFullYear(), d.getMonth(), 1); e = new Date(d.getFullYear(), d.getMonth() + 1, 0) }
  else if (period.value === 'year') { s = new Date(d.getFullYear(), 0, 1); e = new Date(d.getFullYear(), 11, 31) }
  return { from: f(s), to: f(e) > today ? today : f(e) }
})
const loading = ref(false)
const message = ref('')
// 같은 구분(section)끼리 처음 나온 순서대로 묶는다 (서버 응답 순서가 섞여도 구분별로 모아 보이게)
const groups = computed(() => {
  const out = []
  const idx = {}
  for (const r of report.value?.rows || []) {
    const k = r.section || ''
    if (!(k in idx)) { idx[k] = out.length; out.push({ section: k, rows: [] }) }
    out[idx[k]].rows.push(r)
  }
  let n = 0
  for (const g of out) {
    g.start = n; n += g.rows.length
    g.total = g.rows.reduce((a, r) => a + (Number(r.count) || 0), 0)
    g.max = Math.max(1, ...g.rows.map((r) => Number(r.count) || 0))
  }
  return out
})
const grandTotal = computed(() => groups.value.reduce((a, g) => a + g.total, 0))
const pct = (v, max) => Math.round(((Number(v) || 0) / max) * 100) + '%'
const COLORS = ['#2563eb', '#0f9f9a', '#7c4dce', '#d98a00', '#dc3b3b', '#1b7a3d']
// 26.10.29 카드 제목 앞 아이콘 (구분 이름의 단어로 고름)
const ICONS = [[/게이트|입출|OCR/, 'bi-upc-scan'], [/점검|검사/, 'bi-clipboard-check'], [/예외|사고|경보/, 'bi-exclamation-triangle'], [/반납|공컨/, 'bi-arrow-repeat'], [/지선|센터|인수/, 'bi-buildings'], [/허브|도착|하역|인계/, 'bi-box-arrow-in-down'], [/운송|오더|배차|운행/, 'bi-truck'], [/과적|계량/, 'bi-speedometer2'], [/컨테이너|씰/, 'bi-box-seam']]
const iconOf = (s) => (ICONS.find(([re]) => re.test(s || '')) || [null, 'bi-bar-chart-line'])[1]

async function load() {
  loading.value = true
  message.value = ''
  try {
    report.value = await dailyReport(range.value.from, range.value.to)
  } catch (err) {
    report.value = null
    message.value = friendlyError(err, '보고서를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

async function download() {
  try {
    await downloadDailyReport(range.value.from, range.value.to, period.value)
  } catch (err) {
    message.value = friendlyError(err, 'Excel 파일을 받지 못했습니다.')
  }
}

onMounted(load)
</script>

<style scoped>
.drp { display: flex; flex-direction: column; gap: 10px; }
/* 26.10.28 보고 일자 · 달력 · 조회 · Excel 을 한 줄 높이로 맞추고 가운데 정렬 */
.drp-bar { display: flex; gap: 8px; align-items: center; justify-content: flex-start; flex-wrap: wrap; } /* 26.10.28 좌측 정렬 */
.drp-bar label { display: flex; flex-direction: row; align-items: center; gap: 8px; font-size: 13.5px; font-weight: 700; margin: 0; }
.drp-bar .drp-input, .drp-bar .drp-btn { height: 34px; box-sizing: border-box; display: inline-flex; align-items: center; }
.drp-input { border: 1px solid #c9d3e0; border-radius: 6px; padding: 6px 8px; font-size: 13px; }
.drp-btn { border: 1px solid #c9d3e0; background: #fff; border-radius: 6px; padding: 7px 14px; font-size: 13px; cursor: pointer; }
.drp-btn.primary { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; font-weight: 700; }
.drp-btn:disabled { opacity: .5; cursor: default; }
.drp-muted { color: #6b7a90; font-size: 12.5px; }
.drp-msg { margin: 0; color: #c0392b; font-size: 13px; }
.drp-wrap { background: #fff; border: 1px solid #d5dbe5; border-radius: 10px; overflow: hidden; width: 100%; margin: 0 auto; } /* 26.10.28 표를 화면 폭에 맞춰 넓힘 */
/* 26.10.28 구분별 합계 카드 */
/* 26.10.28 카드(전체 합계 + 구분별)를 한 줄에 모두 같은 폭으로 */
.drp-sum { display: grid; grid-template-columns: repeat(var(--n, 5), minmax(0, 1fr)); gap: 10px; }
.drp-card { --c: #112847; background: #fff; border: 1px solid #d5dbe5; border-top: 4px solid var(--c); border-radius: 10px; padding: 12px 14px; display: flex; flex-direction: column; gap: 4px; }
.drp-card.total { --c: #112847; background: #f7f9fc; justify-content: center; }
.drp-card-h { font-size: 13px; font-weight: 700; color: #4a5872; display: flex; align-items: center; gap: 7px; }
.drp-ic { width: 26px; height: 26px; border-radius: 8px; display: inline-flex; align-items: center; justify-content: center; flex: none; background: color-mix(in srgb, var(--c) 12%, #fff); color: var(--c); font-size: 14px; }
.drp-card-v { font-size: 26px; font-weight: 800; color: var(--c); font-variant-numeric: tabular-nums; line-height: 1.2; }
.drp-card-v small { font-size: 13px; font-weight: 600; color: #6b7a90; margin-left: 2px; }
.drp-card-s { font-size: 12px; color: #6b7a90; }
.drp-bars { list-style: none; margin: 6px 0 0; padding: 0; display: flex; flex-direction: column; gap: 5px; }
.drp-bars li { display: grid; grid-template-columns: minmax(48px, 72px) 1fr 30px; align-items: center; gap: 8px; font-size: 12.5px; }
.drp-bars .lb { color: #334155; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.drp-bars .bar { height: 8px; background: #eef1f6; border-radius: 4px; overflow: hidden; }
.drp-bars .bar i { display: block; height: 100%; background: var(--c); border-radius: 4px; }
.drp-bars .ct { text-align: right; font-weight: 700; font-variant-numeric: tabular-nums; }
.drp-table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
.drp-table th, .drp-table td { padding: 9px 12px; border-bottom: 1px solid #e3e8ef; text-align: left; vertical-align: middle; }
.drp-table thead th { background: #eef1f6; color: #4a5872; font-weight: 700; font-size: 12.5px; }
.drp-table tbody tr:last-child td { border-bottom: 0; }
.drp-table tr.first td { border-top: 1px solid #d5dbe5; }
.drp-table .no { width: 56px; text-align: center; color: #6b7a90; background: #f7f9fc; border-right: 1px solid #e3e8ef; font-weight: 700; }
.drp-table .sec { width: 140px; font-weight: 700; color: #112847; background: #f7f9fc; border-right: 1px solid #e3e8ef; }
.drp-table .num { width: 90px; text-align: right; font-variant-numeric: tabular-nums; font-weight: 600; }
.drp-empty { text-align: center !important; color: #6b7a90; }
/* 26.10.28 관리자 화면의 표 가운데 정렬이 덮어써서 항목 글자가 가운데로 가던 문제 → 칸별 정렬 고정 */
.drp .drp-table th, .drp .drp-table td { text-align: center; } /* 26.10.28 하단 표 가운데 정렬 */
.is-dark .drp-wrap { background: #0f2a4a; border-color: #1d3b60; }
.is-dark .drp-table thead th, .is-dark .drp-table .sec { background: #12304f; color: #e6eff7; }
.is-dark .drp-input, .is-dark .drp-btn { background: #0b1d36; border-color: #2a4d76; color: #e6eff7; }
.is-dark .drp-btn.primary { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; }
.is-dark .drp-table th, .is-dark .drp-table td { border-bottom-color: #1d3b60; }
.is-dark .drp-muted { color: #9fb0c6; }
.is-dark .drp-msg { color: #fca5a5; }
</style>
