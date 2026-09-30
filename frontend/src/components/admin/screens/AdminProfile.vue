<template>
  <div>
    <div class="admin-page-header">
      <h1>내 정보</h1>
      <p>관리자 계정 정보입니다.</p>
    </div>

    <div class="crud-table-wrap" style="max-width: 480px;">
      <div v-if="loading" class="crud-empty">불러오는 중...</div>
      <div v-else-if="error" class="crud-empty">{{ error }}</div>
      <dl v-else class="profile-info">
        <div class="profile-row"><dt>이름</dt><dd>{{ profile.userName || '-' }}</dd></div>
        <div class="profile-row"><dt>아이디</dt><dd>{{ profile.userId }}</dd></div>
        <div class="profile-row"><dt>권한</dt><dd><span class="pill pill-on">관리자</span></dd></div>
        <div class="profile-row"><dt>연락처</dt><dd>{{ profile.phoneNum || '-' }}</dd></div>
        <div class="profile-row"><dt>가입일</dt><dd>{{ formatDate(profile.createdAt) }}</dd></div>
      </dl>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { authState } from '@/auth/authState.js'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'

const loading = ref(true)
const error = ref('')
const profile = reactive({})

function formatDate(d) {
  return d ? new Date(d).toLocaleString('ko-KR') : '-'
}

onMounted(async () => {
  const id = authState.user?.accountId
  if (!id) {
    error.value = '로그인 정보를 확인할 수 없습니다.'
    loading.value = false
    return
  }
  try {
    const res = await adminApi.get(`/api/accounts/${id}`)
    Object.assign(profile, res.data)
  } catch (err) {
    error.value = pickErrorMessage(err, '내 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.profile-info { margin: 0; }
.profile-row {
  display: flex;
  justify-content: space-between;
  padding: 12px 4px;
  border-bottom: 1px solid var(--a-border, #e3e8ef);
  font-size: 14px;
}
.profile-row:last-child { border-bottom: none; }
.profile-row dt { color: var(--a-text-muted, #64748b); font-weight: 600; margin: 0; }
.profile-row dd { color: var(--a-text, #0a2540); margin: 0; font-weight: 600; }
</style>