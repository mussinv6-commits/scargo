<template>
  <!-- 26.10.23 S Cargo 관리자 컨테이너 점검표: 전체 기록. 확정본 취소는 사유를 남기는 예외 절차 -->
  <div>
    <AdminPageHeader title="컨테이너 점검표" description="전체 업체의 7-point 점검 기록입니다. 확정된 기록의 취소는 사유와 함께 감사 기록에 남습니다." />
    <ContainerInspectionPanel />
  </div>
</template>

<script setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import ContainerInspectionPanel from '@/components/common/ContainerInspectionPanel.vue'
</script>
