<template>
  <!-- 26.10.22 추가: 택배 이동 / 물류센터 3D 화면. public/sbot3d 의 HTML 을 그대로 불러온다 (Three.js 는 그 파일이 CDN 에서 읽음) -->
  <iframe class="sb-frame" :src="url" :title="title" />
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
const props = defineProps({ page: { type: String, required: true }, title: { type: String, default: "" } });
const route = useRoute();
// 대시보드에서 센터 건물을 누르면 ?c=SEL|GGI|ICN 으로 들어와 그 센터가 먼저 선택된다
const url = computed(() => `/sbot3d/${props.page}.html` + (props.page === "center" && route.query.c ? `?c=${encodeURIComponent(route.query.c)}` : ""));
</script>

<style scoped>
.sb-frame { width: 100%; height: calc(100vh - 100px); min-height: 480px; border: 1px solid #2a3a58; border-radius: 10px; background: #0b1d36; display: block; }
</style>
