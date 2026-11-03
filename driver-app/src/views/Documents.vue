<template>
  <div class="screen">
    <header class="topbar"><button class="icon-btn" type="button" @click="$router.back()">←</button><h1>문서함</h1></header>
    <main class="content">
      <div v-if="soon" class="banner warn"><div><b>만료 임박/만료 {{ soon }}건</b><span>만료일을 확인하고 서류를 갱신해 주세요.</span></div></div>
      <div v-if="err" class="error-box">{{ err }}</div>
      <section class="card">
        <div v-if="!docs.length" class="empty">등록된 서류가 없습니다.</div>
        <div v-for="d in docs" :key="d.documentId" style="padding:10px 0;border-bottom:1px solid var(--line)">
          <div class="between">
            <div class="stack"><b>{{ d.title }}</b><span class="small muted">{{ d.docType }} · {{ d.expiresAt || '만료일 없음' }}</span></div>
            <span class="pill" :class="d.status==='EXPIRED'?'danger':(d.status==='SOON'?'warn':'ok')">{{ d.status }}</span>
          </div>
          <div class="row" style="gap:8px;margin-top:8px">
            <a v-if="d.filePath" class="btn sm" :href="fileUrl(d.filePath)" target="_blank">보기</a>
            <button class="btn sm" type="button" @click="remove(d)">삭제</button>
          </div>
        </div>
      </section>
      <section class="card">
        <b>서류 등록</b>
        <select class="input" v-model="form.docType">
          <option value="LICENSE">운전면허증</option>
          <option value="FREIGHT_QUAL">화물운송종사자격</option>
          <option value="VEHICLE_REG">자동차등록증</option>
          <option value="CARGO_INSURANCE">적재물배상책임보험</option>
          <option value="OTHER">기타</option>
        </select>
        <input class="input" v-model="form.title" placeholder="제목 (비우면 기본명)" />
        <input class="input" type="date" v-model="form.expiresAt" />
        <input class="input" type="file" accept="image/*,application/pdf" @change="onFile" />
        <button class="btn primary" type="button" :disabled="busy" @click="save">{{ busy?'저장 중…':'저장' }}</button>
      </section>
    </main>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Api, errText, getApiBase, getToken } from '../lib/api'
const docs = ref([]); const err=ref(''); const busy=ref(false); const file=ref(null)
const form = reactive({ docType:'LICENSE', title:'', expiresAt:'' })
const soon = computed(() => docs.value.filter(d => d.status==='SOON'||d.status==='EXPIRED').length)
const fileUrl = (p) => (getApiBase()||'') + p
function onFile(e){ file.value = e.target.files?.[0] || null }
async function load(){ try{ docs.value = await Api.menuDocuments() }catch(e){ err.value=errText(e)} }
async function save(){
  err.value=''; busy.value=true
  try{
    const fd=new FormData(); fd.append('docType', form.docType)
    if(form.title) fd.append('title', form.title); if(form.expiresAt) fd.append('expiresAt', form.expiresAt)
    if(file.value) fd.append('file', file.value)
    await Api.menuUpsertDocument(fd); form.title=''; form.expiresAt=''; file.value=null; await load()
  }catch(e){ err.value=errText(e)} finally{ busy.value=false }
}
async function remove(d){ if(!confirm('삭제할까요?')) return; try{ await Api.menuDeleteDocument(d.documentId); await load() }catch(e){ err.value=errText(e)} }
onMounted(load)
</script>
