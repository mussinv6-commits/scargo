<template>
  <div>
    <div class="admin-page-header">
      <h1>과적 검사 관리</h1>
      <p>축중기 등 계측 장비에서 수집된 과적 검사 기록을 조회하고, 오기 입력 건을 정정합니다.</p>
    </div>

    <CrudTable
      title="과적 검사 기록"
      id-key="checkId"
      :columns="columns"
      :rows="rows"
      :loading="loading"
      :form-fields="formFields"
      :on-create="handleCreate"
      :on-update="handleUpdate"
      :on-delete="handleDelete"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import CrudTable from '@/components/admin/CrudTable.vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'

const rows = ref([])
const loading = ref(true)

const columns = [
  { key: 'checkId', label: 'ID', width: '60px' },
  { key: 'vehicleNo', label: '차량번호' },
  { key: 'containerNo', label: '컨테이너번호' },
  { key: 'totalWeight', label: '총중량(kg)' },
  { key: 'maxPayload', label: '최대적재량(kg)' },
  { key: 'isViolation', label: '위반여부', type: 'boolean', trueLabel: '위반', falseLabel: '정상' },
  { key: 'isPassed', label: '최종통과', type: 'boolean', trueLabel: '통과', falseLabel: '미통과' },
  { key: 'retryCount', label: '재검증횟수' },
  { key: 'checkedAt', label: '검사일시', type: 'date' },
]

const formFields = [
  { key: 'vehicleNo', label: '화물차 번호', required: true, placeholder: '12가3456' },
  { key: 'containerNo', label: '컨테이너 번호(선택)' },
  { key: 'usagePurpose', label: '차량 용도/목적' },
  { key: 'emptyVehicleWeight', label: '공차중량(kg)', type: 'number' },
  { key: 'totalWeight', label: '총중량(kg)', type: 'number' },
  { key: 'maxPayload', label: '최대적재량(kg)', type: 'number' },
  { key: 'vgmWeight', label: 'VGM 총중량(kg)', type: 'number' },
  { key: 'tireCount', label: '타이어수', type: 'number' },
  { key: 'vehicleAxleCount', label: '차축수', type: 'number' },
  {
    key: 'isViolation', label: '규정 위반 여부', type: 'select', required: true, default: 'false',
    options: () => [{ value: 'false', label: '정상' }, { value: 'true', label: '위반' }],
  },
  { key: 'violationReason', label: '위반 사유', type: 'textarea' },
  { key: 'retryCount', label: '재검증 시도 횟수', type: 'number', default: 0 },
  {
    key: 'isPassed', label: '최종 통과 여부', type: 'select', required: true, default: 'true',
    options: () => [{ value: 'true', label: '통과' }, { value: 'false', label: '미통과' }],
  },
]

async function loadRows() {
  loading.value = true
  try {
    const res = await adminApi.get('/api/overload-checks')
    // 수정됨: Spring Boot Page 객체의 content 배열을 안전하게 추출하도록 변경
    rows.value = res.data.content || res.data
  } catch (err) {
    alert(pickErrorMessage(err, '과적 검사 목록을 불러오지 못했습니다. (관리자 권한 필요)'))
  } finally {
    loading.value = false
  }
}

function toNum(v) {
  return v === '' || v === null || v === undefined ? null : Number(v)
}
function toBool(v) {
  return v === true || v === 'true'
}

function buildPayload(payload) {
  return {
    ...payload,
    emptyVehicleWeight: toNum(payload.emptyVehicleWeight),
    totalWeight: toNum(payload.totalWeight),
    maxPayload: toNum(payload.maxPayload),
    vgmWeight: toNum(payload.vgmWeight),
    tireCount: toNum(payload.tireCount),
    vehicleAxleCount: toNum(payload.vehicleAxleCount),
    retryCount: toNum(payload.retryCount) ?? 0,
    isViolation: toBool(payload.isViolation),
    isPassed: toBool(payload.isPassed),
  }
}

async function handleCreate(payload) {
  await adminApi.post('/api/overload-checks', buildPayload(payload))
  await loadRows()
}

async function handleUpdate(id, payload) {
  await adminApi.put(`/api/overload-checks/${id}`, buildPayload(payload))
  await loadRows()
}

async function handleDelete(id) {
  try {
    await adminApi.delete(`/api/overload-checks/${id}`)
    await loadRows()
  } catch (err) {
    alert(pickErrorMessage(err, '삭제 중 오류가 발생했습니다.'))
  }
}

onMounted(loadRows)
</script>