<template>
  <!-- 26.10.23 운송사 컨테이너 점검표: 자기 업체 기록 조회 · 작성 · 작성 중 취소 · 재점검 -->
  <div>
    <AdminPageHeader title="컨테이너 점검표" description="소속 업체 컨테이너의 7-point 점검 기록을 조회하고 관리합니다." />
    <ContainerInspectionPanel />
  </div>
</template>

<script setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import ContainerInspectionPanel from '@/components/common/ContainerInspectionPanel.vue'
</script>
