<template>
  <div class="driver-app-shell" :class="{ 'is-my-page': isMyPage }">
    <Sidebar />
    <div class="driver-main">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './Sidebar.vue'

const route = useRoute()
const isMyPage = computed(() => route.path.startsWith('/driver/app/my-page'))
</script>

<style src="@/components/CSS/tokens.css"></style>
