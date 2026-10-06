<template>
  <div class="mapping-card mapping-card--form">
    <h3>차량 등록</h3>

    <form @submit.prevent="submit">
      <div class="mapping-field">
        <label>차량 번호<span style="color:#c81e2c;">*</span></label>
        <input v-model="form.vehicleNo" class="mapping-input" placeholder="예: 12가3456" required />
      </div>

      <div class="mapping-field">
        <label>차종</label>
        <input v-model="form.truckType" class="mapping-input" placeholder="예: 카고/윙바디/탱크로리 등" />
      </div>

      <div class="mapping-field">
        <label>세미트레일러 여부</label>
        <select v-model="form.isSemiTrailer" class="mapping-select">
          <option :value="false">아니오</option>
          <option :value="true">예</option>
        </select>
      </div>

      <div class="mapping-field" v-if="form.isSemiTrailer">
        <label>트레일러 번호</label>
        <input v-model="form.trailerNo" class="mapping-input" placeholder="트레일러 번호를 입력하세요" />
      </div>

      <div class="mapping-field">
        <label>최대 적재 중량 (kg)</label>
        <input v-model="form.maxLoadWeight" type="number" min="0" step="0.01" class="mapping-input" placeholder="예: 25000" @keydown="blockNegative" @input="clampWeight" />
      </div>

      <button type="submit" class="mapping-submit-btn" :disabled="submitting">
        {{ submitting ? '등록 중...' : '차량 등록' }}
      </button>
    </form>

    <div v-if="statusMessage" class="mapping-status" :class="isSuccess ? 'ok' : 'error'">
      {{ statusMessage }}
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import axios from 'axios'
import { authState } from '@/auth/authState.js'
import { API_BASE } from '@/utils/apiBase.js'
import { friendlyError } from '@/utils/apiHelpers.js'
import { VEHICLE_NO_PATTERN, VEHICLE_NO_MESSAGE, normalizeVehicleNo } from '@/utils/validators.js'

const submitting = ref(false)
const statusMessage = ref('')
const isSuccess = ref(true)

const form = reactive({
  vehicleNo: '',
  truckType: '',
  isSemiTrailer: false,
  trailerNo: '',
  maxLoadWeight: '',
})

function blockNegative(event) {
  if (event.key === '-' || event.key === 'Subtract') {
    event.preventDefault()
    return
  }
  if (event.key !== 'ArrowDown') return
  const current = event.target.value === '' ? 0 : Number(event.target.value)
  if (Number.isNaN(current) || current <= 0) event.preventDefault()
}
function clampWeight() {
  if (form.maxLoadWeight !== '' && Number(form.maxLoadWeight) < 0) form.maxLoadWeight = '0'
}

function resetForm() {
  form.vehicleNo = ''
  form.truckType = ''
  form.isSemiTrailer = false
  form.trailerNo = ''
  form.maxLoadWeight = ''
}

async function submit() {
  const companyId = authState.user?.companyId
  if (!companyId) {
    isSuccess.value = false
    statusMessage.value = '소속 업체 정보를 확인할 수 없습니다. 다시 로그인해주세요.'
    return
  }

  if (!VEHICLE_NO_PATTERN.test(normalizeVehicleNo(form.vehicleNo) || '')) {
    isSuccess.value = false
    statusMessage.value = VEHICLE_NO_MESSAGE
    return
  }
  if (form.isSemiTrailer && !form.trailerNo.trim()) {
    isSuccess.value = false
    statusMessage.value = '세미트레일러 차량은 트레일러 번호를 입력해주세요.'
    return
  }

  submitting.value = true
  statusMessage.value = ''
  try {
    await axios.post(
      `${API_BASE}/api/trucks`,
      {
        vehicleNo: normalizeVehicleNo(form.vehicleNo),
        companyId, // 26.09.21: 업체 선택 없이, 로그인한 사업자 본인 소속 업체로 자동 등록
        isSemiTrailer: form.isSemiTrailer,
        semiTrailer: form.isSemiTrailer, // 26.09.30: 백엔드 DTO(boolean isSemiTrailer) 는 JSON 키 semiTrailer 로 받음
        trailerNo: form.isSemiTrailer ? form.trailerNo || null : null,
        truckType: form.truckType || null,
        maxLoadWeight: form.maxLoadWeight === '' ? null : Number(form.maxLoadWeight),
      },
      { withCredentials: true }
    )
    isSuccess.value = true
    statusMessage.value = `[${form.vehicleNo}] 차량이 등록되었습니다. 관리자 진입 허가 심사 후 운행할 수 있습니다.`
    resetForm()
  } catch (err) {
    isSuccess.value = false
    statusMessage.value = `차량 등록에 실패했습니다. ${friendlyError(err)}`
  } finally {
    submitting.value = false
  }
}
</script>