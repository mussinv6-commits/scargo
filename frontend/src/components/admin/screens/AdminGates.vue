<template>
  <div>
    <!-- 26.09.30 수정: 제목/설명 줄이 어긋나던 문제 → 공통 헤더 컴포넌트로 통일 -->
    <AdminPageHeader title="검문소 관리" description="물류센터 검문소(게이트) 정보와 작동 상태를 관리합니다.">
      <template #actions>
        <button class="btn-admin btn-admin-ghost" @click="isMapModalOpen = true">
          <i class="bi bi-map"></i> 위치 보기
        </button>
      </template>
    </AdminPageHeader>

    <!-- 26.09.30: 전달받은 백엔드(scargo_260928)에는 /api/gates 컨트롤러가 없다 (GateLog 만 존재) -->
    <div v-if="apiMissing" class="crud-form-error" style="margin-bottom:16px;">
      검문소 API(/api/gates)가 백엔드에 아직 없습니다. 백엔드에 Gate 컨트롤러가 추가되면 이 화면이 그대로 동작합니다.
    </div>

    <CrudTable
      title="검문소"
      id-key="gateId"
      :columns="columns"
      :rows="rows"
      :loading="loading"
      :form-fields="formFields"
      :page-size="10"
      :row-label="(r) => r.gateName"
      :on-create="handleCreate"
      :on-update="handleUpdate"
      :on-delete="handleDelete"
    />

    <GateMapModal :is-open="isMapModalOpen" :gates="rows" @close="isMapModalOpen = false" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import CrudTable from '@/components/admin/CrudTable.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import GateMapModal from '@/components/admin/GateMapModal.vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'
import { normalizeRows, withBoolAliases, toNum, updateWithFallback , deleteError } from '@/utils/apiHelpers'

const rows = ref([])
const loading = ref(true)
const isMapModalOpen = ref(false)
const apiMissing = ref(false)

const TYPE_LABEL = { IN: '입구', OUT: '출구', BOTH: '출입구' }

const columns = [
  { key: 'gateId', label: 'ID', align: 'center', width: '70px', type: 'seq' },
  { key: 'gateCode', label: '게이트 코드' },
  { key: 'gateName', label: '검문소 명' },
  { key: 'gateType', label: '유형', align: 'center', formatter: (v) => TYPE_LABEL[v] || v || '-' },
  { key: 'isActive', label: '작동 여부', type: 'boolean', align: 'center', trueLabel: '작동중', falseLabel: '중지' },
  { key: 'createdAt', label: '등록일', type: 'date' },
]

const formFields = [
  {
    key: 'gateCode', label: '게이트 코드', required: true, placeholder: '예: GATE_IN_01',
    pattern: /^[A-Za-z0-9_-]+$/, patternMessage: '영문, 숫자, _, - 만 사용할 수 있습니다.',
  },
  { key: 'gateName', label: '검문소 명', required: true, placeholder: '예: 제1 물류문(정문)' },
  {
    key: 'gateType', label: '게이트 유형', type: 'select', required: true,
    options: Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label: `${label} (${value})` })),
  },
  { key: 'latitude', label: '위도', type: 'number', step: '0.0000001', min: -90, max: 90, placeholder: '예: 37.4791400' },
  { key: 'longitude', label: '경도', type: 'number', step: '0.0000001', min: -180, max: 180, placeholder: '예: 126.6042570' },
  { key: 'locationDescription', label: '상세 위치 설명', type: 'textarea', placeholder: '위치에 대한 상세 설명을 입력하세요.' },
  { key: 'isActive', label: '작동 여부', type: 'checkbox', checkboxLabel: '작동 중인 검문소입니다', default: true },
]

async function loadRows() {
  loading.value = true
  try {
    const res = await adminApi.get('/api/gates')
    rows.value = normalizeRows(res.data, { bools: ['isActive'], idKey: 'gateId' })
  } catch (err) {
    if (err?.response?.status === 404) apiMissing.value = true
    else alert(pickErrorMessage(err, '검문소 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

function buildPayload(payload) {
  return withBoolAliases(
    { ...payload, latitude: toNum(payload.latitude), longitude: toNum(payload.longitude) },
    ['isActive']
  )
}

// 26.09.30 수정: 기존에는 여기서 에러를 잡아 alert 만 띄우고 넘겨서, 실패해도 모달이 닫혔다.
// → 에러를 그대로 던져 CrudTable 모달 안에 안내 문구가 뜨도록 변경
async function handleCreate(payload) {
  await adminApi.post('/api/gates', buildPayload(payload))
  await loadRows()
}

async function handleUpdate(id, payload) {
  await updateWithFallback(adminApi, `/api/gates/${id}`, buildPayload(payload), 'patch')
  await loadRows()
}

async function handleDelete(id) {
  try {
    await adminApi.delete(`/api/gates/${id}`)
    await loadRows()
  } catch (err) {
    alert(`검문소 삭제에 실패했습니다.
${deleteError(err, '검문소')}`)
  }
}

onMounted(loadRows)
</script>
