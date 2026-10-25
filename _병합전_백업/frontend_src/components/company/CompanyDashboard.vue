<template>
  <div class="ops-dash biz-ops">
    <div v-if="loadError" class="crud-form-error" style="margin-bottom: 16px;">
      일부 데이터를 불러오지 못했습니다. {{ loadError }}
    </div>

    <div class="ops-kpis biz-kpis">
      <RouterLink v-for="k in kpis" :key="k.label" :to="k.to" class="ops-kpi" :class="k.tone">
        <span class="ops-kpi-icon biz-kpi-icon"><img :src="k.img" :alt="k.label" /></span>
        <span class="ops-kpi-label">{{ k.label }}</span>
        <span class="ops-kpi-value">{{ k.value ?? '-' }}<small>{{ k.unit }}</small></span>
        <span class="ops-kpi-hint" :class="{ up: k.up }">{{ k.hint }}</span>
      </RouterLink>
    </div>

    <div class="ops-mid">
      <section class="ops-panel">
        <div class="ops-panel-head">
          <h2><img :src="dashIcons.section.운송흐름현황" alt="" /> 운송 상태 현황</h2>
        </div>
        <DonutChart
          :items="shipStatusChart"
          unit="건"
          center-label="총 운송 건수"
          show-percent
          :size="156"
        />
      </section>

      <section class="ops-panel">
        <div class="ops-panel-head">
          <h2><img :src="dashIcons.section.차량운행현황" alt="" /> 차량 상태 현황</h2>
          <RouterLink to="/company/trucks/new">상세 보기 &gt;</RouterLink>
        </div>
        <DonutChart
          :items="vehicleStatusChart"
          unit="대"
          center-label="전체 차량"
          show-percent
          :size="156"
        />
      </section>

      <section class="ops-panel biz-notice-panel">
        <div class="ops-panel-head">
          <h2><img :src="dashIcons.section.최근처리현황" alt="" /> 주요 알림 / 공지사항</h2>
          <RouterLink to="/notice">전체보기</RouterLink>
        </div>
        <ul v-if="notices.length" class="biz-notice-list">
          <li v-for="n in notices" :key="n.postId">
            <RouterLink :to="`/notice/${n.postId}`">
              <span class="badge-type" :class="noticeTypeClass(n)">{{ noticeTypeLabel(n) }}</span>
              <span class="biz-notice-title">{{ n.title }}</span>
              <time>{{ fmtDate(n.createdAt) }}</time>
            </RouterLink>
          </li>
        </ul>
        <p v-else class="dash-empty">등록된 공지가 없습니다.</p>
        <RouterLink to="/company/mapping" class="biz-dispatch-cta">
          <i class="bi bi-megaphone-fill"></i> 배차 요청하기
          <i class="bi bi-chevron-right"></i>
        </RouterLink>
      </section>
    </div>

    <div class="ops-bottom">
      <section class="ops-panel">
        <div class="ops-panel-head">
          <h2><img :src="dashIcons.section.운행차량현황" alt="" /> 최근 운송 실적 (7일간)</h2>
          <span class="biz-chart-legend">
            <i class="ship"></i> 운송 건수
            <i class="delay"></i> 지연 건수
          </span>
        </div>
        <div class="ops-hour-chart biz-week-chart">
          <div class="ops-hour-y">
            <span v-for="t in weekTicks" :key="t">{{ t }}</span>
          </div>
          <div class="ops-hour biz-week-hour">
            <div v-for="d in weekSeries" :key="d.key" class="ops-hour-col" :class="{ peak: d.peak }">
              <span v-if="d.peak" class="ops-hour-peak">{{ d.ship }}건</span>
              <div class="biz-week-bars">
                <span class="biz-week-bar ship" :style="{ height: weekHeight(d.ship) }"></span>
                <span class="biz-week-bar delay" :style="{ height: weekHeight(d.delay) }"></span>
              </div>
              <small>{{ d.label }}</small>
            </div>
          </div>
        </div>
      </section>

      <section class="ops-panel">
        <div class="ops-panel-head">
          <h2><img :src="dashIcons.section.안전관리현황" alt="" /> 주요 운송 지표</h2>
        </div>
        <ul class="biz-metrics">
          <li v-for="m in metrics" :key="m.label">
            <span><i class="bi" :class="m.icon"></i> {{ m.label }}</span>
            <b>{{ m.value }}<small>{{ m.unit }}</small></b>
          </li>
        </ul>
      </section>
    </div>

    <section class="ops-panel biz-recent">
      <div class="ops-panel-head">
        <h2><img :src="dashIcons.section.최근처리현황" alt="" /> 최근 운송 현황</h2>
        <RouterLink to="/company/mapping">전체보기</RouterLink>
      </div>
      <table v-if="recentRows.length" class="ops-table biz-table">
        <thead>
          <tr>
            <th>운송번호</th>
            <th>차량번호</th>
            <th>기사명</th>
            <th>차종</th>
            <th>상태</th>
            <th>진입 허가</th>
            <th>진행상황</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in recentRows" :key="r.id">
            <td>{{ r.id }}</td>
            <td>{{ r.vehicleNo }}</td>
            <td>{{ r.driver }}</td>
            <td>{{ r.truckType }}</td>
            <td><span class="pill" :class="r.tone">{{ r.status }}</span></td>
            <td>{{ r.approval }}</td>
            <td>
              <div class="biz-progress">
                <div class="biz-progress-track"><span :style="{ width: r.progress + '%' }"></span></div>
                <em>{{ r.progress }}%</em>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="dash-empty">표시할 운송 현황이 없습니다. 차량을 등록하면 여기에 나타납니다.</p>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import DonutChart from '@/components/common/DonutChart.vue'
import { fetchCompanyDrivers, fetchCompanyTrucks } from '@/utils/companyDrivers.js'
import { postsApi, NOTICE_CATEGORY, noticeTypeLabel, noticeTypeClass, fmtDate } from '@/utils/postsApi.js'
import { normalizeRows } from '@/utils/apiHelpers.js'
import { dashIcons } from '@/components/admin/dashIcons.js'
import { viewDate, toDateInput } from '@/components/admin/adminViewDate.js'

const C = {
  orange: '#f97316',
  blue: '#3b82f6',
  green: '#22c55e',
  purple: '#8b5cf6',
  red: '#ef4444',
  navy: '#0a2540',
  slate: '#94a3b8',
}

const loading = ref(false)
const loadError = ref('')
const trucks = ref([])
const drivers = ref([])
const notices = ref([])

async function load() {
  loading.value = true
  loadError.value = ''
  const errors = []
  const [t, d, n] = await Promise.allSettled([
    fetchCompanyTrucks(),
    fetchCompanyDrivers(),
    postsApi.get(`/category/${NOTICE_CATEGORY}`, { params: { page: 0, size: 8 } }).then((r) => r.data),
  ])
  if (t.status === 'fulfilled') trucks.value = t.value || []
  else errors.push(t.reason?.message || '차량 목록을 불러오지 못했습니다.')
  if (d.status === 'fulfilled') drivers.value = d.value || []
  else errors.push(d.reason?.message || '기사 목록을 불러오지 못했습니다.')
  if (n.status === 'fulfilled') notices.value = normalizeRows(n.value).slice(0, 5)
  loadError.value = errors.join(' ')
  loading.value = false
}

function statusKey(t) {
  const s = String(t.status || '').toUpperCase()
  const ap = String(t.entryApproval || '').toUpperCase()
  if (ap === 'REJECTED') return 'blocked'
  if (s === 'OUTSIDE') return 'done'
  if (s === 'IN_TRANSIT' || s === 'INSIDE') return 'running'
  if (t.assignedAccountId || t.assignedDriverName) return 'running'
  if (ap === 'PENDING' || !ap) return 'wait'
  return 'wait'
}

const running = computed(() => trucks.value.filter((t) => statusKey(t) === 'running'))
const waiting = computed(() => trucks.value.filter((t) => statusKey(t) === 'wait'))
const done = computed(() => trucks.value.filter((t) => statusKey(t) === 'done'))
const blocked = computed(() => trucks.value.filter((t) => statusKey(t) === 'blocked'))
const assigned = computed(() => trucks.value.filter((t) => t.assignedAccountId || t.assignedDriverName))
const unassigned = computed(() => trucks.value.filter((t) => !(t.assignedAccountId || t.assignedDriverName)))
const pendingEntry = computed(() =>
  trucks.value.filter((t) => !t.entryApproval || String(t.entryApproval).toUpperCase() === 'PENDING')
)

const kpis = computed(() => [
  {
    label: '전체 운송 건수',
    value: trucks.value.length,
    unit: '건',
    hint: `소속 차량 ${trucks.value.length}대 기준`,
    img: dashIcons.top.컨테이너,
    tone: 'blue',
    to: '/company/mapping',
  },
  {
    label: '운행 중 차량',
    value: running.value.length,
    unit: '대',
    hint: `전체 차량 ${trucks.value.length}대 중`,
    img: dashIcons.top.운행중,
    tone: 'orange',
    to: '/company/info',
  },
  {
    label: '이상 상황',
    value: blocked.value.length + pendingEntry.value.length,
    unit: '건',
    hint: '진입 허가 대기 · 운행 불가',
    img: dashIcons.top.과적위반,
    tone: 'red',
    to: '/company/info',
  },
  {
    label: '출발 예정 건수',
    value: waiting.value.length,
    unit: '건',
    hint: isViewDayHint(),
    img: dashIcons.top.야드대기,
    tone: 'teal',
    to: '/company/mapping',
  },
])

function isViewDayHint() {
  const d = viewDate.value
  const week = ['일', '월', '화', '수', '목', '금', '토'][d.getDay()]
  return `${d.getMonth() + 1}/${d.getDate()}(${week}) 배차 대기`
}

const shipStatusChart = computed(() => [
  { label: '운행 중', value: running.value.length, color: C.navy },
  { label: '배차 대기', value: waiting.value.length, color: C.orange },
  { label: '운행 완료', value: done.value.length, color: C.green },
  { label: '지연 / 기타', value: blocked.value.length, color: C.slate },
])

const vehicleStatusChart = computed(() => [
  { label: '운행 중', value: running.value.length, color: C.navy },
  { label: '정비 중', value: trucks.value.filter((t) => String(t.status || '').toUpperCase() === 'MAINTENANCE').length, color: C.orange },
  { label: '대기 중', value: waiting.value.length, color: C.blue },
  { label: '운행 불가', value: blocked.value.length, color: C.red },
])

const weekSeries = computed(() => {
  const days = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date(viewDate.value)
    d.setDate(d.getDate() - i)
    d.setHours(0, 0, 0, 0)
    days.push(d)
  }
  const weekday = ['일', '월', '화', '수', '목', '금', '토']
  const rows = days.map((d) => {
    const key = toDateInput(d)
    const selected = key === toDateInput(viewDate.value)
    const dated = trucks.value.filter((t) => t.createdAt && toDateInput(new Date(t.createdAt)) === key)
    const ship = dated.length ? dated.length : (selected ? trucks.value.length : 0)
    const delay = dated.length
      ? dated.filter((t) => !(t.assignedAccountId || t.assignedDriverName)).length
      : (selected ? unassigned.value.length : 0)
    return {
      key,
      label: `${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')} (${weekday[d.getDay()]})`,
      ship,
      delay,
      peak: false,
    }
  })
  let best = rows[0]
  rows.forEach((r) => { if (r.ship > (best?.ship || 0)) best = r })
  if (best && best.ship) best.peak = true
  return rows
})

const weekMax = computed(() => Math.max(4, ...weekSeries.value.map((d) => Math.max(d.ship, d.delay))))
const weekTicks = computed(() => {
  const max = weekMax.value
  return [max, Math.round(max * 0.75), Math.round(max * 0.5), Math.round(max * 0.25), 0]
})
function weekHeight(v) {
  return `${Math.max(4, Math.round((v / weekMax.value) * 92))}px`
}

const avgWeightTon = computed(() => {
  const weights = trucks.value.map((t) => Number(t.maxLoadWeight)).filter((n) => Number.isFinite(n) && n > 0)
  if (!weights.length) return null
  const kg = weights.reduce((s, n) => s + n, 0) / weights.length
  return Math.round((kg / 1000) * 10) / 10
})

const metrics = computed(() => [
  { label: '배정된 차량', icon: 'bi-signpost-2', value: assigned.value.length, unit: '대' },
  { label: '소속 기사', icon: 'bi-clock', value: drivers.value.length, unit: '명' },
  { label: '평균 적재 중량', icon: 'bi-box-seam', value: avgWeightTon.value ?? '-', unit: avgWeightTon.value == null ? '' : ' t' },
  { label: '미배정 차량', icon: 'bi-exclamation-triangle', value: unassigned.value.length, unit: '대' },
])

function approvalLabel(v) {
  const s = String(v || '').toUpperCase()
  if (s === 'APPROVED') return '허가'
  if (s === 'REJECTED') return '반려'
  if (s === 'PENDING') return '심사대기'
  return v ? v : '미신청'
}

function progressOf(t) {
  const k = statusKey(t)
  if (k === 'done') return 100
  if (k === 'running') return 68
  if (k === 'blocked') return 12
  return 22
}

function statusLabel(t) {
  const k = statusKey(t)
  if (k === 'running') return { text: '운행 중', tone: 'pill-warn' }
  if (k === 'done') return { text: '운행 완료', tone: 'pill-muted' }
  if (k === 'blocked') return { text: '운행 불가', tone: 'pill-off' }
  return { text: '배차 대기', tone: 'pill-on' }
}

const recentRows = computed(() =>
  trucks.value.slice(0, 8).map((t, i) => {
    const st = statusLabel(t)
    return {
      id: `SC${String(t.vehicleNo || i).replace(/\s/g, '').slice(-8).padStart(8, '0')}`,
      vehicleNo: t.vehicleNo || '-',
      driver: t.assignedDriverName || drivers.value.find((d) => d.accountId === t.assignedAccountId)?.userName || '-',
      truckType: t.truckType || '-',
      status: st.text,
      tone: st.tone,
      approval: approvalLabel(t.entryApproval),
      progress: progressOf(t),
    }
  })
)

onMounted(load)
</script>
