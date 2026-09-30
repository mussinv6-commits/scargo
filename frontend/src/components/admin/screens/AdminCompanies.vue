<template>
  <div>
    <div class="admin-page-header">
      <h1>업체 관리</h1>
      <p>화주/운송사 업체 정보를 등록, 수정, 삭제합니다.</p>
    </div>

    <CrudTable
      title="업체"
      id-key="companyId"
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
  { key: 'companyId', label: 'ID', width: '60px' },
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
  { key: 'businessNo', label: '사업자 등록번호', placeholder: '000-00-00000' },
  { key: 'industryType', label: '업종', placeholder: '컨테이너 운송업' },
  { key: 'representativeName', label: '대표자명', placeholder: '홍길동' },
]

async function loadRows() {
  loading.value = true
  try {
    const optionsRes = await adminApi.get('/api/companies/options')
    const options = optionsRes.data // { companyId, companyName, address }

    // CompanyResponse(목록 API)에는 companyId가 빠져있어 매칭이 불가능하므로,
    // options 목록을 기준으로 각 업체의 상세 정보를 병렬로 채워넣는다.
    const details = await Promise.all(
      options.map((o) =>
        adminApi
          .get(`/api/companies/${o.companyId}`)
          .then((r) => ({ companyId: o.companyId, ...r.data }))
          .catch(() => ({ companyId: o.companyId, companyName: o.companyName, address: o.address }))
      )
    )
    rows.value = details
  } catch (err) {
    alert(pickErrorMessage(err, '업체 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

async function handleCreate(payload) {
  await adminApi.post('/api/companies', payload)
  await loadRows()
}

async function handleUpdate(id, payload) {
  await adminApi.put(`/api/companies/${id}`, payload)
  await loadRows()
}

async function handleDelete(id) {
  try {
    await adminApi.delete(`/api/companies/${id}`)
    await loadRows()
  } catch (err) {
    alert(pickErrorMessage(err, '삭제 중 오류가 발생했습니다.'))
  }
}

onMounted(loadRows)
</script>
