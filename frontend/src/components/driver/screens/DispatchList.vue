<template>
  <div class="topbar">
    <div>
      <div class="brand">배차목록</div>
      <div class="sub">대기 중인 배차와 지난 운행 이력을 확인하세요</div>
    </div>
  </div>

  <main class="page">
    <div v-if="!vehicleNo" class="empty-state">
      <p class="hint-text">차량을 먼저 등록해야 배차를 받을 수 있어요.</p>
      <RouterLink to="/driver/app/my-page#register" class="drv-cta">차량 등록하러 가기</RouterLink>
    </div>

    <template v-else>
      <!-- 탭 -->
      <div class="segment">
        <button class="segment-btn" :class="{ active: activeTab === 'waiting' }" @click="activeTab = 'waiting'">
          배차대기 <span class="count">{{ waitingList.length }}</span>
        </button>
        <button class="segment-btn" :class="{ active: activeTab === 'history' }" @click="activeTab = 'history'">
          지난이력
        </button>
      </div>

      <p v-if="loading" class="hint-text">불러오는 중...</p>

      <!-- 배차대기 리스트 -->
      <div v-else-if="activeTab === 'waiting'" class="card-grid">
        <div v-if="waitingList.length === 0" class="empty">현재 대기 중인 배차가 없습니다.</div>
        <div v-for="job in waitingList" :key="job.dispatchId" class="job-card">
          <div class="job-top">
            <span class="job-date">{{ formatDateTime(job.pickupTime) }} 상차</span>
            <span class="badge waiting">배차대기</span>
          </div>
          <div class="job-route">
            <span class="place">{{ job.pickupPlace || job.pickupAddress }}</span>
            <span class="arrow">→</span>
            <span class="place">{{ job.dropoffPlace || job.dropoffAddress }}</span>
          </div>
          <div class="job-meta">
            <span v-if="job.cargoItem">📦 {{ job.cargoItem }}</span>
            <span v-if="job.distanceKm">🛣️ {{ job.distanceKm }}km</span>
            <span class="fare">{{ formatWon(job.fare) }}원</span>
          </div>
          <div class="job-actions">
            <button class="btn-outline" @click="reject(job)">배차 거절</button>
            <button class="btn-fill" @click="accept(job)">배차 확정</button>
          </div>
        </div>
      </div>

      <!-- 지난이력 리스트 -->
      <div v-else class="card-grid">
        <div v-if="historyList.length === 0" class="empty">지난 운행 이력이 없습니다.</div>
        <div v-for="job in historyList" :key="job.dispatchId" class="job-card history">
          <div class="job-top">
            <span class="job-date">{{ formatDateTime(job.dropoffTime || job.createdAt) }}</span>
            <span class="badge" :class="job.status === 'COMPLETED' ? 'done' : 'cancel'">
              {{ job.status === 'COMPLETED' ? '운송완료' : '배차취소' }}
            </span>
          </div>
          <div class="job-route">
            <span class="place">{{ job.pickupPlace || job.pickupAddress }}</span>
            <span class="arrow">→</span>
            <span class="place">{{ job.dropoffPlace || job.dropoffAddress }}</span>
          </div>
          <div class="job-meta">
            <span v-if="job.cargoItem">📦 {{ job.cargoItem }}</span>
            <span v-if="job.distanceKm">🛣️ {{ job.distanceKm }}km</span>
            <span class="fare">{{ formatWon(job.fare) }}원</span>
          </div>
        </div>
      </div>
    </template>
  </main>
</template>

<script setup>
import { fetchMyVehicleNo } from '@/utils/driverTruck.js'
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { authState } from '@/auth/authState.js'
import { API_BASE } from '@/utils/apiBase.js'

const vehicleNo = ref(null)
const loading = ref(true)
const activeTab = ref('waiting')
const dispatches = ref([])

const waitingList = computed(() => dispatches.value.filter((d) => d.status === 'WAITING'))
const historyList = computed(() =>
  dispatches.value.filter((d) => d.status === 'COMPLETED' || d.status === 'CANCELED')
)

async function loadDispatches() {
  if (!vehicleNo.value) return
  loading.value = true
  try {
    const resp = await axios.get(`${API_BASE}/api/dispatches/vehicle/${encodeURIComponent(vehicleNo.value)}`)
    dispatches.value = resp.data
  } catch (err) {
    console.error(err)
    dispatches.value = []
  } finally {
    loading.value = false
  }
}

async function accept(job) {
  if (!confirm(`배차 #${job.dispatchId}를 확정하시겠습니까?`)) return
  try {
    await axios.patch(`${API_BASE}/api/dispatches/${job.dispatchId}/status`, null, { params: { status: 'ACCEPTED' } })
    await loadDispatches()
    alert('배차가 확정되었습니다. 운송현황 화면에서 확인하세요.')
  } catch (err) {
    alert(err.response?.data?.message || '배차 확정에 실패했습니다.')
  }
}

async function reject(job) {
  if (!confirm(`배차 #${job.dispatchId}를 거절하시겠습니까?`)) return
  try {
    await axios.patch(`${API_BASE}/api/dispatches/${job.dispatchId}/status`, null, { params: { status: 'CANCELED' } })
    await loadDispatches()
  } catch (err) {
    alert(err.response?.data?.message || '배차 거절에 실패했습니다.')
  }
}

function formatDateTime(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  return `${d.getMonth() + 1}.${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
function formatWon(n) {
  return n != null ? Number(n).toLocaleString('ko-KR') : '-'
}

onMounted(async () => {
  // 26.09.30 수정: 배정 차량을 서버에서 조회 (localStorage 값은 더 이상 저장되지 않음)
  vehicleNo.value = await fetchMyVehicleNo()
  await loadDispatches()
})
</script>

<style scoped>
.segment { max-width: 360px; }

/* 세그먼트 탭 */
.segment { display: flex; background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 4px; margin-bottom: 18px; }
.segment-btn { flex: 1; padding: 10px 0; border: none; background: transparent; color: var(--text-muted); font-size: 14px; font-weight: 600; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 6px; }
.segment-btn.active { background: var(--surface-alt); color: var(--amber); }
.segment-btn .count { font-size: 11px; background: var(--amber-soft); color: var(--amber); padding: 1px 6px; border-radius: 999px; }
.segment-btn.active .count { background: var(--amber); color: #fff; }

.list { display: flex; flex-direction: column; gap: 12px; }
.empty, .hint-text { text-align: center; color: var(--text-muted); font-size: 14px; padding: 40px 0; }

.job-card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 14px 16px; }
.job-card.history { opacity: 0.92; }
.job-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.job-date { font-size: 12.5px; color: var(--text-muted); font-weight: 500; }
.job-route { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; margin-bottom: 8px; }
.job-route .arrow { color: var(--text-muted); font-weight: 400; }
.job-meta { display: flex; gap: 12px; flex-wrap: wrap; font-size: 13px; color: var(--text-muted); margin-bottom: 10px; }
.job-meta .fare { margin-left: auto; color: var(--text); font-weight: 600; }
.job-actions { display: flex; gap: 8px; }
.btn-outline, .btn-fill { flex: 1; padding: 10px 0; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; }
.btn-outline { border: 1px solid var(--border); background: transparent; color: var(--text-muted); }
.btn-fill { border: none; background: var(--amber); color: #fff; }
.btn-outline:active, .btn-fill:active { transform: scale(0.98); }
</style>
