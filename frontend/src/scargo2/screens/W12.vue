<template>
<section class="scr" id="W-12"><iframe class="toll-frame" :src="src" title="구간 관제 지도" /></section>
</template>
<script setup>
// 26.10.26: 인천항 → 곤지암 구간 관제 화면 (run_toll.bat 서버, 기본 http://localhost:8002). 주소는 .env 의 VITE_TOLL_URL 로 바꾼다
import { computed } from 'vue'
const base = (import.meta.env.VITE_TOLL_URL || 'http://localhost:8002').replace(/\/$/, '')
const token = import.meta.env.VITE_TOLL_TOKEN || ''
const src = computed(() => `${base}/?embed=1${token ? '&token=' + encodeURIComponent(token) : ''}`)
</script>
<style scoped>
.toll-frame { width: 100%; height: calc(100vh - 110px); min-height: 520px; border: 0; display: block; }
</style>
