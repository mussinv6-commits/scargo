<template>
  <!-- 26.10.29 지선 운송기사 웹 로그인 안내: 지선 업무 화면(지선 운행 · 상차 확인 · 센터 인계)은 기사 앱에만 있다 -->
  <div class="ao">
    <div class="ao-card">
      <i class="bi bi-phone"></i>
      <h1>기사 앱을 이용해 주세요</h1>
      <p>{{ name }}님은 <b>지선 운송기사</b> 계정입니다.<br />지선 운행 · 상차 확인 · 센터 인계는 <b>S Cargo 기사 앱</b>에서 할 수 있습니다.</p>
      <div class="ao-btns">
        <RouterLink to="/notice" class="ao-btn">공지사항 보기</RouterLink>
        <RouterLink to="/notifications" class="ao-btn">알림 내역</RouterLink>
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed } from 'vue'
import { authState } from '@/auth/authState.js'
const name = computed(() => authState.user?.userName || '기사')
</script>
<style scoped>
.ao { min-height: calc(100vh - 160px); display: flex; align-items: center; justify-content: center; padding: 24px 16px; }
.ao-card { max-width: 440px; width: 100%; background: #fff; border: 1px solid #d5dbe5; border-radius: 14px; padding: 32px 24px; text-align: center; box-shadow: 0 6px 20px rgba(20, 33, 61, .08); }
.ao-card > i { font-size: 40px; color: #1F4F8C; }
.ao-card h1 { font-size: 20px; font-weight: 800; margin: 10px 0 8px; color: #14213d; }
.ao-card p { font-size: 14px; color: #4b5563; line-height: 1.7; margin: 0 0 18px; }
.ao-btns { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }
.ao-btn { border: 1px solid #c9d3e0; border-radius: 8px; padding: 8px 14px; font-size: 13.5px; color: #1f2937; text-decoration: none; background: #f8fafc; }
.ao-btn:hover { border-color: #1F4F8C; color: #1F4F8C; }
</style>
