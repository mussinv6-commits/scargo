<template>
  <!-- 26.10.22 추가: 택배 이동 / 물류센터 관제 화면이 붙기 전까지 쓰는 임시 안내 화면 -->
  <div class="soon">
    <h2>{{ $route.path.includes('center') ? '물류센터 관제' : '택배 이동' }}</h2>
    <p>이 화면은 준비 중입니다.</p>
    <RouterLink to="/sbot">← 대시보드로</RouterLink>
  </div>
</template>

<style scoped>
.soon { padding: 60px 0; text-align: center; color: #e6eff7; }
.soon h2 { color: #f3d27a; margin: 0 0 8px; }
.soon p { color: #98a0ad; }
.soon a { color: #f3d27a; text-decoration: none; }
</style>
