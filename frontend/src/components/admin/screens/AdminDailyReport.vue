<template>
  <!-- 26.10.24 S Cargo 관리자 일일 처리량 보고서 (전체 허브 + 게이트 · 과적 검사) -->
  <div>
    <AdminPageHeader title="일일 처리량 보고서" description="하루 동안의 게이트 · 운송 · 허브 하역 · 점검 처리량입니다. Excel 파일로 내려받아 보고서로 쓸 수 있습니다." />
    <DailyReportPanel />
  </div>
</template>

<script setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import DailyReportPanel from '@/components/common/DailyReportPanel.vue'
</script>
