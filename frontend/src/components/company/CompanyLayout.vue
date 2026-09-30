<template>
  <div class="biz-shell">
    <div class="biz-header">
      <div>
        <h1>사업자 페이지</h1>
        <p>{{ authState.user?.userName ? `${authState.user.userName}님, ` : '' }}업체 정보와 배차 매핑을 관리하세요.</p>
      </div>
    </div>

    <nav class="biz-tabs">
      <RouterLink to="/company" class="biz-tab" :class="{ active: route.path === '/company' }">
        🏢 업체 정보
      </RouterLink>
      <!-- 26.09.21 추가: 사업자 전용 차량 등록 서브메뉴 -->
      <RouterLink to="/company/trucks/new" class="biz-tab" :class="{ active: route.path === '/company/trucks/new' }">
        🚛 차량 등록
      </RouterLink>
      <RouterLink to="/company/mapping" class="biz-tab" :class="{ active: route.path === '/company/mapping' }">
        🚚 차량-컨테이너 매핑
      </RouterLink>
    </nav>

    <div class="biz-content">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { authState } from '@/auth/authState.js'
const route = useRoute()
</script>

<style src="@/components/CSS/company.css"></style>