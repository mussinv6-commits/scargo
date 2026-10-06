<template>
  <div class="topbar">
    <div>
      <div class="brand">{{ title }}</div>
      <div class="sub">기타</div>
    </div>
  </div>
  <main class="page">
    <p class="info-empty">{{ title }} 화면은 준비 중입니다.</p>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => route.meta.title || '안내')
</script>

<style scoped>
.info-empty { margin: 0; color: var(--text-muted, #7b8da3); font-size: 15px; }
</style>
