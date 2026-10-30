<template>
  <!-- 26.10.29 인수 내역 (관리자 · 관제사 · 운송사 공용 화면) -->
  <div>
    <AdminPageHeader title="인수 내역" description="허브에서 인계한 컨테이너의 인수자 · 서명 · 사진을 확인하고 인수증(A4)을 PDF 로 내려받습니다." />
    <HandoverListPanel />
  </div>
</template>
<script setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import HandoverListPanel from '@/components/common/HandoverListPanel.vue'
</script>
