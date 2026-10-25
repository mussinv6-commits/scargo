<template>
  <div class="topbar">
    <div>
      <div class="brand">정산/매출</div>
      <div class="sub">이번 달 완료 운행을 확인하세요</div>
    </div>
  </div>

  <main class="page">
    <div v-if="!vehicleNo" class="empty-state">
      <p class="hint-text">차량을 먼저 등록해야 정산 내역을 볼 수 있어요.</p>
      <RouterLink to="/driver/app/my-page#register" class="drv-cta">차량 등록하러 가기</RouterLink>
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
          <div class="revenue-diff">운임 데이터는 백엔드에 없습니다. 완료 운행 건수만 표시합니다.</div>

          <div class="revenue-sub-row">
            <div class="sub-item">
              <div class="sub-value">{{ summary.completedCount }}<span class="unit">건</span></div>
              <div class="sub-label">완료 운행</div>
            </div>
          </div>
        </div>

        <div class="layout-2col">
          <div class="col-main">
            <!-- 세금계산서 현황 -->
            <div class="section-title">완료 운행</div>

            <div class="card-grid">
              <div v-if="invoiceList.length === 0" class="empty">이 달에 완료된 운행이 없습니다.</div>
              <div v-for="row in invoiceList" :key="row.recordId" class="invoice-card">
                <div class="invoice-top">
                  <span class="invoice-date">{{ formatDate(row.loadedAt) }} · {{ row.containerNo }}</span>
                  <span class="badge done">운송완료</span>
                </div>
                <div class="invoice-mid">
                  <span class="invoice-shipper">{{ row.locationLabel }}</span>
                </div>
                <div class="invoice-note">정산·세금계산서 API는 백엔드에 아직 없습니다.</div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </template>
  </main>
</template>

<script setup>
import { fetchMyVehicleNo } from '@/utils/driverTruck.js'
import { ref, computed, onMounted, watch } from 'vue'
import { fetchDriverAssignment } from '@/utils/driverAssignment.js'

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

async function loadSettlement() {
  if (!vehicleNo.value) return
  loading.value = true
  try {
    const { history } = await fetchDriverAssignment()
    const monthRows = history.filter((r) => {
      if (r.status !== 'COMPLETED' || !r.loadedAt) return false
      const d = new Date(r.loadedAt)
      return d.getFullYear() === year.value && d.getMonth() + 1 === month.value
    })
    invoiceList.value = monthRows
    summary.value = {
      total: 0,
      diffRate: 0,
      completedCount: monthRows.length,
      avgFare: 0,
      totalDistance: 0,
      weeklyRevenue: [],
    }
  } catch (err) {
    console.error(err)
    summary.value = { total: 0, diffRate: 0, completedCount: 0, avgFare: 0, totalDistance: 0, weeklyRevenue: [] }
    invoiceList.value = []
  } finally {
    loading.value = false
  }
}
function formatDate(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  return `${d.getMonth() + 1}.${String(d.getDate()).padStart(2, '0')}`
}
function formatWon(n) {
  return n != null ? Number(n).toLocaleString('ko-KR') : '0'
}

watch([year, month], loadSettlement)

onMounted(async () => {
  // 26.09.30 수정: 배정 차량을 서버에서 조회 (localStorage 값은 더 이상 저장되지 않음)
  vehicleNo.value = await fetchMyVehicleNo()
  await loadSettlement()
})
</script>

<style scoped>

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
.btn-fill { border: none; background: var(--amber); color: #fff; font-weight: 600; border-radius: 8px; cursor: pointer; }
</style>
