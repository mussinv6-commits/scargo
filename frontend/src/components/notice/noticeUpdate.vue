<template>
  <PostEditor
    category="NOTICE"
    label="공지사항"
    :post-id="route.params.id"
    :list-route="{ name: 'notice' }"
    :detail-route="(id) => ({ name: 'noticeDetail', params: { id } })"
  />
</template>

<script setup>
import { useRoute } from 'vue-router'
import PostEditor from '@/components/notice/PostEditor.vue'
const route = useRoute()
</script>
