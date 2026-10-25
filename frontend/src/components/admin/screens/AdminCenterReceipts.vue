<template>
  <!-- 26.10.23 S Cargo 관리자 지역센터 인수 현황 (전체 센터, 조회만 — 인수 확정은 지역센터 관제사 업무) -->
  <div>
    <AdminPageHeader title="지역센터 인수 현황" description="지선 운행의 센터 도착 · 인수 확정 상태를 전체 센터 기준으로 봅니다. 인수 확정은 지역센터 관제사가 합니다." />
    <CenterReceiptPanel :can-confirm="false" />
  </div>
</template>

<script setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import CenterReceiptPanel from '@/components/common/CenterReceiptPanel.vue'
</script>
