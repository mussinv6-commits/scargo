<template>
  <!-- 26.10.24 게이트 사전 예약 -->
  <div>
    <AdminPageHeader title="게이트 사전 예약" description="업체가 등록한 반입 · 반출 예약을 확인하고 입차 확인 · 불참 처리합니다." />
    <GateReservationPanel />
  </div>
</template>

<script setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import GateReservationPanel from '@/components/common/GateReservationPanel.vue'
</script>
