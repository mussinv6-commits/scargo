<template>
  <!-- v13: 승인 대기 운송사(CORPORATE_PENDING) 로그인 후 화면 -->
  <div class="pend"><div class="box">
    <div class="ico"><i class="bi bi-hourglass-split"></i></div>
    <h2>운송사 승인 대기 중입니다</h2>
    <p><b>{{ user?.userName }}</b> 계정의 운송사 가입 신청을 관리자가 검토하고 있습니다.<br>승인이 끝나면 통합 대시보드·운송오더·지선 배차 등 운송사 메뉴를 이용할 수 있습니다.</p>
    <table><tbody><tr><th>아이디</th><td>{{ user?.userId }}</td></tr><tr><th>상태</th><td><span class="tag">승인 대기</span></td></tr></tbody></table>
    <div class="note">승인 결과는 알림(종 모양)으로 안내됩니다. 문의: 관리자 또는 고객센터</div>
    <div class="btns"><RouterLink to="/scargo" class="b1">홈으로</RouterLink><RouterLink to="/notice" class="b2">공지사항</RouterLink></div>
  </div></div>
</template>
<script setup>
import { computed } from 'vue'
import { authState } from '@/auth/authState.js'
const user = computed(() => authState.user)
</script>
<style scoped>
.pend{min-height:calc(100vh / var(--sc-zoom, 1) - var(--nav-h, 60px));display:flex;align-items:center;justify-content:center;background:#f4f5f8;padding:40px 16px}
.box{background:#fff;border-radius:14px;box-shadow:0 4px 20px rgba(16,38,68,.08);padding:40px 48px;max-width:560px;width:100%;text-align:center;color:var(--color-primary)}
.ico{width:64px;height:64px;border-radius:50%;background:rgba(238,204,131,.25);color:#8c6c3b;display:flex;align-items:center;justify-content:center;font-size:28px;margin:0 auto 16px}
h2{font-size:22px;font-weight:700;margin:0 0 12px}
p{color:#4b5873;line-height:1.7;margin:0 0 20px}
table{margin:0 auto 16px;border-collapse:collapse;font-size:14px}th{color:#6b7590;font-weight:500;padding:4px 14px;text-align:right}td{padding:4px 14px;text-align:left}
.tag{background:rgba(238,204,131,.35);color:#6b4e1d;border-radius:10px;padding:2px 10px;font-size:12px;font-weight:700}
.note{font-size:12.5px;color:#6b7590;margin-bottom:22px}
.btns{display:flex;gap:10px;justify-content:center}
.btns a{padding:9px 22px;border-radius:8px;font-weight:600;text-decoration:none;font-size:14px}
.b1{background:var(--color-gold);color:var(--color-primary)}.b2{background:#fff;border:1px solid #cfd5e2;color:var(--color-primary)}
</style>
