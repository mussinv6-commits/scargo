<template>
  <div>
    <div class="admin-page-header">
      <h1>적재 위치 관리</h1>
      <p>야드 내 섹터/블록(적재 위치) 단위로 상태와 가용 여부를 관리합니다.</p>
    </div>

    <CrudTable
      title="적재 위치"
      id-key="locationId"
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
const yardOptions = ref([])
const yardNameById = ref({})

const columns = [
  { key: 'locationId', label: 'ID', width: '60px' },
  { key: 'yardId', label: '소속야드', format: (v) => yardNameById.value[v] || `#${v}` },
  { key: 'sector', label: '섹터/블록명' },
  { key: 'status', label: '상태' },
  { key: 'isAvailable', label: '이용가능', type: 'boolean' },
  { key: 'latitude', label: '위도' },
  { key: 'longitude', label: '경도' },
]

const formFields = [
  { key: 'yardId', label: '소속 야드', type: 'select', required: true, options: () => yardOptions.value, disabled: (row) => !!row },
  { key: 'sector', label: '섹터/블록명', required: true, placeholder: '예: A-1, B블록' },
  {
    key: 'status', label: '상태', type: 'select', default: 'AVAILABLE',
    options: () => [
      { value: 'AVAILABLE', label: '이용가능' },
      { value: 'OCCUPIED', label: '사용중' },
      { value: 'BLOCKED', label: '이용불가' },
    ],
  },
  { key: 'isAvailable', label: '이용 가능 여부', type: 'checkbox', checkboxLabel: '이용 가능', default: true },
  { key: 'latitude', label: '위도', type: 'number', step: '0.000001' },
  { key: 'longitude', label: '경도', type: 'number', step: '0.000001' },
]

async function loadYardOptions() {
  const res = await adminApi.get('/api/yards/options')
  yardOptions.value = res.data.map((y) => ({ value: y.yardId, label: `${y.yardName} (${y.yardType})` }))
  yardNameById.value = Object.fromEntries(res.data.map((y) => [y.yardId, y.yardName]))
}

async function loadRows() {
  loading.value = true
  try {
    const res = await adminApi.get('/api/loading-locations')
    rows.value = res.data
  } catch (err) {
    alert(pickErrorMessage(err, '적재 위치 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

function toNum(v) {
  return v === '' || v === null || v === undefined ? null : Number(v)
}

async function handleCreate(payload) {
  await adminApi.post('/api/loading-locations', {
    ...payload,
    yardId: toNum(payload.yardId),
    latitude: toNum(payload.latitude),
    longitude: toNum(payload.longitude),
  })
  await loadRows()
}

async function handleUpdate(id, payload) {
  // yardId는 수정 불가(생성 시에만 지정) - sector/좌표/상태만 PATCH
  await adminApi.patch(`/api/loading-locations/${id}`, {
    sector: payload.sector,
    status: payload.status,
    isAvailable: payload.isAvailable,
    latitude: toNum(payload.latitude),
    longitude: toNum(payload.longitude),
  })
  await loadRows()
}

async function handleDelete(id) {
  try {
    await adminApi.delete(`/api/loading-locations/${id}`)
    await loadRows()
  } catch (err) {
    alert(pickErrorMessage(err, '삭제 중 오류가 발생했습니다.'))
  }
}

onMounted(async () => {
  await loadYardOptions()
  await loadRows()
})
</script>
