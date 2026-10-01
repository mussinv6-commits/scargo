<template>
  <div class="profile-page">
    <AdminPageHeader title="내 정보" description="로그인한 관리자 계정 정보와 자주 쓰는 관리 메뉴입니다." />

    <div class="profile-grid">
      <section class="profile-card">
        <div v-if="loading" class="crud-empty">불러오는 중...</div>
        <div v-else-if="error" class="crud-empty">{{ error }}</div>
        <template v-else>
          <div class="profile-head">
            <span class="profile-avatar">{{ (profile.userName || 'A')[0] }}</span>
            <div>
              <div class="profile-name">{{ profile.userName || '-' }}</div>
              <span class="pill pill-on">관리자</span>
            </div>
          </div>
          <dl class="profile-info">
            <dt>아이디</dt><dd>{{ profile.userId || '-' }}</dd>
            <dt>이름</dt><dd>{{ profile.userName || '-' }}</dd>
            <dt>연락처</dt><dd>{{ profile.phoneNum || '-' }}</dd>
            <dt>회원 번호</dt><dd>{{ profile.accountId ?? '-' }}</dd>
            <dt>가입일</dt><dd>{{ formatDate(profile.createdAt) }}</dd>
          </dl>
        </template>
      </section>

      <div class="profile-side">
        <section class="profile-card">
          <h2 class="profile-side-title">계정 권한</h2>
          <ul class="profile-note">
            <li>회원·업체 승인 및 계정 관리</li>
            <li>차량 진입 허가 심사</li>
            <li>야드·적재 위치·과적 검사 운영</li>
          </ul>
        </section>

        <section class="profile-card">
          <h2 class="profile-side-title">바로가기</h2>
          <div class="shortcut-grid">
            <RouterLink v-for="m in shortcuts" :key="m.to" :to="m.to">
              <i :class="['bi', m.icon]"></i> {{ m.label }}
            </RouterLink>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { authState } from '@/auth/authState.js'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'

const shortcuts = [
  { to: '/admin', label: '대시보드', icon: 'bi-speedometer2' },
  { to: '/admin/accounts', label: '회원 관리', icon: 'bi-people' },
  { to: '/admin/trucks', label: '차량 관리', icon: 'bi-truck' },
  { to: '/admin/overload-checks', label: '과적 검사', icon: 'bi-exclamation-triangle' },
]

const loading = ref(true)
const error = ref('')
const profile = reactive({})

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('ko-KR') : '-'
}

onMounted(async () => {
  const id = authState.user?.accountId
  if (!id) {
    error.value = '로그인 정보를 확인할 수 없습니다.'
    loading.value = false
    return
  }
  Object.assign(profile, authState.user)
  try {
    const res = await adminApi.get(`/api/accounts/${id}`)
    Object.assign(profile, res.data)
  } catch (err) {
    if (!profile.userId) error.value = pickErrorMessage(err, '내 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.profile-page {
  text-align: left;
}
.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(280px, 0.85fr);
  gap: 16px;
  align-items: start;
}
.profile-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.profile-card {
  background: var(--a-surface, #fff);
  border: 1px solid var(--a-border, #e3e8ef);
  border-radius: var(--a-radius, 12px);
  padding: 24px;
}
.profile-side-title {
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 12px;
}
.profile-note {
  margin: 0;
  padding-left: 18px;
  color: var(--a-text-muted, #64748b);
  font-size: 13.5px;
  line-height: 1.7;
}
.profile-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 18px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--a-border, #e3e8ef);
}
.profile-avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--a-primary, #0a2540);
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.profile-name { font-size: 18px; font-weight: 700; margin-bottom: 4px; }
.profile-info {
  display: grid;
  grid-template-columns: 110px 1fr;
  margin: 0;
}
.profile-info dt,
.profile-info dd {
  margin: 0;
  padding: 12px 0;
  border-bottom: 1px solid var(--a-border, #e3e8ef);
  font-size: 14px;
}
.profile-info dt { color: var(--a-text-muted, #64748b); font-weight: 600; }
.profile-info dd { color: var(--a-text, #0a2540); font-weight: 600; word-break: break-all; }
.profile-info dt:nth-last-of-type(1),
.profile-info dd:last-of-type { border-bottom: none; }
@media (max-width: 960px) {
  .profile-grid { grid-template-columns: 1fr; }
}
</style>
