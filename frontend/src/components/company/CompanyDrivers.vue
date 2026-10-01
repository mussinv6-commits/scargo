<template>
  <div class="mapping-card">
    <h3>기사 관리</h3>

    <div v-if="loading" class="empty-text">불러오는 중...</div>
    <div v-else-if="drivers.length === 0 && !errorMsg" class="empty-text">
      소속된 화물차 기사(일반회원)가 아직 없습니다.
    </div>

    <table v-else-if="drivers.length" class="truck-table">
      <thead>
        <tr>
          <th>이름</th>
          <th>아이디</th>
          <th>연락처</th>
          <th>배정 차량</th>
          <th style="min-width: 260px;">배정 관리</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="d in drivers" :key="d.accountId">
          <td>{{ d.userName }}</td>
          <td>{{ d.userId }}</td>
          <td>{{ d.phoneNum || '-' }}</td>
          <td>
            <span v-if="assignedTruckOf(d)" class="pill pill-on">{{ assignedTruckOf(d).vehicleNo }}</span>
            <span v-else class="empty-text" style="padding:0;">미배정</span>
          </td>
          <td>
            <div v-if="assignedTruckOf(d)" style="display:flex; gap:6px; align-items:center;">
              <button
                class="mapping-submit-btn"
                style="width:auto; padding:6px 12px; font-size:12.5px; background:#c81e2c;"
                :disabled="processingAccountId === d.accountId"
                @click="unassign(d)"
              >
                {{ processingAccountId === d.accountId ? '처리 중...' : '배정 해제' }}
              </button>
            </div>
            <div v-else style="display:flex; gap:6px; align-items:center;">
              <select class="mapping-select" style="width:auto;" v-model="selectedTruckByDriver[d.accountId]">
                <option value="" disabled>차량 선택</option>
                <option v-for="t in availableTrucksFor(d)" :key="t.vehicleNo" :value="t.vehicleNo">
                  {{ t.vehicleNo }} ({{ t.truckType || '차종미상' }})
                </option>
              </select>
              <button
                class="mapping-submit-btn"
                style="width:auto; padding:6px 12px; font-size:12.5px;"
                :disabled="!selectedTruckByDriver[d.accountId] || processingAccountId === d.accountId"
                @click="assign(d)"
              >
                {{ processingAccountId === d.accountId ? '처리 중...' : '배정' }}
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-if="errorMsg" class="hint-text" style="color:#c81e2c; white-space:pre-line;">{{ errorMsg }}</p>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { API_BASE } from '@/utils/apiBase'
import { fetchCompanyDrivers, fetchCompanyTrucks } from '@/utils/companyDrivers.js'
import { friendlyError } from '@/utils/apiHelpers.js'

const drivers = ref([])
const trucks = ref([])
const loading = ref(true)
const errorMsg = ref('')
const selectedTruckByDriver = reactive({}) // { [accountId]: vehicleNo }
const processingAccountId = ref(null)

async function loadData() {
  loading.value = true
  errorMsg.value = ''
  // 26.09.30 수정: 기사/차량 목록을 각각 불러와 한쪽이 실패해도 나머지는 표시되도록 변경
  const [d, t] = await Promise.allSettled([fetchCompanyDrivers(), fetchCompanyTrucks()])
  if (d.status === 'fulfilled') drivers.value = d.value
  else errorMsg.value = d.reason?.message || '소속 기사 목록을 불러오지 못했습니다.'
  if (t.status === 'fulfilled') trucks.value = t.value
  else errorMsg.value = [errorMsg.value, t.reason?.message || '소속 차량 목록을 불러오지 못했습니다.'].filter(Boolean).join('\n')
  loading.value = false
}

function assignedTruckOf(driver) {
  return trucks.value.find((t) => t.assignedAccountId === driver.accountId) || null
}

// 아직 다른 기사에게 배정되지 않은 차량만 선택지로 노출
function availableTrucksFor() {
  return trucks.value.filter((t) => !t.assignedAccountId)
}

async function assign(driver) {
  const vehicleNo = selectedTruckByDriver[driver.accountId]
  if (!vehicleNo) return
  errorMsg.value = ''
  processingAccountId.value = driver.accountId
  try {
    await axios.patch(
      `${API_BASE}/api/trucks/${encodeURIComponent(vehicleNo)}/assign-driver`,
      null,
      { params: { accountId: driver.accountId }, withCredentials: true }
    )
    await loadData()
  } catch (err) {
    errorMsg.value = `기사 배정에 실패했습니다. ${friendlyError(err)}`
  } finally {
    processingAccountId.value = null
  }
}

async function unassign(driver) {
  const truck = assignedTruckOf(driver)
  if (!truck) return
  if (!confirm(`[${driver.userName}] 기사의 차량(${truck.vehicleNo}) 배정을 해제하시겠습니까?`)) return
  errorMsg.value = ''
  processingAccountId.value = driver.accountId
  try {
    await axios.patch(
      `${API_BASE}/api/trucks/${encodeURIComponent(truck.vehicleNo)}/unassign-driver`,
      null,
      { withCredentials: true }
    )
    await loadData()
  } catch (err) {
    errorMsg.value = `배정 해제에 실패했습니다. ${friendlyError(err)}`
  } finally {
    processingAccountId.value = null
  }
}

onMounted(loadData)
</script>