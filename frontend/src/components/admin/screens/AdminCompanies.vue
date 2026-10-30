<template>
  <div>
    <AdminPageHeader title="업체 관리" description="화주/운송사 업체 정보를 등록, 수정, 삭제합니다." />

    <!-- fix_1330: 회원가입 화면에서 새로 등록된 업체는 승인 대기. 승인 전에는 업체 선택 목록에 나오지 않음 -->
    <div v-if="pending.length" class="pend-card">
      <h3>승인 대기 업체 <span class="pend-meta">{{ pending.length }}건 · 승인해야 가입·선택 목록에서 쓸 수 있습니다</span></h3>
      <table class="pend-table">
        <thead><tr><th>번호</th><th>업체명</th><th>대표자</th><th>사업자번호</th><th>주소</th><th>신청일</th><th>처리</th></tr></thead>
        <tbody><tr v-for="(c, i) in pending" :key="c.companyId">
          <td>{{ i + 1 }}</td><td>{{ c.companyName }}</td><td>{{ c.representativeName || '—' }}</td><td>{{ c.businessNo || '—' }}</td>
          <td class="is-left">{{ c.address }}</td><td>{{ ymd(c.createdAt) }}</td>
          <td>
            <button class="btn-act edit" :disabled="busy === c.companyId" @click="approveCo(c)">승인</button>
            <input v-model="reasons[c.companyId]" placeholder="반려 사유" class="pend-input" />
            <button class="btn-act del" :disabled="busy === c.companyId || !(reasons[c.companyId] || '').trim()" @click="rejectCo(c)">반려</button>
          </td>
        </tr></tbody>
      </table>
      <p v-if="pmsg" :class="pok ? 'pend-ok' : 'pend-err'">{{ pmsg }}</p>
    </div>

    <CrudTable
      title="업체"
      id-key="companyId"
      :columns="columns"
      :rows="visibleRows"
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
import { computed, onMounted, reactive, ref } from 'vue'
import { rowsForViewDate } from '@/components/admin/adminViewDate.js'
import CrudTable from '@/components/admin/CrudTable.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'
import { friendlyError, updateWithFallback, deleteError, normalizeRows } from '@/utils/apiHelpers'
import { BUSINESS_NO_PATTERN, BUSINESS_NO_MESSAGE, normalizeBusinessNo } from '@/utils/validators'

const rows = ref([])
// fix_1330: 승인 대기 업체 승인·반려 (PATCH /api/companies/{id}/approve|reject)
const pending = computed(() => rows.value.filter((r) => r.approvalStatus === 'PENDING'))
// fix_1330: 서버가 등록일을 초 단위 숫자로 보내 1970 년으로 보이던 문제 → ISO 문자열로 바꿔 표시
const toIso = (v) => (typeof v === 'number' ? new Date(v < 1e12 ? v * 1000 : v).toISOString() : v)
const ymd = (v) => { const d = new Date(toIso(v)); return isNaN(d) ? '—' : d.toLocaleDateString('ko-KR') }
const reasons = reactive({}), busy = ref(null), pmsg = ref(''), pok = ref(true)
async function approveCo(c) {
  busy.value = c.companyId
  try { await adminApi.patch(`/api/companies/${c.companyId}/approve`); pok.value = true; pmsg.value = `${c.companyName} 승인됨`; await loadRows() }
  catch (err) { pok.value = false; pmsg.value = pickErrorMessage(err, '승인하지 못했습니다.') } finally { busy.value = null }
}
async function rejectCo(c) {
  busy.value = c.companyId
  try { await adminApi.patch(`/api/companies/${c.companyId}/reject`, { reason: reasons[c.companyId].trim() }); pok.value = true; pmsg.value = `${c.companyName} 반려됨`; await loadRows() }
  catch (err) { pok.value = false; pmsg.value = pickErrorMessage(err, '반려하지 못했습니다.') } finally { busy.value = null }
}
const loading = ref(true)
const visibleRows = computed(() => rowsForViewDate(rows.value, 'createdAt'))

const columns = [
  { key: 'companyId', label: '번호', width: '60px', align: 'center', type: 'seq' },
  { key: 'companyName', label: '업체명' },
  { key: 'representativeName', label: '대표자' },
  { key: 'industryType', label: '업종' },
  { key: 'businessNo', label: '사업자번호' },
  { key: 'address', label: '주소', align: 'left' },
  { key: 'approvalLabel', label: '승인' },
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
    rows.value = normalizeRows(res.data, { idKey: 'companyId' }).map((r) => ({ ...r, createdAt: toIso(r.createdAt), approvalLabel: { PENDING: '승인 대기', APPROVED: '승인', REJECTED: '반려' }[r.approvalStatus] || '—' }))
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

<style scoped>
.pend-card{background:#fff;border:1px solid #EECC83;border-radius:10px;padding:14px 16px;margin-bottom:16px}
.pend-card h3{margin:0 0 10px;color:#102644;font-size:16px}
.pend-meta{font-size:12px;color:#888;font-weight:400}
.pend-table{width:100%;border-collapse:collapse;font-size:13px}
.pend-table th,.pend-table td{border-bottom:1px solid #eee;padding:7px 6px;text-align:center}
.pend-table th{background:#f6f7f9;color:#102644}
.pend-input{height:34px;width:140px;max-width:240px;border:1px solid #ccc;border-radius:6px;padding:0 8px;margin:0 4px}
.pend-btn{height:34px;padding:0 12px;border-radius:6px;border:1px solid #102644;background:#fff;color:#102644;cursor:pointer}
.pend-btn.ok{background:#102644;color:#fff}
.pend-btn:disabled{opacity:.5;cursor:default}
.pend-ok{color:#1b7f3b;margin:8px 0 0}.pend-err{color:#c62828;margin:8px 0 0}
</style>
