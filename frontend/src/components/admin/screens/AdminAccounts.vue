<template>
  <div>
    <AdminPageHeader title="회원 관리" description="전체 회원을 조회합니다. 관리자를 제외한 회원은 수정·삭제할 수 있고, 승인 대기 기업회원은 승인하거나 거절합니다." />

    <div class="crud-table-wrap">
      <div class="crud-toolbar">
        <div class="crud-toolbar-left">
          <h2 class="crud-title">회원 목록</h2>
          <span v-if="!loading" class="crud-count">총 {{ filteredAccounts.length }}건</span>
        </div>
        <div class="crud-toolbar-right">
          <select class="crud-input" style="width:auto;" v-model="filter">
            <option value="ALL">전체</option>
            <option value="CORPORATE_PENDING">기업회원(승인대기)</option>
            <option value="CORPORATE_APPROVED">기업회원(승인완료)</option>
            <option value="GENERAL">일반회원(기사)</option>
            <option value="ADMIN">관리자</option>
          </select>
        </div>
      </div>

      <div class="crud-table-scroll">
        <table class="crud-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>아이디</th>
              <th>이름</th>
              <th>회원유형</th>
              <th>소속업체</th>
              <th>연락처</th>
              <th>가입일</th>
              <th class="is-center" style="width:1%;">관리</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="8" class="crud-empty">불러오는 중...</td></tr>
            <tr v-else-if="error"><td colspan="8" class="crud-empty">{{ error }}</td></tr>
            <tr v-else-if="filteredAccounts.length === 0"><td colspan="8" class="crud-empty">해당하는 회원이 없습니다.</td></tr>
            <tr v-for="a in pagedAccounts" :key="a.accountId">
              <td>{{ a.accountId }}</td>
              <td>{{ a.userId }}</td>
              <td>{{ a.userName || '-' }}</td>
              <td><span class="pill" :class="typeTone(a.userType)">{{ typeLabel(a.userType) }}</span></td>
              <td>{{ companyName(a.companyId) }}</td>
              <td>{{ a.phoneNum || '-' }}</td>
              <td>{{ formatDate(a.createdAt) }}</td>
              <td class="is-center">
                <div v-if="a.userType !== 'ADMIN'" class="crud-actions">
                  <template v-if="a.userType === 'CORPORATE_PENDING'">
                    <button
                      class="btn-admin btn-admin-accent"
                      :disabled="approvingId === a.accountId || rejectingId === a.accountId"
                      @click="approve(a)"
                    >
                      {{ approvingId === a.accountId ? '처리 중...' : '승인' }}
                    </button>
                    <button
                      class="btn-admin btn-admin-danger"
                      :disabled="approvingId === a.accountId || rejectingId === a.accountId"
                      @click="reject(a)"
                    >
                      {{ rejectingId === a.accountId ? '처리 중...' : '거절' }}
                    </button>
                  </template>
                  <button class="btn-admin btn-admin-ghost" @click="openEdit(a)">수정</button>
                  <button
                    v-if="a.userType !== 'CORPORATE_PENDING'"
                    class="btn-admin btn-admin-danger"
                    :disabled="deletingId === a.accountId"
                    @click="removeAccount(a)"
                  >
                    {{ deletingId === a.accountId ? '처리 중...' : '삭제' }}
                  </button>
                </div>
                <span v-else style="color: var(--a-text-muted); font-size:12.5px;">-</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <AdminPager v-model="page" :page-count="pageCount" />
    </div>

    <div v-if="editing" class="crud-modal-backdrop" @click.self="closeEdit">
      <div class="crud-modal" role="dialog" aria-modal="true">
        <div class="crud-modal-header">
          <h3>회원 수정</h3>
          <button type="button" class="crud-modal-close" aria-label="닫기" @click="closeEdit">×</button>
        </div>
        <form class="crud-modal-body" @submit.prevent="saveEdit">
          <div class="crud-field">
            <label>아이디</label>
            <input class="crud-input" :value="editing.userId" disabled />
          </div>
          <div class="crud-field">
            <label>이름<span class="req">*</span></label>
            <input class="crud-input" v-model="editForm.userName" />
          </div>
          <div class="crud-field">
            <label>연락처</label>
            <input class="crud-input" v-model="editForm.phoneNum" placeholder="010-0000-0000" />
          </div>
          <div class="crud-field">
            <label>비밀번호</label>
            <input class="crud-input" type="password" v-model="editForm.userPw" placeholder="변경할 때만 입력" autocomplete="new-password" />
            <p class="crud-hint">비워두면 기존 비밀번호를 유지합니다.</p>
          </div>
          <div v-if="editError" class="crud-form-error">{{ editError }}</div>
          <div class="crud-modal-footer">
            <button type="button" class="btn-admin btn-admin-ghost" @click="closeEdit">취소</button>
            <button type="submit" class="btn-admin btn-admin-primary" :disabled="saving">
              {{ saving ? '처리 중...' : '수정 저장' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import AdminPager from '@/components/admin/AdminPager.vue'
import { computed, onMounted, ref, watch } from 'vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'

const accounts = ref([])
const loading = ref(true)
const error = ref('')
const filter = ref('ALL')
const approvingId = ref(null)
const rejectingId = ref(null) // 26.09.21 추가
const deletingId = ref(null)
const editing = ref(null)
const saving = ref(false)
const editError = ref('')
const editForm = ref({ userName: '', phoneNum: '', userPw: '' })
const companyMap = ref({})

function companyName(id) {
  if (id === null || id === undefined) return '-'
  return companyMap.value[id] || `#${id}`
}
function typeTone(t) {
  return { CORPORATE_PENDING: 'pill-warn', ADMIN: 'pill-muted' }[t] || 'pill-on'
}

const PAGE_SIZE = 10
const page = ref(1)

const filteredAccounts = computed(() => {
  if (filter.value === 'ALL') return accounts.value
  return accounts.value.filter((a) => a.userType === filter.value)
})
const pageCount = computed(() => Math.max(1, Math.ceil(filteredAccounts.value.length / PAGE_SIZE) || 1))
const pagedAccounts = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return filteredAccounts.value.slice(start, start + PAGE_SIZE)
})
watch(filter, () => { page.value = 1 })
watch(pageCount, (n) => { if (page.value > n) page.value = n })

function typeLabel(t) {
  return {
    ADMIN: '관리자',
    GENERAL: '일반회원(기사)',
    CORPORATE_PENDING: '기업회원(승인대기)',
    CORPORATE_APPROVED: '기업회원(승인완료)',
  }[t] || t
}

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('ko-KR') : '-'
}

async function fetchAccounts() {
  loading.value = true
  error.value = ''
  try {
    const res = await adminApi.get('/api/accounts')
    accounts.value = res.data
  } catch (err) {
    error.value = pickErrorMessage(err, '회원 목록을 불러오지 못했습니다. (관리자 권한 필요)')
  } finally {
    loading.value = false
  }
}

async function approve(account) {
  if (!confirm(`[${account.userId}] 계정을 기업회원으로 승인하시겠습니까?`)) return
  approvingId.value = account.accountId
  try {
    await adminApi.patch(`/api/accounts/${account.accountId}/approve`)
    account.userType = 'CORPORATE_APPROVED'
  } catch (err) {
    alert(pickErrorMessage(err, '승인 처리 중 오류가 발생했습니다.'))
  } finally {
    approvingId.value = null
  }
}

// 26.09.21 추가: 기업회원 가입 거절 (승인 대기 신청을 삭제 처리)
function openEdit(account) {
  editing.value = account
  editError.value = ''
  editForm.value = {
    userName: account.userName || '',
    phoneNum: account.phoneNum || '',
    userPw: '',
  }
}

function closeEdit() {
  editing.value = null
  saving.value = false
  editError.value = ''
}

async function saveEdit() {
  const name = editForm.value.userName.trim()
  if (!name) {
    editError.value = '이름을 입력해주세요.'
    return
  }
  saving.value = true
  editError.value = ''
  try {
    const res = await adminApi.put(`/api/accounts/${editing.value.accountId}`, {
      userName: name,
      phoneNum: editForm.value.phoneNum.trim(),
      userPw: editForm.value.userPw.trim() || null,
    })
    const updated = res.data
    const row = accounts.value.find((a) => a.accountId === editing.value.accountId)
    if (row) {
      row.userName = updated.userName
      row.phoneNum = updated.phoneNum
    }
    closeEdit()
  } catch (err) {
    editError.value = pickErrorMessage(err, '회원 수정 중 오류가 발생했습니다.')
    saving.value = false
  }
}

async function removeAccount(account) {
  if (!confirm(`[${account.userId}] 회원을 삭제하시겠습니까?\n삭제한 계정은 되돌릴 수 없습니다.`)) return
  deletingId.value = account.accountId
  try {
    await adminApi.delete(`/api/accounts/${account.accountId}`)
    accounts.value = accounts.value.filter((a) => a.accountId !== account.accountId)
  } catch (err) {
    alert(pickErrorMessage(err, '회원 삭제 중 오류가 발생했습니다.'))
  } finally {
    deletingId.value = null
  }
}

async function reject(account) {
  if (!confirm(`[${account.userId}] 계정의 기업회원 가입을 거절하시겠습니까?\n거절하면 해당 가입 신청 건이 삭제됩니다.`)) return
  rejectingId.value = account.accountId
  try {
    await adminApi.patch(`/api/accounts/${account.accountId}/reject`)
    accounts.value = accounts.value.filter((a) => a.accountId !== account.accountId)
  } catch (err) {
    alert(pickErrorMessage(err, '거절 처리 중 오류가 발생했습니다.'))
  } finally {
    rejectingId.value = null
  }
}

onMounted(async () => {
  fetchAccounts()
  try {
    const res = await adminApi.get('/api/companies/options')
    companyMap.value = Object.fromEntries(res.data.map((c) => [c.companyId, c.companyName]))
  } catch (e) {
    console.error(e)
  }
})
</script>