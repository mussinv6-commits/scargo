<template>
  <!-- 26.10.24 KPI 일별 추이 -->
  <div>
    <AdminPageHeader title="KPI 추이" description="일별 입고 · 하역 · 운송 · 인계 대기 시간과 점검 불합격 추이를 확인합니다." />
    <KpiTrendPanel />
  </div>
</template>

<script setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import KpiTrendPanel from '@/components/common/KpiTrendPanel.vue'
</script>
