<template>
  <div>
    <div class="admin-page-header">
      <h1>야드 관리</h1>
      <p>컨테이너 야드(부지) 정보를 등록하고 이용 가능 상태를 관리합니다.</p>
    </div>

    <CrudTable
      title="야드"
      id-key="yardId"
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
  { key: 'yardId', label: 'ID', width: '60px' },
  { key: 'yardName', label: '야드명' },
  { key: 'yardType', label: '용도' },
  { key: 'status', label: '상태' },
  { key: 'isAvailable', label: '이용가능', type: 'boolean' },
  { key: 'latitude', label: '위도' },
  { key: 'longitude', label: '경도' },
  { key: 'createdAt', label: '등록일', type: 'date' },
]

const formFields = [
  { key: 'yardName', label: '야드명', required: true, placeholder: '예: A야드 / 제1컨테이너 야드' },
  {
    key: 'yardType', label: '야드 용도', type: 'select', default: 'GENERAL',
    options: () => [
      { value: 'GENERAL', label: '일반' },
      { value: 'REFRIGERATED', label: '냉동' },
      { value: 'HAZARDOUS', label: '위험물' },
    ],
  },
  {
    key: 'status', label: '상태', type: 'select', default: 'AVAILABLE',
    options: () => [
      { value: 'AVAILABLE', label: '이용가능' },
      { value: 'MAINTENANCE', label: '점검중' },
      { value: 'UNAVAILABLE', label: '이용불가' },
    ],
  },
  { key: 'isAvailable', label: '이용 가능 여부', type: 'checkbox', checkboxLabel: '이용 가능', default: true },
  { key: 'latitude', label: '위도', type: 'number', step: '0.000001' },
  { key: 'longitude', label: '경도', type: 'number', step: '0.000001' },
]

async function loadRows() {
  loading.value = true
  try {
    const res = await adminApi.get('/api/yards')
    rows.value = res.data
  } catch (err) {
    alert(pickErrorMessage(err, '야드 목록을 불러오지 못했습니다. (관리자 권한 필요)'))
  } finally {
    loading.value = false
  }
}

function toNum(v) {
  return v === '' || v === null || v === undefined ? null : Number(v)
}

async function handleCreate(payload) {
  await adminApi.post('/api/yards', { ...payload, latitude: toNum(payload.latitude), longitude: toNum(payload.longitude) })
  await loadRows()
}

async function handleUpdate(id, payload) {
  await adminApi.put(`/api/yards/${id}`, { ...payload, latitude: toNum(payload.latitude), longitude: toNum(payload.longitude) })
  await loadRows()
}

async function handleDelete(id) {
  try {
    await adminApi.delete(`/api/yards/${id}`)
    await loadRows()
  } catch (err) {
    alert(pickErrorMessage(err, '삭제 중 오류가 발생했습니다.'))
  }
}

onMounted(loadRows)
</script>
