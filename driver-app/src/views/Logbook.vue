<template>
  <div class="screen">
    <header class="topbar"><button class="icon-btn" type="button" @click="$router.back()">←</button><h1>운행 일지</h1></header>
    <main class="content">
      <input class="input" type="date" v-model="day" @change="load" />
      <div v-if="err" class="error-box">{{ err }}</div>
      <div class="grid3" v-if="data">
        <div class="card tight stat"><span>오늘 운행</span><b>{{ fmtMin(data.driveMinutes) }}</b></div>
        <div class="card tight stat"><span>휴게(추정)</span><b>{{ fmtMin(data.restMinutes) }}</b></div>
        <div class="card tight stat"><span>거리</span><b>{{ data.distanceKm }}km</b></div>
      </div>
      <p v-if="data?.note" class="small faint">{{ data.note }}</p>
      <section class="card tight" v-if="data">
        <b style="font-size:14px">타임라인</b>
        <div v-if="!(data.timeline||[]).length" class="empty" style="padding:12px">이날 기록이 없습니다.</div>
        <div v-for="(t,i) in data.timeline||[]" :key="i" style="padding:8px 0;border-bottom:1px solid var(--line)">
          <b class="small">{{ t.label }}</b>
          <div class="small muted">{{ t.place }}</div>
        </div>
      </section>
    </main>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { Api, errText } from '../lib/api'
const day = ref(new Date().toISOString().slice(0,10))
const data = ref(null); const err=ref('')
const fmtMin = (m) => { m=Number(m||0); return `${Math.floor(m/60)}h ${m%60}m` }
async function load(){ err.value=''; try{ data.value = await Api.menuLogbook(day.value) }catch(e){ err.value=errText(e)} }
onMounted(load)
</script>
