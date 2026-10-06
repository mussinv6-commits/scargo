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
          </dl>
        </template>
      </section>

      <div class="profile-side">
        <section class="profile-card">
          <h2 class="profile-side-title">바로가기</h2>
          <div class="shortcut-grid">
            <RouterLink v-for="m in shortcuts" :key="m.to" :to="m.to" :class="m.tone">
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
  { to: '/admin', label: '대시보드', icon: 'bi-speedometer2', tone: 'tone-navy' },
  { to: '/admin/accounts', label: '회원 관리', icon: 'bi-people', tone: 'tone-blue' },
  { to: '/admin/trucks', label: '차량 관리', icon: 'bi-truck', tone: 'tone-orange' },
  { to: '/admin/overload-checks', label: '과적 검사', icon: 'bi-exclamation-triangle', tone: 'tone-teal' },
  { to: '/admin/notices', label: '공지사항', icon: 'bi-megaphone', tone: 'tone-violet' },
]

const loading = ref(true)
const error = ref('')
const profile = reactive({})

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
.profile-page :deep(.admin-page-header) {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e3e8ef;
}
.profile-page :deep(.admin-page-heading) { padding-left: 0; }
.profile-page :deep(.admin-page-header h1) {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
  margin: 0 0 4px;
  color: #0a2540;
}
.profile-page :deep(.admin-page-header p) {
  font-size: 13.5px;
  line-height: 1.5;
  color: #64748b;
  margin: 0;
}
.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: stretch;
}
.profile-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
}
.profile-side > .profile-card {
  flex: 1;
}
.profile-card {
  background: var(--a-surface, #fff);
  border: 1px solid var(--a-border, #e3e8ef);
  border-radius: var(--a-radius, 12px);
  padding: 24px;
  height: 100%;
  box-sizing: border-box;
}
.profile-side-title {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 12px;
}
.profile-card .shortcut-grid {
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
}
.profile-card .shortcut-grid a {
  font-size: 16px;
  padding: 12px 14px;
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
.shortcut-grid a.tone-navy,
.shortcut-grid a.tone-navy:hover { background: #0a2540; color: #fff; border-color: #0a2540; }
.shortcut-grid a.tone-blue,
.shortcut-grid a.tone-blue:hover { background: #2563eb; color: #fff; border-color: #2563eb; }
.shortcut-grid a.tone-orange,
.shortcut-grid a.tone-orange:hover { background: #ff6b00; color: #fff; border-color: #ff6b00; }
.shortcut-grid a.tone-teal,
.shortcut-grid a.tone-teal:hover { background: #0f766e; color: #fff; border-color: #0f766e; }
.shortcut-grid a.tone-violet,
.shortcut-grid a.tone-violet:hover { background: #6d28d9; color: #fff; border-color: #6d28d9; }
.shortcut-grid a:hover { filter: brightness(1.08); }
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
@media (max-width: 960px) {
  .profile-grid { grid-template-columns: 1fr; }
}
</style>
