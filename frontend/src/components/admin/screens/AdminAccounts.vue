<template>
  <div>
    <div class="admin-page-header">
      <h1>회원 관리</h1>
      <p>전체 회원 목록을 조회하고, 승인 대기 중인 기업회원을 승인할 수 있습니다.</p>
    </div>

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
              <th>소속업체ID</th>
              <th>연락처</th>
              <th>가입일</th>
              <th>관리</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="8" class="crud-empty">불러오는 중...</td></tr>
            <tr v-else-if="error"><td colspan="8" class="crud-empty">{{ error }}</td></tr>
            <tr v-else-if="filteredAccounts.length === 0"><td colspan="8" class="crud-empty">해당하는 회원이 없습니다.</td></tr>
            <tr v-for="a in filteredAccounts" :key="a.accountId">
              <td>{{ a.accountId }}</td>
              <td>{{ a.userId }}</td>
              <td>{{ a.userName || '-' }}</td>
              <td><span class="pill" :class="a.userType === 'CORPORATE_PENDING' ? 'pill-off' : 'pill-on'">{{ typeLabel(a.userType) }}</span></td>
              <td>{{ a.companyId ?? '-' }}</td>
              <td>{{ a.phoneNum || '-' }}</td>
              <td>{{ formatDate(a.createdAt) }}</td>
              <td>
                <div v-if="a.userType === 'CORPORATE_PENDING'" style="display:flex; gap:6px;">
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
                </div>
                <span v-else style="color: var(--a-text-muted); font-size:12.5px;">-</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'

const accounts = ref([])
const loading = ref(true)
const error = ref('')
const filter = ref('ALL')
const approvingId = ref(null)
const rejectingId = ref(null) // 26.09.21 추가

const filteredAccounts = computed(() => {
  if (filter.value === 'ALL') return accounts.value
  return accounts.value.filter((a) => a.userType === filter.value)
})

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

onMounted(fetchAccounts)
</script>