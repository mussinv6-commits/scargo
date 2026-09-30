<template>
  <div>
    <div class="admin-page-header">
      <h1>대시보드</h1>
      <p>업체 · 차량 · 컨테이너 · 과적 검사 현황을 한눈에 확인하세요.</p>
    </div>

    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-label">승인 대기 기업회원</div>
        <div class="kpi-value accent">{{ counts.pendingAccounts ?? '-' }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">등록 업체</div>
        <div class="kpi-value">{{ counts.companies ?? '-' }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">등록 차량</div>
        <div class="kpi-value">{{ counts.trucks ?? '-' }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">등록 컨테이너</div>
        <div class="kpi-value">{{ counts.containers ?? '-' }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">등록 야드</div>
        <div class="kpi-value">{{ counts.yards ?? '-' }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">과적 위반 건수</div>
        <div class="kpi-value accent">{{ counts.violations ?? '-' }}</div>
      </div>
    </div>

    <div class="crud-table-wrap" v-if="loadError">
      <p style="color: var(--a-red); font-size: 13.5px; margin: 0;">
        일부 데이터를 불러오지 못했습니다: {{ loadError }}
        <br />(관리자 인증/세션이 필요한 API입니다. 로그인 상태와 백엔드 권한 설정을 확인해주세요.)
      </p>
    </div>

    <div class="crud-table-wrap" style="margin-top: 16px;">
      <h2 class="crud-title" style="margin-bottom: 12px;">바로가기</h2>
      <div style="display:flex; flex-wrap:wrap; gap:10px;">
        <RouterLink class="btn-admin btn-admin-ghost" to="/admin/accounts">회원 승인 처리</RouterLink>
        <RouterLink class="btn-admin btn-admin-ghost" to="/admin/companies">업체 등록</RouterLink>
        <RouterLink class="btn-admin btn-admin-ghost" to="/admin/trucks">차량 등록</RouterLink>
        <RouterLink class="btn-admin btn-admin-ghost" to="/admin/yards">야드 등록</RouterLink>
        <RouterLink class="btn-admin btn-admin-ghost" to="/admin/overload-checks">과적 검사 기록</RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'

const counts = reactive({
  pendingAccounts: null,
  companies: null,
  trucks: null,
  containers: null,
  yards: null,
  violations: null,
})
const loadError = ref('')

async function safe(promise) {
  try {
    const res = await promise
    return res.data
  } catch (err) {
    loadError.value = pickErrorMessage(err)
    return null
  }
}

onMounted(async () => {
  const [accounts, companies, trucks, containers, yards, overloads] = await Promise.all([
    safe(adminApi.get('/api/accounts')),
    safe(adminApi.get('/api/companies')),
    safe(adminApi.get('/api/trucks')),
    safe(adminApi.get('/api/containers')),
    safe(adminApi.get('/api/yards')),
    safe(adminApi.get('/api/overload-checks')),
  ])

  if (accounts) counts.pendingAccounts = accounts.filter((a) => a.userType === 'CORPORATE_PENDING').length
  if (companies) counts.companies = companies.length
  if (trucks) counts.trucks = trucks.length
  if (containers) counts.containers = containers.length
  if (yards) counts.yards = yards.length
  if (overloads) counts.violations = overloads.filter((o) => o.isViolation).length
})
</script>
