<template>
  <div class="screen">
    <header class="topbar"><button class="icon-btn" type="button" @click="$router.back()">←</button><h1>고객센터·긴급연락</h1></header>
    <main class="content">
      <section class="card">
        <b>긴급 연락망</b>
        <a v-for="c in data?.contacts||[]" :key="c.key+c.label" class="btn" :class="c.urgent?'danger':''" :href="'tel:'+String(c.phone).replace(/[^0-9+]/g,'')">{{ c.urgent?'🚨 ':'' }}{{ c.label }} · {{ c.phone }}</a>
      </section>
      <section class="card">
        <b>문의하기</b>
        <select class="input" v-model="form.category"><option>배차·운행</option><option>정산·수당</option><option>앱 오류</option><option>서류·자격</option><option>OTHER</option></select>
        <input class="input" v-model="form.subject" placeholder="제목" />
        <textarea class="input" v-model="form.body" placeholder="문의 내용 (5자 이상)"></textarea>
        <p v-if="err" class="hint bad">{{ err }}</p>
        <button class="btn primary" type="button" :disabled="busy" @click="send">{{ busy?'전송 중…':'문의 등록' }}</button>
      </section>
      <section class="card tight">
        <b style="font-size:14px">FAQ</b>
        <div v-for="(f,i) in data?.faq||[]" :key="i" style="padding:8px 0;border-bottom:1px solid var(--line)">
          <b class="small">{{ f.q }}</b><div class="small muted">{{ f.a }}</div>
        </div>
      </section>
      <section class="card tight" v-if="(data?.inquiries||[]).length">
        <b style="font-size:14px">내 문의</b>
        <div v-for="q in data.inquiries" :key="q.inquiryId" class="small" style="padding:6px 0">{{ q.subject }} · {{ q.status }}</div>
      </section>
    </main>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Api, errText } from '../lib/api'
const data = ref(null); const busy=ref(false); const err=ref('')
const form = reactive({ category:'배차·운행', subject:'', body:'' })
onMounted(async()=>{ try{ data.value = await Api.menuSupport() }catch(e){ err.value=errText(e)} })
async function send(){ err.value=''; busy.value=true; try{ await Api.menuInquiry({...form}); form.subject=''; form.body=''; data.value = await Api.menuSupport() }catch(e){err.value=errText(e)} finally{busy.value=false} }
</script>
<style scoped>textarea.input{min-height:90px;height:auto;padding:12px 14px}</style>
