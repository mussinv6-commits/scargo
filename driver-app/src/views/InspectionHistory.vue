<template>
  <div class="screen">
    <header class="topbar"><button class="icon-btn" type="button" @click="$router.back()">←</button><h1>점검 이력</h1></header>
    <main class="content">
      <div class="chips">
        <button v-for="r in ranges" :key="r.k" type="button" class="chip" :class="{on: range===r.k}" @click="range=r.k; load()">{{ r.l }}</button>
      </div>
      <div class="chips">
        <button type="button" class="chip" :class="{on: result==='ALL'}" @click="result='ALL'; load()">전체</button>
        <button type="button" class="chip" :class="{on: result==='PASS'}" @click="result='PASS'; load()">양호</button>
        <button type="button" class="chip" :class="{on: result==='WARN'}" @click="result='WARN'; load()">주의</button>
        <button type="button" class="chip" :class="{on: result==='FAIL'}" @click="result='FAIL'; load()">불량</button>
      </div>
      <div v-if="err" class="error-box">{{ err }}</div>
      <p v-if="data?.note" class="small faint">{{ data.note }}</p>
      <section class="card">
        <div v-if="!(data?.items||[]).length" class="empty">이 기간 점검 기록이 없습니다.</div>
        <div v-for="it in data?.items||[]" :key="it.kind+it.id" style="padding:10px 0;border-bottom:1px solid var(--line)">
          <div class="between">
            <b class="small">{{ it.kind==='VEHICLE'?'차량':'컨테이너' }} · {{ it.ref }}</b>
            <span class="pill" :class="pillClass(it.overall)">{{ pillLabel(it.overall) }}</span>
          </div>
          <span class="small muted">{{ it.day || it.at }}</span>
          <div v-if="it.kind==='VEHICLE' && (isFail(it.overall)||isWarn(it.overall))" class="small" :style="{color: isFail(it.overall)?'var(--danger-ink)':'#B45309', marginTop:'4px'}">
            {{ isFail(it.overall)?'불량':'주의' }} 항목: {{ issueItems(it).join(', ') || '상세 없음' }}
          </div>
        </div>
      </section>
    </main>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { Api, errText } from '../lib/api'
const ranges=[{k:'today',l:'오늘'},{k:'week',l:'이번 주'},{k:'month',l:'이번 달'}]
const range=ref('month'); const result=ref('ALL'); const data=ref(null); const err=ref('')
const isFail = (o) => /FAIL|BAD|NG|불량/i.test(String(o||''))
const isWarn = (o) => /WARN|CAUTION|주의/i.test(String(o||''))
const pillClass = (o) => isFail(o) ? 'danger' : (isWarn(o) ? 'warn' : 'ok')
const pillLabel = (o) => isFail(o) ? '불량' : (isWarn(o) ? '주의' : '양호')
const LABELS = {light:'등화',brake:'브레이크',airBrake:'에어브레이크',tire:'타이어',steering:'조향',securement:'고박',engineOil:'엔진오일',seatbelt:'안전벨트',extinguisher:'소화기',triangle:'삼각대'}
function issueItems(it){ return Object.keys(LABELS).filter(k => /FAIL|BAD|NG|불량|WARN|CAUTION|주의/i.test(String(it[k]||''))).map(k=>LABELS[k]) }
async function load(){ err.value=''; try{ data.value = await Api.menuInspectionHistory(range.value, result.value) }catch(e){ err.value=errText(e)} }
onMounted(load)
</script>

<style scoped>
.pill.warn{ background:#FEF3C7; color:#B45309; }
</style>
