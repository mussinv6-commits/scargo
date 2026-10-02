<template>
  <div class="ops-dash">
    <div v-if="loadError" class="crud-form-error" style="margin-bottom: 16px;">
      일부 데이터를 불러오지 못했습니다. {{ loadError }}
    </div>

    <div class="ops-kpis">
      <RouterLink v-for="k in kpis" :key="k.label" :to="k.to" class="ops-kpi" :class="k.tone">
        <span class="ops-kpi-icon"><img :src="k.img" :alt="k.label" /></span>
        <span class="ops-kpi-label">{{ k.label }}</span>
        <span class="ops-kpi-value">{{ k.value ?? '-' }}<small>{{ k.unit }}</small></span>
        <span class="ops-kpi-hint">{{ k.hint }}</span>
        <i class="bi bi-chevron-right ops-kpi-go"></i>
      </RouterLink>
    </div>

    <section class="ops-panel ops-shortcuts">
      <span class="ops-shortcuts-label">바로가기</span>
      <RouterLink v-for="s in quickLinks" :key="s.to" :to="s.to" class="ops-chip">
        <img :src="s.img" :alt="s.label" /> {{ s.label }}
      </RouterLink>
    </section>

    <div class="ops-mid">
      <section class="ops-panel">
        <div class="ops-panel-head">
          <h2><img :src="dashIcons.section.차량운행현황" alt="" /> 차량 운행 현황</h2>
          <RouterLink to="/admin/trucks">상세 보기 &gt;</RouterLink>
        </div>
        <DonutChart
          :items="truckStatusChart"
          unit="대"
          center-label="총 차량"
          show-percent
          :size="156"
        />
      </section>

      <section class="ops-panel">
        <div class="ops-panel-head">
          <h2><img :src="dashIcons.section.안전관리현황" alt="" /> 검사소 검사</h2>
          <RouterLink to="/admin/overload-checks">상세 보기 &gt;</RouterLink>
        </div>
        <div class="ops-safety">
          <div>
            <p class="ops-safety-kicker">과적검사</p>
            <p class="ops-safety-total">{{ todayOverloads.length }}<small>건</small></p>
            <ul class="ops-dot-list">
              <li v-for="row in overloadRows" :key="row.label">
                <i :style="{ background: row.color }"></i>
                {{ row.label }}
                <b>{{ row.value }}건</b>
              </li>
            </ul>
          </div>
          <div>
            <p class="ops-safety-kicker">검문소 운영 상태</p>
            <ul v-if="gateStatus.length" class="ops-dot-list">
              <li v-for="g in gateStatus" :key="g.name">
                <i :style="{ background: g.color }"></i>
                {{ g.name }}
                <b>{{ g.label }}</b>
              </li>
            </ul>
            <p v-else class="dash-empty">검문소 기록이 없습니다.</p>
          </div>
        </div>
      </section>

      <section class="ops-panel">
        <div class="ops-panel-head">
          <h2><img :src="dashIcons.section.운행차량현황" alt="" /> 운행 차량 현황 (실시간)</h2>
          <RouterLink to="/admin/trucks">전체보기</RouterLink>
        </div>
        <ul class="ops-bars">
          <li v-for="row in truckStatusChart" :key="row.label">
            <span class="ops-bars-label">
              <img :src="row.img" :alt="row.label" />
              <span>
                {{ row.label }}
                <b>{{ row.value }}대</b>
              </span>
            </span>
            <div class="ops-bars-track"><span :style="{ width: barPct(row.value) + '%', background: row.color }"></span></div>
            <em>{{ barPct(row.value) }}%</em>
          </li>
        </ul>
      </section>
    </div>

    <div class="ops-bottom">
      <section class="ops-panel">
        <div class="ops-panel-head">
          <h2><img :src="dashIcons.section.최근처리현황" alt="" /> 최근 처리 현황</h2>
          <RouterLink to="/admin/accounts">전체 보기</RouterLink>
        </div>
        <table v-if="recentEvents.length" class="ops-table">
          <thead>
            <tr>
              <th>시간</th>
              <th>구분</th>
              <th>내용</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(e, i) in recentEvents" :key="i">
              <td>{{ e.time }}</td>
              <td><span class="ops-kind"><img :src="e.img" :alt="e.kind" /> {{ e.kind }}</span></td>
              <td>{{ e.text }}</td>
              <td><span class="pill" :class="e.tone">{{ e.status }}</span></td>
            </tr>
          </tbody>
        </table>
        <p v-else class="dash-empty">최근 처리 내역이 없습니다.</p>
      </section>

      <section class="ops-panel">
        <div class="ops-panel-head">
          <h2><img :src="dashIcons.section.운송흐름현황" alt="" /> 운송 흐름 현황</h2>
          <span class="ops-muted">{{ isViewToday ? '오늘 기준' : '선택일 기준' }}</span>
        </div>
        <div class="ops-flow">
          <template v-for="(step, i) in flowSteps" :key="step.label">
            <i v-if="i" class="bi bi-chevron-right ops-flow-arrow"></i>
            <div class="ops-flow-step" :class="step.tone">
              <img :src="step.img" :alt="step.label" />
              <strong>{{ step.label }}</strong>
              <b>{{ step.value }}건</b>
            </div>
          </template>
        </div>
        <div class="ops-flow-legend"><i></i> 운행 중 차량</div>
        <p class="ops-safety-kicker" style="margin-top:14px;">시간대별 운행 차량 수</p>
        <div class="ops-hour-chart">
          <div class="ops-hour-y">
            <span v-for="t in hourTicks" :key="t">{{ t }}</span>
          </div>
          <div class="ops-hour">
            <div
              v-for="h in hourly"
              :key="h.hour"
              class="ops-hour-col"
              :class="{ peak: h.hour === peakHour }"
            >
              <span v-if="h.hour === peakHour && h.value" class="ops-hour-peak">{{ padHour(h.hour) }}시 {{ h.value }}대</span>
              <span class="ops-hour-bar" :style="{ height: hourHeight(h.value) }"></span>
              <small>{{ padHour(h.hour) }}시</small>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import DonutChart from '@/components/common/DonutChart.vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'
import { normalizeRows } from '@/utils/apiHelpers'
import { dashIcons } from '@/components/admin/dashIcons.js'
import { viewDate, isViewToday } from '@/components/admin/adminViewDate.js'

const C = {
  orange: '#f97316',
  blue: '#3b82f6',
  green: '#22c55e',
  purple: '#8b5cf6',
  teal: '#14b8a6',
  red: '#ef4444',
  navy: '#0a2540',
  slate: '#94a3b8',
}

const loading = ref(false)
const loadError = ref('')

const accounts = ref([])
const trucks = ref([])
const containers = ref([])
const yards = ref([])
const overloads = ref([])
const gateLogs = ref([])
const loadingRecords = ref([])
const gates = ref([])

function isViewDay(d) {
  if (!d) return false
  const x = new Date(d)
  const t = viewDate.value
  return x.getFullYear() === t.getFullYear() && x.getMonth() === t.getMonth() && x.getDate() === t.getDate()
}

async function safe(promise, silent = false) {
  try {
    const res = await promise
    return res.data
  } catch (err) {
    if (!silent) loadError.value = pickErrorMessage(err)
    return null
  }
}

async function load() {
  loading.value = true
  loadError.value = ''
  const [a, t, ct, y, o, gl, lr, g] = await Promise.all([
    safe(adminApi.get('/api/accounts')),
    safe(adminApi.get('/api/trucks')),
    safe(adminApi.get('/api/containers')),
    safe(adminApi.get('/api/yards')),
    safe(adminApi.get('/api/overload-checks', { params: { page: 0, size: 200 } })),
    safe(adminApi.get('/api/v1/gate-logs', { params: { page: 0, size: 100 } }), true),
    safe(adminApi.get('/api/loading-records', { params: { page: 0, size: 100 } }), true),
    safe(adminApi.get('/api/gates'), true),
  ])
  accounts.value = a ? normalizeRows(a) : []
  trucks.value = t ? normalizeRows(t, { bools: ['isSemiTrailer'] }) : []
  containers.value = ct ? normalizeRows(ct, { bools: ['isHighCube'] }) : []
  yards.value = y ? normalizeRows(y, { bools: ['isAvailable'], idKey: 'yardId' }) : []
  overloads.value = o ? normalizeRows(o, { bools: ['isViolation', 'isPassed'], idKey: 'checkId' }) : []
  gateLogs.value = gl ? normalizeRows(gl, { idKey: 'gateLogId' }) : []
  loadingRecords.value = lr ? normalizeRows(lr, { idKey: 'recordId' }) : []
  gates.value = g ? normalizeRows(g, { idKey: 'gateId' }) : []
  loading.value = false
}

const pendingAccounts = computed(() => accounts.value.filter((x) => x.userType === 'CORPORATE_PENDING'))
const running = computed(() => trucks.value.filter((x) => x.status === 'IN_TRANSIT'))
const yardWait = computed(() => trucks.value.filter((x) => x.status === 'INSIDE'))
const entering = computed(() => trucks.value.filter((x) => x.status !== 'IN_TRANSIT' && x.status !== 'INSIDE' && (!x.entryApproval || x.entryApproval === 'PENDING')))
const finished = computed(() =>
  trucks.value.filter((x) => x.status === 'OUTSIDE' && x.entryApproval && x.entryApproval !== 'PENDING')
)
const todayOverloads = computed(() => overloads.value.filter((x) => isViewDay(x.checkedAt)))
const todayViolations = computed(() => todayOverloads.value.filter((x) => x.isViolation))
const todayIn = computed(() => gateLogs.value.filter((x) => (x.gateType || '').toUpperCase() === 'IN' && isViewDay(x.passAt || x.createdAt)))
const todayOut = computed(() => gateLogs.value.filter((x) => (x.gateType || '').toUpperCase() === 'OUT' && isViewDay(x.passAt || x.createdAt)))
const todayDispatch = computed(() => loadingRecords.value.filter((x) => isViewDay(x.loadedAt)))

const kpis = computed(() => [
  { label: '승인 대기', value: pendingAccounts.value.length, unit: '건', hint: '업체 / 회원 승인 대기', img: dashIcons.top.승인대기, tone: 'orange', to: '/admin/accounts' },
  { label: '운행 중', value: running.value.length, unit: '대', hint: '현재 운행 중인 차량', img: dashIcons.top.운행중, tone: 'blue', to: '/admin/trucks' },
  { label: '야드 대기', value: yardWait.value.length, unit: '대', hint: '야드에서 대기 중인 차량', img: dashIcons.top.야드대기, tone: 'green', to: '/admin/yards' },
  { label: '컨테이너', value: containers.value.length, unit: '개', hint: '등록된 컨테이너', img: dashIcons.top.컨테이너, tone: 'purple', to: '/admin/containers' },
  { label: isViewToday.value ? '오늘 검사' : '당일 검사', value: todayOverloads.value.length, unit: '건', hint: isViewToday.value ? '오늘 실시된 과적 검사' : '선택한 날짜의 과적 검사', img: dashIcons.top.오늘검사, tone: 'teal', to: '/admin/overload-checks' },
  { label: '과적 위반', value: todayViolations.value.length, unit: '건', hint: '확인이 필요한 위반 건', img: dashIcons.top.과적위반, tone: 'red', to: '/admin/overload-checks' },
])

const quickLinks = [
  { to: '/admin/accounts', label: '회원 관리', img: dashIcons.quick.회원승인 },
  { to: '/admin/companies', label: '업체 관리', img: dashIcons.quick.업체등록 },
  { to: '/admin/trucks', label: '차량 관리', img: dashIcons.quick.차량등록 },
  { to: '/admin/containers', label: '컨테이너 관리', img: dashIcons.quick.컨테이너등록 },
  { to: '/admin/yards', label: '야드 관리', img: dashIcons.quick.야드등록 },
  { to: '/admin/overload-checks', label: '과적 검사 관리', img: dashIcons.quick.과적검사 },
]

const truckStatusChart = computed(() => [
  { label: '운행 중', value: running.value.length, color: C.green, img: dashIcons.top.운행중 },
  { label: '야드 대기', value: yardWait.value.length, color: C.orange, img: dashIcons.top.야드대기 },
  { label: '입차 처리', value: entering.value.length, color: C.slate, img: dashIcons.flow.입차 },
  { label: '운행 종료', value: finished.value.length, color: C.purple, img: dashIcons.flow.출차 },
])

const truckTotal = computed(() => truckStatusChart.value.reduce((s, r) => s + r.value, 0))
function barPct(v) {
  return truckTotal.value ? Math.round((v / truckTotal.value) * 100) : 0
}

const overloadRows = computed(() => {
  const pass = todayOverloads.value.filter((x) => !x.isViolation && x.isPassed !== false).length
  const warn = todayViolations.value.length
  const recheck = Math.max(0, todayOverloads.value.length - pass - warn)
  return [
    { label: '정상 통과', value: pass, color: C.green },
    { label: '과적의심', value: warn, color: C.orange },
    { label: '재검중 필요', value: recheck, color: C.red },
  ]
})

const gateStatus = computed(() => {
  if (gates.value.length) {
    return gates.value.slice(0, 4).map((g, i) => {
      const name = g.gateName || g.name || `Gate-${i + 1}`
      const open = g.isAvailable !== false && (g.status || 'OPEN') !== 'CLOSED'
      return { name, label: open ? '운영 중' : '점검 중', color: open ? C.green : C.blue }
    })
  }
  const names = [...new Set(gateLogs.value.map((x) => x.gateName).filter(Boolean))]
  return names.slice(0, 4).map((name, i) => ({
    name,
    label: i === names.length - 1 ? '점검 중' : '운영 중',
    color: i === names.length - 1 ? C.blue : C.green,
  }))
})

const flowSteps = computed(() => [
  { label: '배차', value: todayDispatch.value.length, img: dashIcons.flow.배차, tone: 'orange' },
  { label: '입차', value: todayIn.value.length || yardWait.value.length, img: dashIcons.flow.입차, tone: 'blue' },
  { label: '운행 중', value: running.value.length, img: dashIcons.flow.운행중, tone: 'navy' },
  { label: '출차', value: todayOut.value.length || finished.value.length, img: dashIcons.flow.출차, tone: 'purple' },
])

const hourly = computed(() => {
  const hours = [0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22]
  const counts = Object.fromEntries(hours.map((h) => [h, 0]))
  const bump = (d) => {
    if (!d || !isViewDay(d)) return
    const h = new Date(d).getHours()
    const bucket = hours.reduce((best, x) => (Math.abs(x - h) < Math.abs(best - h) ? x : best), 0)
    counts[bucket] += 1
  }
  gateLogs.value.forEach((x) => bump(x.passAt || x.createdAt))
  overloads.value.forEach((x) => bump(x.checkedAt))
  loadingRecords.value.forEach((x) => bump(x.loadedAt))
  return hours.map((hour) => ({ hour, value: counts[hour] }))
})

const hourMax = computed(() => {
  const peak = Math.max(0, ...hourly.value.map((h) => h.value))
  return Math.max(10, peak)
})
const hourTicks = computed(() => {
  const max = hourMax.value
  return [...new Set([max, Math.round(max * 0.75), Math.round(max * 0.5), Math.round(max * 0.25), 0])]
})
const peakHour = computed(() => {
  let best = hourly.value[0]
  hourly.value.forEach((h) => { if (h.value > (best?.value || 0)) best = h })
  return best?.value ? best.hour : null
})
function padHour(h) {
  return String(h).padStart(2, '0')
}
function hourHeight(v) {
  if (!v) return '0px'
  return `${Math.round((v / hourMax.value) * 92)}px`
}

function fmtTime(d) {
  if (!d) return '--:--'
  return new Date(d).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
}

const recentEvents = computed(() => {
  const rows = []
  pendingAccounts.value.forEach((a) => {
    rows.push({
      at: a.createdAt || 0,
      time: fmtTime(a.createdAt),
      kind: '업체 승인',
      img: dashIcons.table.업체승인,
      text: `${a.userName || a.userId} 승인 요청`,
      status: '대기',
      tone: 'pill-warn',
    })
  })
  overloads.value.forEach((o) => {
    rows.push({
      at: o.checkedAt || 0,
      time: fmtTime(o.checkedAt),
      kind: '과적 검사',
      img: o.isViolation ? dashIcons.table.과적확인 : dashIcons.table.과적검사,
      text: `${o.vehicleNo || '-'} 과적검사 ${o.isViolation ? '위반' : '완료'}`,
      status: o.isViolation ? '확인 필요' : '정상',
      tone: o.isViolation ? 'pill-off' : 'pill-on',
    })
  })
  gateLogs.value.forEach((g) => {
    const type = (g.gateType || '').toUpperCase() === 'OUT' ? '출차' : '입차'
    rows.push({
      at: g.passAt || g.createdAt || 0,
      time: fmtTime(g.passAt || g.createdAt),
      kind: '검문소',
      img: dashIcons.table.검문소,
      text: `${g.actualVehicleNo || g.recognizedPlateNo || '-'} ${type} (${g.gateName || '-'})`,
      status: '정상',
      tone: 'pill-on',
    })
  })
  yards.value.filter((y) => y.isAvailable === false).forEach((y) => {
    rows.push({
      at: y.createdAt || 0,
      time: fmtTime(y.createdAt),
      kind: '야드',
      img: dashIcons.table.야드,
      text: `${y.yardName || '야드'} 이용 불가`,
      status: '완료',
      tone: 'pill-on',
    })
  })
  return rows
    .filter((e) => isViewDay(e.at))
    .sort((a, b) => new Date(b.at) - new Date(a.at))
    .slice(0, 6)
})

onMounted(() => {
  load()
})
</script>
