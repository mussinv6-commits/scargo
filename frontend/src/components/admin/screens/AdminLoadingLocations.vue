<template>
  <div>
    <AdminPageHeader title="적재 위치 관리" description="야드 내 섹터/블록(적재 위치) 단위로 상태와 가용 여부를 관리합니다.">
      <template #actions>
        <button class="btn-admin btn-admin-ghost" @click="isMapOpen = true">
          <i class="bi bi-map"></i> 전체 지도 보기
        </button>
      </template>
    </AdminPageHeader>

    <CrudTable
      title="적재 위치"
      id-key="locationId"
      :columns="columns"
      :rows="rows"
      :loading="loading"
      :form-fields="formFields"
      :page-size="10"
      :row-label="(r) => r.sector"
      :on-create="handleCreate"
      :on-update="handleUpdate"
      :on-delete="handleDelete"
    />

    <LocationMapModal
      :show="isMapOpen"
      title="적재 위치 전체 지도"
      :items="mapItems"
      @close="isMapOpen = false"
    />
  </div>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue'
import CrudTable from '@/components/admin/CrudTable.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import LocationMapModal from '@/components/admin/LocationMapModal.vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'
import { normalizeRows, withBoolAliases, toNum, updateWithFallback , deleteError } from '@/utils/apiHelpers'

const rows = ref([])
const loading = ref(true)
const yardOptions = ref([])
const yardNameById = ref({})
const isMapOpen = ref(false)

const mapItems = computed(() => rows.value.map((row) => ({ ...row, yardName: yardNameById.value[row.yardId] || '' })))

const STATUS_BADGE = {
  AVAILABLE: { label: '비어있음', tone: 'on' },
  OCCUPIED: { label: '사용중', tone: 'warn' },
  BLOCKED: { label: '사용불가', tone: 'off' },
}

// 26.09.30 수정: 상태(AVAILABLE 등 영문)와 이용가능(예/아니오) 컬럼이 같은 의미로 겹쳐 보이던 문제 →
// '적재 상태'(비어있음/사용중/사용불가)와 '배정 가능'(가능/불가)으로 구분
const columns = [
  { key: 'locationId', label: 'ID', width: '60px', align: 'center' },
  { key: 'yardId', label: '소속야드', format: (v) => yardNameById.value[v] || `#${v}` },
  { key: 'sector', label: '섹터/블록명' },
  { key: 'status', label: '적재 상태', type: 'badge', align: 'center', badge: (v) => STATUS_BADGE[v] || { label: v || '-', tone: 'muted' } },
  { key: 'isAvailable', label: '배정 가능', type: 'boolean', align: 'center', trueLabel: '가능', falseLabel: '불가' },
]

const formFields = [
  { key: 'yardId', label: '소속 야드', type: 'select', required: true, options: () => yardOptions.value, disabled: (row) => !!row },
  { key: 'sector', label: '섹터/블록명', required: true, placeholder: '예: A-1, B블록' },
  {
    key: 'status', label: '적재 상태', type: 'select', required: true, default: 'AVAILABLE',
    options: () => Object.entries(STATUS_BADGE).map(([value, b]) => ({ value, label: b.label })),
  },
  { key: 'isAvailable', label: '배정 가능 여부', type: 'checkbox', checkboxLabel: '컨테이너를 배정할 수 있습니다', default: true },
]

async function loadYardOptions() {
  try {
    const res = await adminApi.get('/api/yards/options')
    const list = normalizeRows(res.data, { idKey: 'yardId' })
    yardOptions.value = list.map((y) => ({ value: y.yardId, label: y.yardType ? `${y.yardName} (${y.yardType})` : y.yardName }))
    yardNameById.value = Object.fromEntries(list.map((y) => [y.yardId, y.yardName]))
  } catch (err) {
    console.error('야드 옵션 로드 실패:', err)
  }
}

async function loadRows() {
  loading.value = true
  try {
    const res = await adminApi.get('/api/loading-locations')
    rows.value = normalizeRows(res.data, { bools: ['isAvailable'], idKey: 'locationId' })
  } catch (err) {
    alert(pickErrorMessage(err, '적재 위치 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

async function handleCreate(payload) {
  await adminApi.post(
    '/api/loading-locations',
    withBoolAliases(
      { ...payload, yardId: toNum(payload.yardId) },
      ['isAvailable']
    )
  )
  await loadRows()
}

async function handleUpdate(id, payload, row) {
  // yardId는 수정 불가(생성 시에만 지정) - 그래도 PUT 로 받는 백엔드를 위해 기존 값을 함께 보낸다
  const body = withBoolAliases(
    {
      yardId: toNum(row?.yardId),
      sector: payload.sector,
      status: payload.status,
      isAvailable: payload.isAvailable,
    },
    ['isAvailable']
  )
  await updateWithFallback(adminApi, `/api/loading-locations/${id}`, body, 'patch')
  await loadRows()
}

async function handleDelete(id) {
  try {
    await adminApi.delete(`/api/loading-locations/${id}`)
    await loadRows()
  } catch (err) {
    alert(`적재 위치 삭제에 실패했습니다.
${deleteError(err, '적재 위치')}`)
  }
}

onMounted(async () => {
  await loadYardOptions()
  await loadRows()
})
</script>
