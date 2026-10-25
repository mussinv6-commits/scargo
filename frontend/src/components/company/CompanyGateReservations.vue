<template>
  <!-- 26.10.24 운송사 게이트 사전 예약 -->
  <div>
    <AdminPageHeader title="게이트 사전 예약" description="차량 반입 · 반출 시각을 미리 예약하면 관제가 도착 전에 확인할 수 있습니다." />
    <GateReservationPanel />
  </div>
</template>

<script setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import GateReservationPanel from '@/components/common/GateReservationPanel.vue'
</script>
