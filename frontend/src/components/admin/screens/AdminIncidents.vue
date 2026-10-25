<template>
  <!-- 26.10.24 S Cargo 관리자 예외·사고 접수 · 처리 -->
  <div>
    <AdminPageHeader title="예외 · 사고 접수" description="파손 · 씰 · 지연 · 차량 · 안전 문제를 접수하고 담당자와 조치 내용을 남깁니다. 해결 처리에는 조치 내용이 필요합니다." />
    <IncidentPanel />
  </div>
</template>

<script setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import IncidentPanel from '@/components/common/IncidentPanel.vue'
</script>
