<template>
  <div>
    <AdminPageHeader title="업체 관리" description="화주/운송사 업체 정보를 등록, 수정, 삭제합니다." />

    <CrudTable
      title="업체"
      id-key="companyId"
      :columns="columns"
      :rows="rows"
      :loading="loading"
      :form-fields="formFields"
      :page-size="10"
      :row-label="(r) => r.companyName"
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
import { friendlyError, updateWithFallback, deleteError, normalizeRows } from '@/utils/apiHelpers'
import { BUSINESS_NO_PATTERN, BUSINESS_NO_MESSAGE, normalizeBusinessNo } from '@/utils/validators'

const rows = ref([])
const loading = ref(true)

const columns = [
  { key: 'companyId', label: 'ID', width: '60px', align: 'center' },
  { key: 'companyName', label: '업체명' },
  { key: 'representativeName', label: '대표자' },
  { key: 'industryType', label: '업종' },
  { key: 'businessNo', label: '사업자번호' },
  { key: 'address', label: '주소' },
  { key: 'createdAt', label: '등록일', type: 'date' },
]

const formFields = [
  { key: 'companyName', label: '업체명', required: true, placeholder: '(주)스카고로지스틱스' },
  { key: 'address', label: '주소', required: true, placeholder: '업체 주소를 입력하세요' },
  { key: 'businessNo', label: '사업자 등록번호', placeholder: '123-45-67890', pattern: BUSINESS_NO_PATTERN, patternMessage: BUSINESS_NO_MESSAGE },
  { key: 'industryType', label: '업종', placeholder: '컨테이너 운송업' },
  { key: 'representativeName', label: '대표자명', placeholder: '홍길동' },
]

async function loadRows() {
  loading.value = true
  try {
    // 26.09.30 수정: 백엔드 CompanyResponse 에 companyId 가 포함되어 있어 N+1 조회 없이 목록 API 하나로 조회
    const res = await adminApi.get('/api/companies')
    rows.value = normalizeRows(res.data, { idKey: 'companyId' })
  } catch (err) {
    alert(pickErrorMessage(err, '업체 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

// 26.09.30 수정: 신규 등록 실패 시 서버 원문(JSON/SQL) 대신 원인별 안내 문구 표시
function companyError(err) {
  const status = err?.response?.status
  const raw = JSON.stringify(err?.response?.data || '')
  if (status === 409 || /duplicate|unique|이미/i.test(raw)) {
    return '이미 등록된 업체이거나 사업자등록번호가 중복됩니다. 기존 목록을 확인해주세요.'
  }
  if (status === 403) return '업체를 등록할 권한이 없습니다. 관리자 계정으로 다시 로그인해주세요.'
  if (status === 400) {
    const msg = friendlyError(err, '')
    return msg && msg !== '입력값을 다시 확인해주세요.'
      ? msg
      : '필수 항목(업체명, 주소)과 사업자등록번호 형식(123-45-67890)을 확인해주세요.'
  }
  return friendlyError(err, '업체 정보를 저장하지 못했습니다. 잠시 후 다시 시도해주세요.')
}

function buildPayload(payload) {
  return { ...payload, businessNo: normalizeBusinessNo(payload.businessNo) }
}

async function handleCreate(payload) {
  try {
    await adminApi.post('/api/companies', buildPayload(payload))
  } catch (err) {
    throw new Error(companyError(err))
  }
  await loadRows()
}

async function handleUpdate(id, payload) {
  try {
    await updateWithFallback(adminApi, `/api/companies/${id}`, buildPayload(payload), 'put')
  } catch (err) {
    throw new Error(companyError(err))
  }
  await loadRows()
}

async function handleDelete(id) {
  try {
    await adminApi.delete(`/api/companies/${id}`)
    await loadRows()
  } catch (err) {
    alert(`업체 삭제에 실패했습니다.
${deleteError(err, '업체')}`)
  }
}

onMounted(loadRows)
</script>
