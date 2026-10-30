<template>
  <!-- 26.10.26 메뉴 안내: 관리자 사이드바의 전체 구성을 한 번에 펼쳐서 보여 준다. 이름을 누르면 해당 화면으로 이동 -->
  <div class="mg">
    <h2>메뉴 안내</h2>
    <p class="mg-d">S Cargo 관리자 메뉴 전체 구성입니다. 항목을 누르면 해당 화면으로 이동합니다.</p>
    <div class="mg-grid">
      <section v-for="g in sections" :key="g.key" class="mg-card">
        <h3>{{ g.label }}</h3>
        <RouterLink v-for="m in g.items" :key="m.to" :to="m.to" class="mg-it"><i :class="['bi', m.icon]"></i>{{ m.label }}</RouterLink>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ADMIN_MENU } from '@/components/admin/adminMenu.js'
const LABELS = [['top', '대시보드 · 운영'], ['member', '회원 · 업체'], ['vehicle', '차량 · 화물'], ['yard', '야드 · 적재'], ['tower', '공통 컨트롤타워'], ['port', '항만 → 허브'], ['hubc', '허브 → 물류센터'], ['notice', '설정 · 안내']] // 26.10.28 메뉴 이동 반영
const sections = LABELS.map(([key, label]) => ({ key, label, items: ADMIN_MENU[key] || [] }))
</script>

<style scoped>
.mg { padding: 4px 2px; color: #102644; overflow: visible; }
/* 26.10.28 QA#20 제목은 상단 고정 바에서 다른 메뉴와 같은 글씨체·크기로 표시 (이 화면 h2 숨김) */
.mg h2 { margin: 0 0 4px; font-size: 22px; }
.mg-d { margin: 0 0 16px; color: #64748b; font-size: 13.5px; line-height: 1.5; }
.mg-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; }
.mg-card { background: #fff; border-radius: 12px; padding: 14px 16px; box-shadow: 0 1px 3px #0001; }
.mg-card h3 { margin: 0 0 8px; font-size: 14px; color: #8a6a10; }
.mg-it { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid #f0f2f6; color: #102644; text-decoration: none; font-size: 14px; }
.mg-it:last-child { border-bottom: 0; }
.mg-it:hover { color: #c9972f; }
.mg-it i { width: 16px; color: #8a94a6; }
</style>
