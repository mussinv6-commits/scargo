<template>
  <div class="mapping-grid">
    <!-- 1. 신규 배차 매핑 폼 -->
    <section class="mapping-card">
      <h3>신규 배차 등록</h3>

      <form @submit.prevent="handleCreateMapping">
        <div class="mapping-field">
          <label>소속 차량 선택</label>
          <select v-model="form.vehicleNo" class="mapping-select" required>
            <option value="" disabled>차량을 선택하세요</option>
            <option v-for="truck in trucks" :key="truck.vehicleNo" :value="truck.vehicleNo">
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

      <div v-if="statusMessage" class="mapping-status" :class="isSuccess ? 'ok' : 'error'">
        {{ statusMessage }}
      </div>
    </section>

    <!-- 2. 보유 데이터 현황 -->
    <section class="mapping-card">
      <div class="mapping-section-header">
        <h4>📦 배정 대기중인 컨테이너</h4>
        <button class="mapping-refresh-btn" @click="fetchData">🔄 새로고침</button>
      </div>

      <table class="mapping-table">
        <thead>
          <tr>
            <th>컨테이너 번호</th>
            <th>유형</th>
            <th>적재 위치 ID</th>
            <th>상태</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in containers" :key="c.containerNo">
            <td>{{ c.containerNo }}</td>
            <td>{{ c.containerType }}</td>
            <td>{{ c.locationId || '미지정' }}</td>
            <td><span class="pill pill-off">미배정</span></td>
          </tr>
          <tr v-if="containers.length === 0">
            <td colspan="4" class="mapping-empty">배정 가능한 컨테이너가 없습니다.</td>
          </tr>
        </tbody>
      </table>

      <div class="mapping-section-header">
        <h4>🚛 소속 차량 목록 ({{ trucks.length }}대)</h4>
      </div>
      <div class="mapping-truck-grid">
        <div v-for="t in trucks" :key="t.vehicleNo" class="mapping-truck-card">
          <div class="truck-no">{{ t.vehicleNo }}</div>
          <div class="truck-meta">{{ t.truckType || '차종미상' }} · 트레일러 {{ t.isSemiTrailer ? 'Y' : 'N' }}</div>
        </div>
        <p v-if="trucks.length === 0" class="mapping-empty">소속 차량이 없습니다.</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
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
const loading = ref(false)
const statusMessage = ref('')
const isSuccess = ref(true)

const form = reactive({
  vehicleNo: '',
  containerNo: '',
})

const fetchData = async () => {
  statusMessage.value = ''
  try {
    const [containerRes, truckRes] = await Promise.all([
      api.get('/available-containers'),
      api.get('/my-trucks'),
    ])
    containers.value = containerRes.data
    trucks.value = truckRes.data
  } catch (error) {
    console.error('데이터 조회 실패:', error)
    isSuccess.value = false
    if (error.response?.status === 401) {
      statusMessage.value = '로그인이 필요하거나 세션이 만료되었습니다. 다시 로그인해주세요.'
    } else {
      statusMessage.value = error.response?.data?.message || '데이터를 불러오지 못했습니다.'
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
    await fetchData()
  } catch (error) {
    console.error('매핑 실패:', error)
    isSuccess.value = false
    statusMessage.value = error.response?.data?.message || '배차 매핑 중 오류가 발생했습니다.'
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>
