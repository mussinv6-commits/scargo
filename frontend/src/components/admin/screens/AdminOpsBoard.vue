<template>
  <!-- 26.10.24 S Cargo 관리자 운영 현황판: 하역 대기 · 지연 · 점검 불합격 · 예외/사고를 한 화면에서 보고 바로 처리 -->
  <div>
    <AdminPageHeader title="운영 현황판" description="지금 처리해야 할 항목만 모았습니다. 하역 완료는 이 화면에서 바로 처리하고, 예외·사고는 접수 화면에서 처리합니다." />
    <OpsBoard incidents-link="/admin/incidents" />
  </div>
</template>

<script setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import OpsBoard from '@/components/common/OpsBoard.vue'
</script>
