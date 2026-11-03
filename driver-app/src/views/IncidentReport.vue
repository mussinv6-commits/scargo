<template>
  <div class="screen">
    <header class="topbar"><button class="icon-btn" type="button" @click="$router.back()">←</button><h1>사고·이상 신고</h1></header>
    <main class="content">
      <div class="banner danger"><div><b>관제·허브에 즉시 전달</b><span>웹 이상 상황 관리에 예외로 등록됩니다.</span></div></div>
      <div class="chips">
        <button v-for="c in cats" :key="c.k" type="button" class="chip" :class="{on: form.category===c.k}" @click="form.category=c.k">{{ c.l }}</button>
      </div>
      <section class="card">
        <label class="field">관련 운행
          <select class="input" v-model="form.runKey">
            <option value="">선택 안 함</option>
            <option v-for="r in runs" :key="r.key" :value="r.key">{{ r.label }} · {{ r.route }}</option>
          </select>
        </label>
        <label class="field">발생 위치
          <input class="input" v-model="form.locationText" placeholder="위치 설명" />
          <span class="hint">{{ geoHint }}</span>
        </label>
        <div class="field">심각도
          <div class="chips">
            <button type="button" class="chip" :class="{on: form.severity==='WARNING'}" @click="form.severity='WARNING'">주의</button>
            <button type="button" class="chip" :class="{on: form.severity==='CRITICAL'}" @click="form.severity='CRITICAL'">긴급</button>
          </div>
        </div>
        <label class="field">내용
          <textarea class="input" v-model="form.message" rows="4" placeholder="상황을 적어 주세요 (5자 이상)"></textarea>
        </label>
        <label class="btn" style="height:auto;padding:12px">📷 사진 추가<input type="file" accept="image/*" multiple hidden @change="onFiles" /></label>
        <div v-if="files.length" class="small muted">선택 {{ files.length }}장 (최대 4 · 장당 8MB)</div>
        <p v-if="err" class="hint bad">{{ err }}</p>
        <button class="btn danger" type="button" :disabled="busy" @click="submit">{{ busy ? '접수 중…' : '신고 접수' }}</button>
      </section>
      <section class="card tight">
        <b style="font-size:14px">내 신고 이력</b>
        <div v-if="!hist.length" class="empty" style="padding:16px">아직 신고한 내역이 없습니다.</div>
        <div v-for="h in hist" :key="h.incidentId" class="listish">
          <b class="small">{{ catLabel(h.category) }} · {{ h.exceptionStatus || 'OPEN' }}</b>
          <span class="small muted">{{ h.message }}</span>
        </div>
      </section>
    </main>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Api, errText } from '../lib/api'
const cats = [{k:'ACCIDENT',l:'교통사고'},{k:'BREAKDOWN',l:'차량고장'},{k:'CARGO_DAMAGE',l:'화물파손'},{k:'OFF_ROUTE',l:'경로이탈'},{k:'OTHER',l:'기타'}]
const form = reactive({ category:'ACCIDENT', severity:'CRITICAL', message:'', locationText:'', runKey:'', lat:null, lng:null })
const runs = ref([]); const hist = ref([]); const files = ref([]); const busy = ref(false); const err = ref(''); const geoHint = ref('위치 확인 중…')
const catLabel = (k) => (cats.find(c=>c.k===k)||{}).l || k
function onFiles(e){ files.value = [...(e.target.files||[])].slice(0,4) }
onMounted(async () => {
  try { const list = await Api.menuIncidentRuns(); runs.value = list.map(r => ({ key: (r.orderId?('O'+r.orderId):('A'+r.assignmentId)), ...r })) } catch {}
  try { hist.value = await Api.menuIncidents() } catch {}
  if (navigator.geolocation) navigator.geolocation.getCurrentPosition(p => {
    form.lat = p.coords.latitude; form.lng = p.coords.longitude
    geoHint.value = `GPS ${form.lat.toFixed(5)}, ${form.lng.toFixed(5)}`
    if (!form.locationText) form.locationText = '현재 GPS 위치'
  }, () => { geoHint.value = 'GPS를 가져오지 못했습니다. 위치를 직접 적어 주세요.' }, { enableHighAccuracy:true, timeout:8000 })
  else geoHint.value = '이 기기에서 GPS를 사용할 수 없습니다.'
})
async function submit(){
  err.value=''; busy.value=true
  try {
    const fd = new FormData()
    fd.append('category', form.category); fd.append('severity', form.severity); fd.append('message', form.message)
    if (form.locationText) fd.append('locationText', form.locationText)
    if (form.lat!=null) fd.append('lat', String(form.lat)); if (form.lng!=null) fd.append('lng', String(form.lng))
    if (form.runKey.startsWith('O')) fd.append('orderId', form.runKey.slice(1))
    if (form.runKey.startsWith('A')) fd.append('assignmentId', form.runKey.slice(1))
    files.value.forEach(f => fd.append('files', f))
    await Api.menuReportIncident(fd)
    form.message=''; files.value=[]
    hist.value = await Api.menuIncidents()
    alert('신고가 접수되었습니다. 웹 이상 상황 관리에서 확인할 수 있습니다.')
  } catch(e){ err.value = errText(e) } finally { busy.value=false }
}
</script>
<style scoped>
.listish{display:flex;flex-direction:column;gap:2px;padding:10px 0;border-bottom:1px solid var(--line)}
.listish:last-child{border-bottom:0}
textarea.input{min-height:96px;padding:12px 14px;height:auto}
</style>
