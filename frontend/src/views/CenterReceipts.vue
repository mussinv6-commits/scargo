<template>
  <!-- 26.10.23 지역센터 관제사(CENTER_OP) 인수 확정 화면 (/center/receipts). 소속 센터로 온 지선 운행만 보인다 -->
  <div class="center-page">
    <header class="center-head">
      <h1>지역센터 인수 확정</h1>
      <p>허브에서 출발해 센터에 도착한 지선 운행을 확인하고 인수를 확정합니다.</p>
    </header>
    <CenterReceiptPanel />
  </div>
</template>

<script setup>
import CenterReceiptPanel from '@/components/common/CenterReceiptPanel.vue'
</script>

<style scoped>
.center-page { max-width: 1200px; margin: 0 auto; padding: 24px 16px 48px; }
.center-head h1 { font-size: 22px; font-weight: 700; margin: 0 0 4px; }
.center-head p { margin: 0 0 14px; color: #6b7280; font-size: 14px; }
</style>
