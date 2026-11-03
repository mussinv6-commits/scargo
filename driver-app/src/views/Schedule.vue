<template>
  <div class="screen">
    <header class="topbar"><button class="icon-btn" type="button" @click="$router.back()">←</button><h1>배차 일정</h1></header>
    <main class="content">
      <div class="chips">
        <button type="button" class="chip" @click="shift(-1)">이전달</button>
        <button type="button" class="chip on">{{ month }}</button>
        <button type="button" class="chip" @click="shift(1)">다음달</button>
      </div>
      <div v-if="err" class="error-box">{{ err }}</div>
      <section class="card" v-if="data">
        <div class="cal">
          <div v-for="h in ['일','월','화','수','목','금','토']" :key="h" class="hd">{{ h }}</div>
          <div v-for="n in pad" :key="'p'+n"></div>
          <button v-for="d in daysInMonth" :key="d" type="button" class="day" :class="{on: selected===d, has: hasDay(d)}" @click="selected=d">{{ d }}</button>
        </div>
      </section>
      <section class="card" v-if="data">
        <div class="between"><b>{{ month }}-{{ String(selected).padStart(2,'0') }}</b><span class="pill soft">{{ dayItems.length }}건</span></div>
        <div v-if="!dayItems.length" class="empty" style="padding:12px">이 날 배차가 없습니다.</div>
        <div v-for="it in dayItems" :key="it.kind+it.id" style="padding:10px 0;border-bottom:1px solid var(--line)">
          <div class="between"><b class="small">{{ it.kind==='BRANCH'?'지선':'컨테이너' }} · {{ it.no || it.title }}</b><span class="pill soft">{{ it.status }}</span></div>
          <span class="small muted">{{ it.fromName }} → {{ it.toName }}</span>
        </div>
      </section>
    </main>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { Api, errText } from '../lib/api'
const month = ref(new Date().toISOString().slice(0,7))
const data = ref(null); const err=ref(''); const selected = ref(new Date().getDate())
const daysInMonth = computed(()=>{ const [y,m]=month.value.split('-').map(Number); return new Date(y,m,0).getDate() })
const pad = computed(()=>{ const [y,m]=month.value.split('-').map(Number); return new Date(y,m-1,1).getDay() })
const daySet = computed(()=> new Set((data.value?.days)||[]))
function hasDay(d){ return daySet.value.has(`${month.value}-${String(d).padStart(2,'0')}`) }
const dayItems = computed(()=>{
  const key = `${month.value}-${String(selected.value).padStart(2,'0')}`
  return (data.value?.items||[]).filter(it => String(it.at||'').startsWith(key))
})
async function load(){ err.value=''; try{ data.value = await Api.menuSchedule(month.value) }catch(e){ err.value=errText(e); data.value=null } }
function shift(n){ const [y,m]=month.value.split('-').map(Number); const dt=new Date(y,m-1+n,1); month.value=`${dt.getFullYear()}-${String(dt.getMonth()+1).padStart(2,'0')}`; selected.value=1; load() }
onMounted(load)
</script>
<style scoped>
.cal{display:grid;grid-template-columns:repeat(7,1fr);gap:4px;text-align:center;font-size:12px}
.hd{color:var(--faint);font-weight:600;padding:4px 0}
.day{aspect-ratio:1;border-radius:10px;border:0;background:transparent;cursor:pointer}
.day.on{background:var(--primary);color:#fff;font-weight:700}
.day.has{box-shadow:inset 0 -3px 0 #fca311}
</style>
