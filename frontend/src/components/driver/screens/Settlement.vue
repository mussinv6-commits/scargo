<template>
  <div class="topbar">
    <div>
      <div class="brand">정산/매출</div>
      <div class="sub">이번 달 수익과 세금계산서 상태를 확인하세요</div>
    </div>
  </div>

  <main class="page">
    <div v-if="!vehicleNo" class="empty-state">
      <p class="hint-text">차량을 먼저 등록해야 정산 내역을 볼 수 있어요.</p>
      <RouterLink to="/driver/app/my-page" class="btn-fill link-btn">MY/차량에서 차량 등록하기</RouterLink>
    </div>

    <template v-else>
      <!-- 월 이동 -->
      <div class="month-nav">
        <button class="nav-btn" @click="shiftMonth(-1)">‹</button>
        <div class="month-label">{{ year }}년 {{ month }}월</div>
        <button class="nav-btn" @click="shiftMonth(1)" :disabled="isCurrentMonth">›</button>
      </div>

      <p v-if="loading" class="hint-text">불러오는 중...</p>

      <template v-else>
        <!-- 이번 달 수익 요약 -->
        <div class="revenue-card">
          <div class="revenue-label">이번 달 총 수익</div>
          <div class="revenue-value">{{ formatWon(summary.total) }}<span class="unit">원</span></div>
          <div class="revenue-diff" :class="summary.diffRate >= 0 ? 'up' : 'down'">
            {{ summary.diffRate >= 0 ? '▲' : '▼' }} 전월 대비 {{ Math.abs(summary.diffRate) }}%
          </div>

          <div class="revenue-sub-row">
            <div class="sub-item">
              <div class="sub-value">{{ summary.completedCount }}<span class="unit">건</span></div>
              <div class="sub-label">완료 운행</div>
            </div>
            <div class="sub-divider"></div>
            <div class="sub-item">
              <div class="sub-value">{{ formatWon(summary.avgFare) }}</div>
              <div class="sub-label">건당 평균 운임</div>
            </div>
            <div class="sub-divider"></div>
            <div class="sub-item">
              <div class="sub-value">{{ summary.totalDistance }}<span class="unit">km</span></div>
              <div class="sub-label">총 운행거리</div>
            </div>
          </div>
        </div>

        <div class="layout-2col">
          <div class="col-main">
            <!-- 세금계산서 현황 -->
            <div class="section-title">세금계산서 발행 현황</div>
            <div class="invoice-summary">
              <div class="invoice-chip"><span class="dot amber"></span> 발행대기 {{ invoiceCounts.waiting }}건</div>
              <div class="invoice-chip"><span class="dot blue"></span> 요청됨 {{ invoiceCounts.requested }}건</div>
              <div class="invoice-chip"><span class="dot green"></span> 발행완료 {{ invoiceCounts.done }}건</div>
            </div>

            <div class="card-grid">
              <div v-if="invoiceList.length === 0" class="empty">이 달에 완료된 운행이 없습니다.</div>
              <div v-for="row in invoiceList" :key="row.dispatchId" class="invoice-card">
                <div class="invoice-top">
                  <span class="invoice-date">{{ formatDate(row.dropoffTime) }} · #{{ row.dispatchId }}</span>
                  <span class="badge" :class="invoiceBadgeClass(row.invoiceStatus)">{{ invoiceLabel(row.invoiceStatus) }}</span>
                </div>
                <div class="invoice-mid">
                  <span class="invoice-shipper">{{ row.shipperName || '-' }}</span>
                  <span class="invoice-amount">{{ formatWon(row.fare) }}원</span>
                </div>
                <button v-if="row.invoiceStatus === 'WAITING'" class="btn-request" @click="requestInvoice(row)">
                  세금계산서 발행 요청
                </button>
                <div v-else-if="row.invoiceStatus === 'REQUESTED'" class="invoice-note">
                  발행 요청됨 · 영업일 기준 1~2일 소요
                </div>
                <div v-else-if="row.invoiceStatus === 'ISSUED'" class="invoice-note done">
                  {{ formatDate(row.invoiceIssuedAt) }} 발행 완료
                </div>
              </div>
            </div>
          </div>

          <!-- 주차별 매출 그래프 -->
          <aside class="side-panel">
            <template v-if="summary.weeklyRevenue?.length">
              <div class="section-title">주차별 매출</div>
              <div class="card chart-card">
                <div class="bar-row" v-for="week in summary.weeklyRevenue" :key="week.label">
                  <span class="bar-label">{{ week.label }}</span>
                  <div class="bar-track">
                    <div class="bar-fill" :style="{ width: barWidth(week.amount) + '%' }"></div>
                  </div>
                  <span class="bar-value">{{ formatWonShort(week.amount) }}</span>
                </div>
              </div>
            </template>
          </aside>
        </div>
      </template>
    </template>
  </main>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import axios from 'axios'
import { authState } from '@/auth/authState.js'
import { API_BASE } from '@/utils/apiBase.js'
import { getCompanyName } from '@/utils/companyDirectory.js'

const vehicleNo = ref(null)
const loading = ref(true)

const today = new Date()
const year = ref(today.getFullYear())
const month = ref(today.getMonth() + 1) // 1~12

const isCurrentMonth = computed(
  () => year.value === today.getFullYear() && month.value === today.getMonth() + 1
)

function shiftMonth(delta) {
  if (delta > 0 && isCurrentMonth.value) return
  let m = month.value + delta
  let y = year.value
  if (m < 1) { m = 12; y -= 1 }
  if (m > 12) { m = 1; y += 1 }
  year.value = y
  month.value = m
}

const summary = ref({ total: 0, diffRate: 0, completedCount: 0, avgFare: 0, totalDistance: 0, weeklyRevenue: [] })
const invoiceList = ref([])

const invoiceCounts = computed(() => ({
  waiting: invoiceList.value.filter((r) => r.invoiceStatus === 'WAITING').length,
  requested: invoiceList.value.filter((r) => r.invoiceStatus === 'REQUESTED').length,
  done: invoiceList.value.filter((r) => r.invoiceStatus === 'ISSUED').length,
}))

const maxWeekly = computed(() => {
  const amounts = (summary.value.weeklyRevenue || []).map((w) => Number(w.amount) || 0)
  return Math.max(1, ...amounts)
})
function barWidth(amount) {
  return (Number(amount) / maxWeekly.value) * 100
}

async function loadSettlement() {
  if (!vehicleNo.value) return
  loading.value = true
  try {
    const [summaryResp, listResp] = await Promise.all([
      axios.get(`${API_BASE}/api/settlements/vehicle/${encodeURIComponent(vehicleNo.value)}/summary`, {
        params: { year: year.value, month: month.value },
      }),
      axios.get(`${API_BASE}/api/settlements/vehicle/${encodeURIComponent(vehicleNo.value)}`, {
        params: { year: year.value, month: month.value },
      }),
    ])
    summary.value = summaryResp.data
    const rows = listResp.data
    for (const row of rows) {
      row.shipperName = row.shipperCompanyId ? await getCompanyName(row.shipperCompanyId) : null
    }
    invoiceList.value = rows
  } catch (err) {
    console.error(err)
    summary.value = { total: 0, diffRate: 0, completedCount: 0, avgFare: 0, totalDistance: 0, weeklyRevenue: [] }
    invoiceList.value = []
  } finally {
    loading.value = false
  }
}

async function requestInvoice(row) {
  if (!confirm(`#${row.dispatchId} 건의 세금계산서 발행을 요청하시겠습니까?`)) return
  try {
    await axios.patch(`${API_BASE}/api/dispatches/${row.dispatchId}/invoice-status`, null, {
      params: { status: 'REQUESTED' },
    })
    row.invoiceStatus = 'REQUESTED'
  } catch (err) {
    alert(err.response?.data?.message || '발행 요청에 실패했습니다.')
  }
}

function invoiceLabel(status) {
  return { WAITING: '발행대기', REQUESTED: '요청됨', ISSUED: '발행완료' }[status] || '-'
}
function invoiceBadgeClass(status) {
  return { WAITING: 'waiting', REQUESTED: 'active', ISSUED: 'done' }[status] || ''
}
function formatDate(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  return `${d.getMonth() + 1}.${String(d.getDate()).padStart(2, '0')}`
}
function formatWon(n) {
  return n != null ? Number(n).toLocaleString('ko-KR') : '0'
}
function formatWonShort(n) {
  if (n == null) return '0만'
  return (Number(n) / 10000).toLocaleString('ko-KR') + '만'
}

watch([year, month], loadSettlement)

onMounted(async () => {
  const userId = authState.user?.userId
  if (userId) {
    vehicleNo.value = localStorage.getItem(`scargo_myTruck_${userId}`)
  }
  await loadSettlement()
})
</script>

<style scoped>
.page { padding: 28px 32px; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 80px 20px; text-align: center; }
.link-btn { display: inline-block; text-decoration: none; text-align: center; width: auto; padding: 12px 24px; }

/* 월 이동 */
.month-nav { display: flex; align-items: center; justify-content: center; gap: 20px; margin-bottom: 16px; }
.nav-btn { width: 32px; height: 32px; border-radius: 8px; border: 1px solid var(--border); background: var(--surface); color: var(--text); font-size: 16px; cursor: pointer; }
.nav-btn:disabled { opacity: 0.35; cursor: default; }
.month-label { font-family: 'Barlow Condensed', sans-serif; font-size: 18px; font-weight: 600; min-width: 120px; text-align: center; }

/* 수익 카드 */
.revenue-card { background: linear-gradient(160deg, var(--surface) 0%, var(--surface-alt) 100%); border: 1px solid var(--border); border-radius: var(--radius); padding: 22px 20px; margin-bottom: 18px; }
.revenue-label { font-size: 13px; color: var(--text-muted); margin-bottom: 8px; }
.revenue-value { font-family: 'Barlow Condensed', sans-serif; font-size: 36px; font-weight: 700; color: var(--amber); line-height: 1; }
.revenue-value .unit { font-size: 18px; color: var(--text); margin-left: 4px; }
.revenue-diff { font-size: 13px; margin-top: 8px; font-weight: 600; }
.revenue-diff.up { color: var(--green); }
.revenue-diff.down { color: var(--red); }
.revenue-sub-row { display: flex; align-items: center; margin-top: 18px; padding-top: 16px; border-top: 1px solid var(--border); }
.sub-item { flex: 1; text-align: center; }
.sub-divider { width: 1px; height: 30px; background: var(--border); }
.sub-value { font-family: 'Barlow Condensed', sans-serif; font-size: 19px; font-weight: 700; }
.sub-value .unit { font-size: 13px; font-weight: 500; color: var(--text-muted); margin-left: 2px; }
.sub-label { font-size: 11.5px; color: var(--text-muted); margin-top: 3px; }

/* 주차별 그래프 */
.chart-card { margin-bottom: 20px; display: flex; flex-direction: column; gap: 12px; }
.bar-row { display: flex; align-items: center; gap: 10px; }
.bar-label { width: 42px; font-size: 12.5px; color: var(--text-muted); flex-shrink: 0; }
.bar-track { flex: 1; height: 10px; background: var(--surface-alt); border-radius: 6px; overflow: hidden; }
.bar-fill { height: 100%; background: var(--amber); border-radius: 6px; transition: width .3s ease; }
.bar-value { width: 52px; text-align: right; font-size: 12.5px; color: var(--text); font-weight: 500; flex-shrink: 0; }

/* 세금계산서 요약 칩 */
.invoice-summary { display: flex; gap: 8px; margin-bottom: 14px; flex-wrap: wrap; }
.invoice-chip { display: flex; align-items: center; gap: 6px; font-size: 12.5px; color: var(--text-muted); background: var(--surface); border: 1px solid var(--border); padding: 6px 10px; border-radius: 999px; }
.invoice-chip .dot { width: 7px; height: 7px; border-radius: 50%; }
.invoice-chip .dot.amber { background: var(--amber); }
.invoice-chip .dot.blue { background: #4DA6FF; }
.invoice-chip .dot.green { background: var(--green); }

/* 세금계산서 리스트 */
.list { display: flex; flex-direction: column; gap: 10px; }
.empty { text-align: center; color: var(--text-muted); font-size: 14px; padding: 30px 0; }
.invoice-card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 14px 16px; }
.invoice-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.invoice-date { font-size: 12.5px; color: var(--text-muted); }
.invoice-mid { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 10px; }
.invoice-shipper { font-size: 15px; font-weight: 600; }
.invoice-amount { font-size: 15px; font-weight: 700; }
.btn-request { width: 100%; padding: 10px 0; border-radius: 8px; border: 1px solid var(--amber); background: transparent; color: var(--amber); font-size: 13.5px; font-weight: 600; cursor: pointer; }
.btn-request:active { transform: scale(0.98); }
.invoice-note { font-size: 12.5px; color: var(--text-muted); }
.invoice-note.done { color: var(--green); }

.hint-text { text-align: center; color: var(--text-muted); font-size: 14px; padding: 40px 0; }
.btn-fill { border: none; background: var(--amber); color: #1A1300; font-weight: 600; border-radius: 8px; cursor: pointer; }
</style>
