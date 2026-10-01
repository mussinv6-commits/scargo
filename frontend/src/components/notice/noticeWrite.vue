<template>
  <PostEditor
    category="NOTICE"
    label="공지사항"
    :list-route="{ name: 'notice' }"
    :detail-route="(id) => ({ name: 'noticeDetail', params: { id } })"
  />
</template>

<script setup>
import PostEditor from '@/components/notice/PostEditor.vue'
</script>
