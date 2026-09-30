<template>
  <div class="admin-shell">
    <AdminSidebar />
    <div class="admin-main">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import AdminSidebar from './AdminSidebar.vue'
</script>

<style src="@/components/CSS/admin.css"></style>
