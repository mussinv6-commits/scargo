<template>
  <span class="ops-live-clock" :title="fullLabel">
    <i class="bi bi-clock"></i>
    <span>{{ timeLabel }}</span>
  </span>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const now = ref(new Date())
let timer = null

function pad(n) {
  return String(n).padStart(2, '0')
}

const timeLabel = computed(() => {
  const n = now.value
  return `${pad(n.getHours())}:${pad(n.getMinutes())}:${pad(n.getSeconds())}`
})

const fullLabel = computed(() => {
  const n = now.value
  const week = ['일', '월', '화', '수', '목', '금', '토'][n.getDay()]
  return `${n.getFullYear()}.${pad(n.getMonth() + 1)}.${pad(n.getDate())} (${week}) ${timeLabel.value}`
})

onMounted(() => {
  now.value = new Date()
  timer = setInterval(() => { now.value = new Date() }, 1000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>
