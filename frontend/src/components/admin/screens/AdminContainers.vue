<template>
  <div>
    <AdminPageHeader title="컨테이너 관리" description="컨테이너 규격/중량 정보를 등록하고 적재 위치를 관리합니다.">
      <template #actions>
        <button class="btn-admin btn-admin-ghost" @click="isMapOpen = true">
          <i class="bi bi-geo-alt"></i> 컨테이너 위치 지도
        </button>
      </template>
    </AdminPageHeader>

    <CrudTable
      title="컨테이너"
      id-key="containerNo"
      :columns="columns"
      :rows="rows"
      :loading="loading"
      :form-fields="formFields"
      :page-size="10"
      show-index
      index-label="번호"
      :on-create="handleCreate"
      :on-update="handleUpdate"
      :on-delete="handleDelete"
    />

    <ContainerMapModal
      :show="isMapOpen"
      title="전체 컨테이너 위치 지도"
      :containers="mapContainers"
      @close="isMapOpen = false"
    />
  </div>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue'
import CrudTable from '@/components/admin/CrudTable.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import ContainerMapModal from '@/components/admin/ContainerMapModal.vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'
import { normalizeRows, withBoolAliases, toNum, seg, updateWithFallback , deleteError } from '@/utils/apiHelpers'

const rows = ref([])
const locations = ref([])
const loading = ref(true)
const companyOptions = ref([])
const isMapOpen = ref(false)

const locationById = computed(() => Object.fromEntries(locations.value.map((l) => [l.locationId, l])))

const mapContainers = computed(() =>
  rows.value.map((container) => {
    const loc = locationById.value[container.loadingLocationId] || {}
    return {
      ...container,
      latitude: container.latitude || loc.latitude,
      longitude: container.longitude || loc.longitude,
      sector: loc.sector || `위치 #${container.loadingLocationId}`,
    }
  })
)

const locationOptions = computed(() =>
  locations.value.map((l) => ({ value: l.locationId, label: `#${l.locationId} · ${l.sector}${l.isAvailable ? '' : ' (이용불가)'}` }))
)

const columns = [
  { key: 'containerNo', label: '컨테이너번호' },
  { key: 'isoSizeTypeCode', label: 'ISO코드', align: 'center' },
  { key: 'containerType', label: '타입' },
  // 26.09.30 수정: 하이큐브 여부가 항상 '아니오'로 보이던 문제(응답 키 highCube) + 표시 문구 개선
  {
    key: 'isHighCube', label: '하이큐브', type: 'badge', align: 'center',
    badge: (v) => (v ? { label: '하이큐브', tone: 'warn' } : { label: '일반', tone: 'muted' }),
  },
  { key: 'maxGrossKg', label: 'MAX GROSS(kg)', align: 'center', format: fmtNum },
  { key: 'tareKg', label: 'TARE(kg)', align: 'center', format: fmtNum },
  { key: 'netKg', label: 'NET(kg)', align: 'center', format: fmtNum },
  { key: 'loadingLocationId', label: '적재위치', format: (v) => (v ? (locationById.value[v]?.sector ? `${locationById.value[v].sector} (#${v})` : `#${v}`) : '-') },
  { key: 'createdAt', label: '등록일', type: 'date' },
]

function fmtNum(v) {
  return v === null || v === undefined || v === '' ? '-' : Number(v).toLocaleString('ko-KR')
}

const formFields = [
  {
    key: 'containerNo', label: '컨테이너 번호', required: true,
    placeholder: '예: BICU1234567', disabled: (row) => !!row,
    pattern: /^[A-Za-z]{4}\d{7}$/, patternMessage: '영문 4자리 + 숫자 7자리로 입력해주세요. (예: BICU1234567)',
  },
  { key: 'companyId', label: '예약 업체(선택)', type: 'select', options: () => companyOptions.value, placeholder: '미지정' },
  {
    key: 'isoSizeTypeCode', label: 'ISO 규격·종류 코드', required: true, placeholder: '예: 45G1',
    pattern: /^[0-9A-Za-z]{4}$/, patternMessage: '영문/숫자 4자리로 입력해주세요. (예: 22G1, 45G1)',
    hint: '두 번째 자리가 5면 높이 9ft 6in 하이큐브입니다. (예: 45G1)',
  },
  { key: 'containerType', label: '컨테이너 타입', required: true, placeholder: '일반/냉동 등' },
  { key: 'isHighCube', label: '하이큐브 여부', type: 'checkbox', checkboxLabel: '하이큐브 컨테이너입니다' },
  {
    key: 'maxGrossKg', label: 'MAX GROSS (kg)', type: 'number', step: '0.1', min: 0, required: true,
    validate: (v) => (Number(v) <= 0 ? '0보다 큰 값을 입력해주세요.' : ''),
  },
  {
    key: 'tareKg', label: 'TARE (kg)', type: 'number', step: '0.1', min: 0, required: true,
    validate: (v, f) => {
      if (Number(v) <= 0) return '0보다 큰 값을 입력해주세요.'
      if (f.maxGrossKg !== '' && Number(v) >= Number(f.maxGrossKg)) return 'TARE 는 MAX GROSS 보다 작아야 합니다.'
      return ''
    },
  },
  {
    key: 'netKg', label: 'NET (kg)', type: 'number', step: '0.1', min: 0, required: true,
    hint: 'MAX GROSS는 TARE와 NET을 더한 값보다 크거나 같아야 합니다.',
    validate: (v, f) => {
      if (Number(v) <= 0) return '0보다 큰 값을 입력해주세요.'
      const max = Number(f.maxGrossKg)
      const tare = Number(f.tareKg)
      const net = Number(v)
      if ([max, tare, net].some((n) => Number.isNaN(n))) return ''
      if (Math.round(max * 10) < Math.round(tare * 10) + Math.round(net * 10)) {
        return 'MAX GROSS는 TARE와 NET을 더한 값보다 크거나 같아야 합니다.'
      }
      return ''
    },
  },
  {
    key: 'cubicCapacityCbm', label: '내부 용적 (CBM)', type: 'number', step: '0.01', min: 0, required: true,
    validate: (v) => (Number(v) <= 0 ? '0보다 큰 값을 입력해주세요.' : ''),
  },
  { key: 'cscApprovalNo', label: 'CSC 승인번호' },
  { key: 'loadingLocationId', label: '적재 위치(선택)', type: 'select', options: () => locationOptions.value, placeholder: '미지정' },
  {
    key: 'reservedCargoInfo', label: '화물 상세 정보(JSON, 선택)', type: 'textarea',
    placeholder: '{"item":"전자제품","qty":20}',
    validate: (v) => {
      try { JSON.parse(v); return '' } catch { return 'JSON 형식이 아닙니다. 비워두거나 {"키":"값"} 형태로 입력해주세요.' }
    },
  },
]

async function loadCompanyOptions() {
  try {
    const res = await adminApi.get('/api/companies/options')
    companyOptions.value = res.data.map((c) => ({ value: c.companyId, label: c.companyName }))
  } catch (err) {
    console.error('업체 옵션 로드 실패:', err)
  }
}

async function loadLocations() {
  try {
    const res = await adminApi.get('/api/loading-locations')
    locations.value = normalizeRows(res.data, { bools: ['isAvailable'], idKey: 'locationId' })
  } catch (err) {
    console.error('적재 위치 정보 로드 실패:', err)
  }
}

async function loadRows() {
  loading.value = true
  try {
    const res = await adminApi.get('/api/containers')
    rows.value = normalizeRows(res.data, { bools: ['isHighCube'], idKey: 'containerNo' }).map((row) => ({
      ...row,
      reservedCargoInfo:
        row.reservedCargoInfo && typeof row.reservedCargoInfo === 'object'
          ? JSON.stringify(row.reservedCargoInfo)
          : (row.reservedCargoInfo ?? ''),
    }))
  } catch (err) {
    alert(pickErrorMessage(err, '컨테이너 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

// 26.09.30 수정: 신규 등록/수정이 안 되던 원인
//  - 비어 있는 JSON 칸('')이 그대로 전송되어 DB JSON 컬럼에서 오류
//  - 하이큐브 체크값이 백엔드 필드명(highCube)과 달라 무시됨
//  → 빈 값은 null, boolean 은 두 이름 모두 전송, 컨테이너 번호는 대문자로 통일
function roundNum(v, digits) {
  const n = toNum(v)
  if (!Number.isFinite(n)) return null
  const f = 10 ** digits
  return Math.round(n * f) / f
}

function cargoText(v) {
  if (v === null || v === undefined || v === '') return null
  return typeof v === 'string' ? v : JSON.stringify(v)
}

function buildPayload(payload) {
  return withBoolAliases(
    {
      ...payload,
      containerNo: payload.containerNo ? String(payload.containerNo).toUpperCase() : payload.containerNo,
      isoSizeTypeCode: payload.isoSizeTypeCode ? String(payload.isoSizeTypeCode).toUpperCase() : payload.isoSizeTypeCode,
      companyId: toNum(payload.companyId),
      maxGrossKg: roundNum(payload.maxGrossKg, 1),
      tareKg: roundNum(payload.tareKg, 1),
      netKg: roundNum(payload.netKg, 1),
      cubicCapacityCbm: roundNum(payload.cubicCapacityCbm, 2),
      loadingLocationId: toNum(payload.loadingLocationId),
      reservedCargoInfo: cargoText(payload.reservedCargoInfo),
    },
    ['isHighCube']
  )
}

async function handleCreate(payload) {
  await adminApi.post('/api/containers', buildPayload(payload))
  await loadRows()
}

async function handleUpdate(id, payload) {
  await updateWithFallback(adminApi, `/api/containers/${seg(id)}`, buildPayload({ ...payload, containerNo: id }), 'put')
  await loadRows()
}

async function handleDelete(id) {
  try {
    await adminApi.delete(`/api/containers/${seg(id)}`)
    await loadRows()
  } catch (err) {
    alert(`컨테이너 삭제에 실패했습니다.
${deleteError(err, '컨테이너')}`)
  }
}

onMounted(async () => {
  await Promise.all([loadCompanyOptions(), loadLocations(), loadRows()])
})
</script>
