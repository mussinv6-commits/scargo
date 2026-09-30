<template>
  <div class="driver-app-shell">
    <Sidebar />
    <div class="driver-main">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import Sidebar from './Sidebar.vue'
</script>

<style src="@/components/CSS/tokens.css"></style>
