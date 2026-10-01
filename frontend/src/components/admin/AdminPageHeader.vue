<template>
  <!-- 26.09.30 추가: 관리자 화면 제목 영역 공통 컴포넌트.
       화면마다 제목이 가운데/왼쪽으로 제각각이고, 버튼이 있는 화면(야드/검문소 등)은
       제목과 설명 줄이 어긋나던 문제를 한 곳에서 맞춘다. -->
  <header class="admin-page-header">
    <div class="admin-page-heading">
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="admin-page-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
})
</script>
