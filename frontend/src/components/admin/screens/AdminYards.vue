<template>
  <div>
    <AdminPageHeader title="야드 관리" description="컨테이너 야드(부지) 정보를 등록하고 이용 가능 상태를 관리합니다.">
      <template #actions>
        <button class="btn-admin btn-admin-ghost" @click="openMapModal(null)">
          <i class="bi bi-map"></i> 전체 지도 보기
        </button>
      </template>
    </AdminPageHeader>

    <CrudTable
      title="야드"
      id-key="yardId"
      :columns="columns"
      :rows="visibleRows"
      :loading="loading"
      :form-fields="formFields"
      :page-size="10"
      :row-label="(r) => r.yardName"
      :on-create="handleCreate"
      :on-update="handleUpdate"
      :on-delete="handleDelete"
    />

    <YardMapModal
      :show="isMapOpen"
      :yards="visibleRows"
      :selected-yard="selectedYard"
      @close="isMapOpen = false"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { rowsForViewDate } from '@/components/admin/adminViewDate.js'
import CrudTable from '@/components/admin/CrudTable.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import YardMapModal from '@/components/admin/YardMapModal.vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'
import { normalizeRows, withBoolAliases, toNum, updateWithFallback , deleteError } from '@/utils/apiHelpers'

const rows = ref([])
const visibleRows = computed(() => rowsForViewDate(rows.value, 'createdAt'))
const loading = ref(true)
const isMapOpen = ref(false)
const selectedYard = ref(null)

function openMapModal(yard = null) {
  selectedYard.value = yard
  isMapOpen.value = true
}

const TYPE_LABEL = { GENERAL: '일반', REFRIGERATED: '냉동', HAZARDOUS: '위험물' }
const STATUS_BADGE = {
  AVAILABLE: { label: '운영중', tone: 'on' },
  MAINTENANCE: { label: '점검중', tone: 'warn' },
  UNAVAILABLE: { label: '운영중지', tone: 'off' },
}

// 26.09.30 수정
//  - 위도/경도 컬럼·입력칸 제거 (요청사항)
//  - '상태'(AVAILABLE 영문 그대로)와 '이용가능'(예/아니오)이 같은 뜻으로 겹쳐 보이던 문제 →
//    상태는 운영 상태(운영중/점검중/운영중지), 이용가능은 배차 가능 여부(가능/불가)로 구분해 표시
const columns = [
  { key: 'yardId', label: 'ID', width: '60px', align: 'center' },
  { key: 'yardName', label: '야드명' },
  { key: 'yardType', label: '용도', format: (v) => TYPE_LABEL[v] || v || '-' },
  { key: 'status', label: '운영 상태', type: 'badge', align: 'center', badge: (v) => STATUS_BADGE[v] || { label: v || '-', tone: 'muted' } },
  { key: 'isAvailable', label: '배차 가능', type: 'boolean', align: 'center', trueLabel: '가능', falseLabel: '불가' },
  { key: 'createdAt', label: '등록일', type: 'date' },
]

const formFields = [
  { key: 'yardName', label: '야드명', required: true, placeholder: '예: A야드 / 제1컨테이너 야드' },
  {
    key: 'yardType', label: '야드 용도', type: 'select', required: true, default: 'GENERAL',
    options: () => Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label })),
  },
  {
    key: 'status', label: '운영 상태', type: 'select', required: true, default: 'AVAILABLE',
    options: () => Object.entries(STATUS_BADGE).map(([value, b]) => ({ value, label: b.label })),
  },
  { key: 'isAvailable', label: '배차 가능 여부', type: 'checkbox', checkboxLabel: '이 야드로 배차할 수 있습니다', default: true },
]

async function loadRows() {
  loading.value = true
  try {
    const res = await adminApi.get('/api/yards')
    rows.value = normalizeRows(res.data, { bools: ['isAvailable'], idKey: 'yardId' })
  } catch (err) {
    alert(pickErrorMessage(err, '야드 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

function buildPayload(payload, row = null) {
  return withBoolAliases(
    {
      yardName: payload.yardName,
      yardType: payload.yardType,
      status: payload.status,
      isAvailable: payload.isAvailable,
      // 입력칸은 없앴지만, 수정 시 기존 좌표가 null 로 지워지지 않도록 그대로 다시 보낸다 (지도 표시용)
      latitude: row ? toNum(row.latitude) : null,
      longitude: row ? toNum(row.longitude) : null,
    },
    ['isAvailable']
  )
}

async function handleCreate(payload) {
  await adminApi.post('/api/yards', buildPayload(payload))
  await loadRows()
}

async function handleUpdate(id, payload, row) {
  await updateWithFallback(adminApi, `/api/yards/${id}`, buildPayload(payload, row), 'put')
  await loadRows()
}

async function handleDelete(id) {
  try {
    await adminApi.delete(`/api/yards/${id}`)
    await loadRows()
  } catch (err) {
    alert(`야드 삭제에 실패했습니다.
${deleteError(err, '야드')}`)
  }
}

onMounted(loadRows)
</script>
