<template>
  <div class="screen">
    <header class="topbar"><button class="icon-btn" type="button" @click="$router.back()">←</button><h1>휴게·주유·차고</h1></header>
    <main class="content">
      <div class="chips">
        <button v-for="t in tabs" :key="t.k" type="button" class="chip" :class="{on: tab===t.k}" @click="tab=t.k">{{ t.l }}</button>
      </div>
      <div v-if="err" class="error-box">{{ err }}</div>
      <p v-if="data?.note" class="small faint">{{ data.note }}</p>
      <section class="card">
        <div v-for="p in list" :key="p.type+p.id" style="padding:10px 0;border-bottom:1px solid var(--line)">
          <div class="between"><b class="small">{{ p.name }}</b><span class="pill soft">{{ p.type }}</span></div>
          <span class="small muted">{{ p.amenities || p.code || '' }}{{ p.example ? ' · 예시' : '' }}</span>
        </div>
        <div v-if="!list.length" class="empty">표시할 장소가 없습니다.</div>
      </section>
    </main>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { Api, errText } from '../lib/api'
import { filterCheckpoints } from '../lib/checkpoints'
const data = ref(null); const err=ref(''); const tab=ref('ALL')
const tabs=[{k:'ALL',l:'전체'},{k:'REST',l:'휴게'},{k:'FUEL',l:'주유'},{k:'DEPOT',l:'차고'},{k:'CHECKPOINT',l:'검문'}]
const list = computed(()=>{
  if(!data.value) return []
  const rest = data.value.restFuel||[]
  const dep = data.value.depots||[]
  const cp = filterCheckpoints(data.value.checkpoints||[])
  const all = [...rest, ...dep, ...cp]
  if(tab.value==='ALL') return all
  if(tab.value==='CHECKPOINT') return cp
  if(tab.value==='DEPOT') return dep
  return rest.filter(x => x.type===tab.value)
})
onMounted(async()=>{
  try{
    let lat, lng
    await new Promise(r => navigator.geolocation?.getCurrentPosition(p=>{lat=p.coords.latitude;lng=p.coords.longitude;r()}, ()=>r(), {timeout:5000}) || r())
    data.value = await Api.menuNearby(lat, lng)
  }catch(e){ err.value=errText(e) }
})
</script>
