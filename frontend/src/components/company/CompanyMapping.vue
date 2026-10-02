<template>
  <div>
  <div v-if="statusMessage" class="mapping-status mapping-status-banner" :class="isSuccess ? 'ok' : 'error'">
    {{ statusMessage }}
  </div>
  <div class="mapping-grid">
    <!-- 1. 신규 배차 매핑 폼 -->
    <section class="mapping-card">
      <h3>신규 배차 등록</h3>

      <form @submit.prevent="handleCreateMapping">
        <div class="mapping-field">
          <label>소속 차량 선택</label>
          <select v-model="form.vehicleNo" class="mapping-select" required>
            <option value="" disabled>차량을 선택하세요</option>
            <option v-for="truck in freeTrucks" :key="truck.vehicleNo" :value="truck.vehicleNo">
              {{ truck.vehicleNo }} ({{ truck.truckType || '차종미상' }})
            </option>
          </select>
        </div>

        <div class="mapping-field">
          <label>배정 가능 컨테이너</label>
          <select v-model="form.containerNo" class="mapping-select" required>
            <option value="" disabled>컨테이너를 선택하세요</option>
            <option v-for="container in containers" :key="container.containerNo" :value="container.containerNo">
              {{ container.containerNo }} [{{ container.containerType }}]
            </option>
          </select>
        </div>

        <button type="submit" class="mapping-submit-btn" :disabled="loading || !form.vehicleNo || !form.containerNo">
          {{ loading ? '처리 중...' : '배차 매핑 등록' }}
        </button>
      </form>
    </section>

    <!-- 2. 보유 데이터 현황 -->
    <section class="mapping-card">
      <div class="mapping-section-header">
        <h4>현재 매핑 ({{ activeMappings.length }}건)</h4>
        <button class="mapping-refresh-btn" @click="fetchData">🔄 새로고침</button>
      </div>

      <table class="mapping-table">
        <thead>
          <tr>
            <th style="width:64px;">번호</th>
            <th>컨테이너 번호</th>
            <th>유형</th>
            <th>배정 차량</th>
            <th style="min-width:220px;">관리</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, idx) in activeMappings" :key="row.containerNo">
            <td>{{ idx + 1 }}</td>
            <td>{{ row.containerNo }}</td>
            <td>{{ row.containerType || '-' }}</td>
            <td>{{ row.vehicleNo }}</td>
            <td>
              <div v-if="editingNo === row.containerNo" class="mapping-actions">
                <select v-model="editVehicleNo" class="mapping-select">
                  <option v-for="t in trucksForEdit(row)" :key="t.vehicleNo" :value="t.vehicleNo">
                    {{ t.vehicleNo }} ({{ t.truckType || '차종미상' }})
                  </option>
                </select>
                <button type="button" class="mapping-mini-btn" :disabled="busyNo === row.containerNo" @click="saveEdit(row)">
                  {{ busyNo === row.containerNo ? '처리 중' : '저장' }}
                </button>
                <button type="button" class="mapping-mini-btn ghost" :disabled="busyNo === row.containerNo" @click="closeEdit">닫기</button>
              </div>
              <div v-else class="mapping-actions">
                <button type="button" class="mapping-mini-btn" :disabled="busyNo === row.containerNo" @click="startEdit(row)">수정</button>
                <button type="button" class="mapping-mini-btn danger" :disabled="busyNo === row.containerNo" @click="cancelMapping(row)">취소</button>
              </div>
            </td>
          </tr>
          <tr v-if="activeMappings.length === 0">
            <td colspan="5" class="mapping-empty">매핑된 컨테이너가 없습니다.</td>
          </tr>
        </tbody>
      </table>

      <div class="mapping-section-header">
        <h4>📦 배정 대기중인 컨테이너</h4>
        <button class="mapping-refresh-btn" @click="fetchData">🔄 새로고침</button>
      </div>

      <table class="mapping-table">
        <thead>
          <tr>
            <th style="width:64px;">번호</th>
            <th>컨테이너 번호</th>
            <th>유형</th>
            <th>적재 위치 ID</th>
            <th>상태</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(c, idx) in containers" :key="c.containerNo">
            <td>{{ idx + 1 }}</td>
            <td>{{ c.containerNo }}</td>
            <td>{{ c.containerType }}</td>
            <td>{{ c.locationId || '미지정' }}</td>
            <td><span class="pill pill-off">미배정</span></td>
          </tr>
          <tr v-if="containers.length === 0">
            <td colspan="5" class="mapping-empty">배정 가능한 컨테이너가 없습니다.</td>
          </tr>
        </tbody>
      </table>

      <div class="mapping-section-header">
        <h4>🚛 소속 차량 목록 ({{ trucks.length }}대)</h4>
      </div>
      <div class="mapping-truck-grid">
        <div v-for="t in trucks" :key="t.vehicleNo" class="mapping-truck-card">
          <div class="truck-no">{{ t.vehicleNo }}</div>
          <div class="truck-meta">{{ t.truckType || '차종미상' }} · 트레일러 {{ (t.isSemiTrailer ?? t.semiTrailer) ? 'Y' : 'N' }}</div>
        </div>
        <p v-if="trucks.length === 0" class="mapping-empty">소속 차량이 없습니다.</p>
      </div>
    </section>
  </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { API_BASE } from '@/utils/apiBase'

// /api/mappings 는 로그인한 계정의 세션(JSESSIONID)을 기준으로 동작하므로
// withCredentials 가 반드시 필요하다.
const api = axios.create({
  baseURL: `${API_BASE}/api/mappings`,
  withCredentials: true,
})

const trucks = ref([])
const containers = ref([])
const activeMappings = ref([])
const loading = ref(false)
const statusMessage = ref('')
const isSuccess = ref(true)
const editingNo = ref('')
const editVehicleNo = ref('')
const busyNo = ref('')

const mappedVehicleNos = computed(() => new Set(activeMappings.value.map((row) => row.vehicleNo)))
const freeTrucks = computed(() => trucks.value.filter((truck) => !mappedVehicleNos.value.has(truck.vehicleNo)))

function trucksForEdit(row) {
  return trucks.value.filter((truck) => truck.vehicleNo === row.vehicleNo || !mappedVehicleNos.value.has(truck.vehicleNo))
}

const form = reactive({
  vehicleNo: '',
  containerNo: '',
})

function apiMessage(error, fallback) {
  const message = error.response?.data?.message
  if (typeof message === 'string' && message && !message.includes('com/scargo') && !message.includes('com.scargo')) {
    return message
  }
  return fallback
}

const loadLists = async () => {
  const [containerResult, truckResult, activeResult] = await Promise.allSettled([
    api.get('/available-containers'),
    api.get('/my-trucks'),
    api.get('/active'),
  ])
  if (containerResult.status === 'fulfilled') containers.value = containerResult.value.data
  if (truckResult.status === 'fulfilled') trucks.value = truckResult.value.data
  if (activeResult.status === 'fulfilled') activeMappings.value = activeResult.value.data

  const failed = [containerResult, truckResult, activeResult].find((result) => result.status === 'rejected')
  if (failed) throw failed.reason
}

const fetchData = async () => {
  statusMessage.value = ''
  try {
    await loadLists()
  } catch (error) {
    console.error('데이터 조회 실패:', error)
    isSuccess.value = false
    if (error.response?.status === 401) {
      statusMessage.value = '로그인이 필요하거나 세션이 만료되었습니다. 다시 로그인해주세요.'
    } else if (error.response?.status === 404) {
      statusMessage.value = '매핑 목록 API가 아직 반영되지 않았습니다. 서버를 완전히 끈 뒤 다시 켜고 새로고침해주세요.'
    } else {
      statusMessage.value = apiMessage(error, '데이터를 불러오지 못했습니다. 서버를 완전히 끈 뒤 다시 켜고 새로고침해주세요.')
    }
  }
}

const handleCreateMapping = async () => {
  loading.value = true
  statusMessage.value = ''
  try {
    await api.post('', {
      vehicleNo: form.vehicleNo,
      containerNo: form.containerNo,
    })
    isSuccess.value = true
    statusMessage.value = `[${form.vehicleNo}] 차량과 [${form.containerNo}] 컨테이너가 성공적으로 매핑되었습니다.`
    form.vehicleNo = ''
    form.containerNo = ''
    await loadLists()
  } catch (error) {
    console.error('매핑 실패:', error)
    isSuccess.value = false
    statusMessage.value = error.response?.data?.message || '배차 매핑 중 오류가 발생했습니다.'
  } finally {
    loading.value = false
  }
}

function startEdit(row) {
  editingNo.value = row.containerNo
  editVehicleNo.value = row.vehicleNo
}

function closeEdit() {
  editingNo.value = ''
  editVehicleNo.value = ''
}

async function saveEdit(row) {
  if (!editVehicleNo.value || editVehicleNo.value === row.vehicleNo) {
    isSuccess.value = false
    statusMessage.value = '변경할 차량을 선택해주세요.'
    return
  }
  busyNo.value = row.containerNo
  statusMessage.value = ''
  try {
    await api.put(`/${encodeURIComponent(row.containerNo)}`, { vehicleNo: editVehicleNo.value })
    isSuccess.value = true
    statusMessage.value = `[${row.containerNo}] 컨테이너의 배정 차량을 [${editVehicleNo.value}]로 변경했습니다.`
    closeEdit()
    await loadLists()
  } catch (error) {
    isSuccess.value = false
    statusMessage.value = error.response?.data?.message || '매핑 수정 중 오류가 발생했습니다.'
  } finally {
    busyNo.value = ''
  }
}

async function cancelMapping(row) {
  if (!confirm(`[${row.containerNo}] 컨테이너와 [${row.vehicleNo}] 차량의 매핑을 취소하시겠습니까?`)) return
  busyNo.value = row.containerNo
  statusMessage.value = ''
  try {
    await api.delete(`/${encodeURIComponent(row.containerNo)}`)
    isSuccess.value = true
    statusMessage.value = `[${row.containerNo}] 컨테이너 매핑을 취소했습니다.`
    if (editingNo.value === row.containerNo) closeEdit()
    await loadLists()
  } catch (error) {
    isSuccess.value = false
    statusMessage.value = error.response?.data?.message || '매핑 취소 중 오류가 발생했습니다.'
  } finally {
    busyNo.value = ''
  }
}

onMounted(fetchData)
</script>

<style scoped>
.mapping-status-banner { margin: 0 0 14px; }
.mapping-actions {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}
.mapping-actions .mapping-select {
  width: 168px;
  height: 34px;
  padding: 0 8px;
}
.mapping-mini-btn {
  height: 34px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  background: var(--color-accent);
  color: #fff;
  font-weight: 700;
  font-size: 12.5px;
  cursor: pointer;
  flex-shrink: 0;
}
.mapping-mini-btn.danger { background: #c81e2c; }
.mapping-mini-btn.ghost {
  background: #fff;
  color: var(--color-subtext);
  border: 1px solid #dfe3ea;
}
.mapping-mini-btn:disabled { opacity: 0.6; cursor: default; }
</style>
