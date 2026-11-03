<template>
  <div class="screen">
    <header class="topbar"><button class="icon-btn" type="button" @click="$router.back()">←</button><h1>정산·수당 내역</h1></header>
    <main class="content">
      <div class="chips">
        <button type="button" class="chip" @click="shift(-1)">이전달</button>
        <button type="button" class="chip on">{{ month }}</button>
        <button type="button" class="chip" @click="shift(1)">다음달</button>
      </div>
      <div v-if="err" class="error-box">{{ err }}</div>
      <div class="grid3" v-if="data">
        <div class="card tight stat"><span>이번 달</span><b>{{ won(data.monthTotal) }}</b></div>
        <div class="card tight stat"><span>지급 예정</span><b>{{ won(data.pendingTotal) }}</b></div>
        <div class="card tight stat"><span>건수</span><b>{{ data.monthCount }}</b></div>
      </div>
      <p v-if="data?.note" class="small faint">{{ data.note }}</p>
      <section class="card" v-if="data">
        <div v-if="!(data.items||[]).length" class="empty">이달 내역이 없습니다.</div>
        <div v-for="it in data.items||[]" :key="it.kind+it.id+it.at" style="padding:10px 0;border-bottom:1px solid var(--line)">
          <div class="between"><b class="small">{{ it.title }}</b><span class="pill" :class="it.status==='PAID'?'ok':(it.status==='PENDING'?'warn':'soft')">{{ it.status }}</span></div>
          <span class="small muted">{{ it.ref }} · {{ won(it.amount) }}</span>
        </div>
      </section>
    </main>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { Api, errText } from '../lib/api'
const month = ref(new Date().toISOString().slice(0,7))
const data = ref(null); const err = ref('')
const won = (n) => '₩' + Number(n||0).toLocaleString('ko-KR')
async function load(){ err.value=''; try{ data.value = await Api.menuSettlements(month.value) }catch(e){ err.value=errText(e) } }
function shift(n){ const [y,m]=month.value.split('-').map(Number); const dt=new Date(y,m-1+n,1); month.value=`${dt.getFullYear()}-${String(dt.getMonth()+1).padStart(2,'0')}`; load() }
onMounted(load)
</script>
