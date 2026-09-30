<template>
  <div>
    <div class="admin-page-header">
      <h1>컨테이너 관리</h1>
      <p>컨테이너 규격/중량 정보를 등록하고 적재 위치를 관리합니다.</p>
    </div>

    <CrudTable
      title="컨테이너"
      id-key="containerNo"
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
const companyOptions = ref([])

const columns = [
  { key: 'containerNo', label: '컨테이너번호' },
  { key: 'isoSizeTypeCode', label: 'ISO코드' },
  { key: 'containerType', label: '타입' },
  { key: 'isHighCube', label: '하이큐브', type: 'boolean' },
  { key: 'maxGrossKg', label: 'MAX GROSS(kg)' },
  { key: 'tareKg', label: 'TARE(kg)' },
  { key: 'netKg', label: 'NET(kg)' },
  { key: 'loadingLocationId', label: '적재위치ID' },
  { key: 'createdAt', label: '등록일', type: 'date' },
]

const formFields = [
  {
    key: 'containerNo', label: '컨테이너 번호', required: true,
    placeholder: '예: BICU1234567 (영문4+숫자7)', disabled: (row) => !!row,
  },
  { key: 'companyId', label: '예약 업체(선택)', type: 'select', options: () => companyOptions.value, placeholder: '미지정' },
  { key: 'isoSizeTypeCode', label: 'ISO 규격·종류 코드', required: true, placeholder: '예: 45G1' },
  { key: 'containerType', label: '컨테이너 타입', required: true, placeholder: '일반/냉동 등' },
  { key: 'isHighCube', label: '하이큐브 여부', type: 'checkbox', checkboxLabel: '하이큐브입니다' },
  { key: 'maxGrossKg', label: 'MAX GROSS (kg)', type: 'number', step: '0.1', required: true },
  { key: 'tareKg', label: 'TARE (kg)', type: 'number', step: '0.1', required: true },
  { key: 'netKg', label: 'NET (kg)', type: 'number', step: '0.1', required: true },
  { key: 'cubicCapacityCbm', label: '내부 용적 (CBM)', type: 'number', step: '0.01', required: true },
  { key: 'cscApprovalNo', label: 'CSC 승인번호' },
  { key: 'loadingLocationId', label: '적재 위치 ID(선택)', type: 'number', hint: '적재 위치 관리 화면에서 ID를 확인할 수 있습니다.' },
  { key: 'reservedCargoInfo', label: '화물 상세 정보(JSON, 선택)', type: 'textarea' },
]

async function loadCompanyOptions() {
  const res = await adminApi.get('/api/companies/options')
  companyOptions.value = res.data.map((c) => ({ value: c.companyId, label: c.companyName }))
}

async function loadRows() {
  loading.value = true
  try {
    const res = await adminApi.get('/api/containers')
    rows.value = res.data
  } catch (err) {
    alert(pickErrorMessage(err, '컨테이너 목록을 불러오지 못했습니다. (관리자/승인기업 권한 필요)'))
  } finally {
    loading.value = false
  }
}

function toNum(v) {
  return v === '' || v === null || v === undefined ? null : Number(v)
}

function buildPayload(payload) {
  return {
    ...payload,
    companyId: toNum(payload.companyId),
    maxGrossKg: toNum(payload.maxGrossKg),
    tareKg: toNum(payload.tareKg),
    netKg: toNum(payload.netKg),
    cubicCapacityCbm: toNum(payload.cubicCapacityCbm),
    loadingLocationId: toNum(payload.loadingLocationId),
  }
}

async function handleCreate(payload) {
  await adminApi.post('/api/containers', buildPayload(payload))
  await loadRows()
}

async function handleUpdate(id, payload) {
  await adminApi.put(`/api/containers/${id}`, buildPayload(payload))
  await loadRows()
}

async function handleDelete(id) {
  try {
    await adminApi.delete(`/api/containers/${id}`)
    await loadRows()
  } catch (err) {
    alert(pickErrorMessage(err, '삭제 중 오류가 발생했습니다.'))
  }
}

onMounted(async () => {
  await loadCompanyOptions()
  await loadRows()
})
</script>
