<template>
  <div class="topbar">
    <div>
      <div class="brand">배차목록</div>
      <div class="sub">매핑된 컨테이너와 적재 위치를 확인하세요</div>
    </div>
  </div>

  <main class="page">
    <div v-if="!truck" class="empty-state">
      <p class="hint-text">차량을 먼저 등록하고, 소속 업체에서 기사 배정을 받아야 배차를 볼 수 있어요.</p>
      <RouterLink to="/driver/app/my-page#register" class="drv-cta">MY/차량으로 가기</RouterLink>
    </div>

    <template v-else>
      <div class="segment">
        <button class="segment-btn" :class="{ active: activeTab === 'waiting' }" @click="activeTab = 'waiting'">
          진행중 <span class="count">{{ waitingList.length }}</span>
        </button>
        <button class="segment-btn" :class="{ active: activeTab === 'history' }" @click="activeTab = 'history'">
          지난이력
        </button>
      </div>

      <p v-if="loading" class="hint-text">불러오는 중...</p>

      <div v-else-if="activeTab === 'waiting'" class="card-grid">
        <div v-if="waitingList.length === 0" class="empty">현재 진행 중인 배차가 없습니다. 사업자가 컨테이너를 매핑하면 여기에 표시됩니다.</div>
        <div v-for="job in waitingList" :key="job.recordId" class="job-card">
          <div class="job-top">
            <span class="job-date">{{ formatDateTime(job.loadedAt) }} 배정</span>
            <span class="badge waiting">{{ assignmentStatusLabel(job.status) }}</span>
          </div>
          <div class="job-route">
            <span class="place">{{ job.containerNo || '-' }}</span>
          </div>
          <div class="job-meta">
            <span>적재 위치 {{ job.locationLabel }}</span>
            <span>차량 {{ truck.vehicleNo }}</span>
            <span>진입 {{ approval.label }}</span>
          </div>
          <div class="job-actions">
            <RouterLink class="btn-fill" to="/driver/app/status">운송현황에서 보기</RouterLink>
          </div>
        </div>
      </div>

      <div v-else class="card-grid">
        <div v-if="historyList.length === 0" class="empty">지난 운행 이력이 없습니다.</div>
        <div v-for="job in historyList" :key="job.recordId" class="job-card history">
          <div class="job-top">
            <span class="job-date">{{ formatDateTime(job.loadedAt) }}</span>
            <span class="badge" :class="job.status === 'COMPLETED' ? 'done' : 'cancel'">
              {{ assignmentStatusLabel(job.status) }}
            </span>
          </div>
          <div class="job-route">
            <span class="place">{{ job.containerNo || '-' }}</span>
          </div>
          <div class="job-meta">
            <span>적재 위치 {{ job.locationLabel }}</span>
          </div>
        </div>
      </div>
    </template>
  </main>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import {
  fetchDriverAssignment,
  entryApprovalMeta,
  assignmentStatusLabel,
  formatDateTime,
} from '@/utils/driverAssignment.js'

const truck = ref(null)
const current = ref(null)
const history = ref([])
const loading = ref(true)
const activeTab = ref('waiting')

const waitingList = computed(() => (current.value ? [current.value] : []))
const historyList = computed(() => history.value)
const approval = computed(() => entryApprovalMeta(truck.value?.entryApproval))

onMounted(async () => {
  loading.value = true
  const data = await fetchDriverAssignment()
  truck.value = data.truck
  current.value = data.current
  history.value = data.history
  loading.value = false
})
</script>

<style scoped>
.segment { max-width: 360px; display: flex; background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 4px; margin-bottom: 18px; }
.segment-btn { flex: 1; padding: 10px 0; border: none; background: transparent; color: var(--text-muted); font-size: 14px; font-weight: 600; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 6px; }
.segment-btn.active { background: var(--surface-alt); color: var(--amber); }
.segment-btn .count { font-size: 11px; background: var(--amber-soft); color: var(--amber); padding: 1px 6px; border-radius: 999px; }
.segment-btn.active .count { background: var(--amber); color: #fff; }

.card-grid { display: flex; flex-direction: column; gap: 12px; }
.empty, .hint-text { text-align: center; color: var(--text-muted); font-size: 14px; padding: 40px 0; }

.job-card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 14px 16px; }
.job-card.history { opacity: 0.92; }
.job-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.job-date { font-size: 12.5px; color: var(--text-muted); font-weight: 500; }
.job-route { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; margin-bottom: 8px; }
.job-meta { display: flex; gap: 12px; flex-wrap: wrap; font-size: 13px; color: var(--text-muted); margin-bottom: 10px; }
.job-actions { display: flex; gap: 8px; }
.btn-fill { flex: 1; padding: 10px 0; border-radius: 8px; font-size: 14px; font-weight: 600; text-align: center; text-decoration: none; border: none; background: var(--amber); color: #fff; }
.btn-fill:active { transform: scale(0.98); }
</style>
