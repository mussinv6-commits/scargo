<template>
  <div>
    <AdminPageHeader
      title="과적 검사 관리"
      description="축중기 등 계측 장비에서 수집된 과적 검사 기록을 조회하고, 잘못 입력된 기록을 정정합니다."
    />

    <CrudTable
      title="과적 검사"
      id-key="checkId"
      :columns="columns"
      :rows="rows"
      :loading="loading"
      :form-fields="formFields"
      :page-size="10"
      :row-label="(r) => `#${r.checkId} ${r.vehicleNo || ''}`"
      :on-create="handleCreate"
      :on-update="handleUpdate"
      :on-delete="handleDelete"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import CrudTable from '@/components/admin/CrudTable.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'
import { normalizeRows, withBoolAliases, toNum, updateWithFallback , deleteError } from '@/utils/apiHelpers'
import { VEHICLE_NO_PATTERN, VEHICLE_NO_MESSAGE } from '@/utils/validators'

const rows = ref([])
const loading = ref(true)

function fmtNum(v) {
  return v === null || v === undefined || v === '' ? '-' : Number(v).toLocaleString('ko-KR')
}

const columns = [
  { key: 'checkId', label: 'ID', width: '60px', align: 'center', type: 'seq' },
  { key: 'vehicleNo', label: '차량번호' },
  { key: 'containerNo', label: '컨테이너번호' },
  { key: 'totalWeight', label: '총중량(kg)', align: 'right', format: fmtNum },
  { key: 'maxPayload', label: '최대적재량(kg)', align: 'right', format: fmtNum },
  // 26.09.30 수정: 위반여부 색이 반대로(위반=초록) 나오던 문제 → invert 로 [정상: 초록 / 위반: 빨강]
  { key: 'isViolation', label: '위반여부', type: 'boolean', align: 'center', invert: true, trueLabel: '위반', falseLabel: '정상' },
  { key: 'isPassed', label: '최종통과', type: 'boolean', align: 'center', trueLabel: '통과', falseLabel: '미통과' },
  { key: 'retryCount', label: '재검증', align: 'center', format: (v) => `${v ?? 0}회` },
  { key: 'checkedAt', label: '검사일시', type: 'date' },
]

// 백엔드 DTO 가 Integer(kg) 이므로 정수만 입력. 수정 API(OverloadCheckUpdateRequest)는 계측값을 받지 않아
// 수정 모드에서는 계측값 입력칸을 잠그고 안내한다 (오기 정정은 차량/컨테이너 번호·판정 결과만 가능)
const LOCKED_HINT = '계측값은 수정할 수 없습니다. (장비 측정값 보존)'
const weightField = (key, label, extra = {}) => ({
  key, label, type: 'number', min: 0, step: '1',
  disabled: (row) => !!row,
  validate: (v) => (Number.isInteger(Number(v)) ? '' : '정수(kg)로 입력해주세요.'),
  ...extra,
})

const formFields = [
  { key: 'vehicleNo', label: '화물차 번호', required: true, placeholder: '예: 12가3456', pattern: VEHICLE_NO_PATTERN, patternMessage: VEHICLE_NO_MESSAGE },
  {
    key: 'containerNo', label: '컨테이너 번호(선택)', placeholder: '예: BICU1234567',
    pattern: /^[A-Za-z]{4}\d{7}$/, patternMessage: '영문 4자리 + 숫자 7자리로 입력해주세요.',
  },
  { key: 'usagePurpose', label: '차량 용도/목적', placeholder: '예: 수출 컨테이너 운송' },
  weightField('emptyVehicleWeight', '공차중량(kg)'),
  weightField('totalWeight', '총중량(kg)', { required: true }),
  weightField('maxPayload', '최대적재량(kg)'),
  weightField('vgmWeight', 'VGM 총중량(kg)'),
  { key: 'tireCount', label: '타이어수', type: 'number', min: 0, step: '1', disabled: (row) => !!row },
  { key: 'vehicleAxleCount', label: '차축수', type: 'number', min: 1, step: '1', disabled: (row) => !!row, hint: LOCKED_HINT },
  {
    key: 'isViolation', label: '규정 위반 여부', type: 'select', required: true, default: 'false',
    options: () => [{ value: 'false', label: '정상' }, { value: 'true', label: '위반' }],
  },
  {
    key: 'violationReason', label: '위반 사유', type: 'textarea', placeholder: '예: 축하중 10톤 초과',
    showIf: (f) => f.isViolation === 'true' || f.isViolation === true,
  },
  { key: 'retryCount', label: '재검증 시도 횟수', type: 'number', min: 0, step: '1', default: 0 },
  {
    key: 'isPassed', label: '최종 통과 여부', type: 'select', required: true, default: 'true',
    options: () => [{ value: 'true', label: '통과' }, { value: 'false', label: '미통과' }],
  },
]

async function loadRows() {
  loading.value = true
  try {
    const res = await adminApi.get('/api/overload-checks', { params: { page: 0, size: 500 } })
    rows.value = normalizeRows(res.data, { bools: ['isViolation', 'isPassed'], idKey: 'checkId' })
  } catch (err) {
    alert(pickErrorMessage(err, '과적 검사 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

function buildPayload(payload) {
  const isViolation = payload.isViolation === true || payload.isViolation === 'true'
  return withBoolAliases(
    {
      ...payload,
      containerNo: payload.containerNo ? String(payload.containerNo).toUpperCase() : null,
      emptyVehicleWeight: toNum(payload.emptyVehicleWeight),
      totalWeight: toNum(payload.totalWeight),
      maxPayload: toNum(payload.maxPayload),
      vgmWeight: toNum(payload.vgmWeight),
      tireCount: toNum(payload.tireCount),
      vehicleAxleCount: toNum(payload.vehicleAxleCount),
      axleCount: toNum(payload.vehicleAxleCount),
      retryCount: toNum(payload.retryCount) ?? 0,
      isViolation,
      violationReason: isViolation ? payload.violationReason || null : null,
    },
    ['isViolation', 'isPassed']
  )
}

async function handleCreate(payload) {
  await adminApi.post('/api/overload-checks', buildPayload(payload))
  await loadRows()
}

async function handleUpdate(id, payload) {
  await updateWithFallback(adminApi, `/api/overload-checks/${id}`, buildPayload(payload), 'put')
  await loadRows()
}

async function handleDelete(id) {
  try {
    await adminApi.delete(`/api/overload-checks/${id}`)
    await loadRows()
  } catch (err) {
    alert(`과적 검사 기록 삭제에 실패했습니다.
${deleteError(err, '과적 검사 기록')}`)
  }
}

onMounted(loadRows)
</script>
