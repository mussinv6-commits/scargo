<template>
  <div class="screen">
    <header class="topbar" style="flex-direction: column; align-items: stretch; padding: calc(var(--safe-t) + 16px) 20px 12px; gap: 12px">
      <h1 style="font-size: 22px">{{ branch ? '지선 운행 이력' : '운송 이력' }}</h1>
      <div class="chips">
        <button v-for="r in ranges" :key="r.key" type="button" :class="['chip', { on: range === r.key }]" @click="range = r.key; load()">{{ r.label }}</button>
      </div>
    </header>
    <main class="content">
      <div v-if="error" class="error-box">{{ error }}<div style="margin-top:8px"><button class="btn o" type="button" @click="load">다시 불러오기</button></div></div>
      <section v-if="data && !branch" class="card grid3">
        <div class="stat"><span>완료</span><b>{{ data.summary.count }}건</b></div>
        <div class="stat"><span>총 거리</span><b>{{ Number(data.summary.totalDistanceKm || 0).toFixed(1) }}km</b></div>
        <div class="stat"><span>과적 경고</span><b>{{ data.summary.overloadWarnings }}건</b></div>
      </section>
      <section v-if="data && branch" class="card grid3">
        <div class="stat"><span>완료</span><b>{{ data.summary.count }}건</b></div>
        <div class="stat"><span>기간</span><b>{{ rangeLabel }}</b></div>
        <div class="stat"><span>구분</span><b>지선</b></div>
      </section>
      <div v-if="loading" class="empty"><div class="spinner" style="margin: 0 auto"></div></div>
      <div v-else-if="data && !data.items.length" class="empty">이 기간에 완료한 운행이 없습니다.</div>
      <template v-if="!branch">
        <div v-for="o in data?.items || []" :key="o.orderId" class="card tight tap" role="button" @click="openReceipt(o)">
          <div class="between">
            <b class="mono" style="font-size: 15px">{{ o.containerNo }}</b>
            <span v-if="o.deviationCount" class="pill danger">경로 이탈 {{ o.deviationCount }}회</span>
            <span v-else-if="o.overloadWarning" class="pill danger">과적 경고</span>
            <span v-else class="pill ok">인계 완료</span>
          </div>
          <span style="font-size: 13px; color: var(--ink-2)">{{ o.originName }} → {{ o.destName }}</span>
          <span class="small faint">{{ o.gateOutAt ? dateTime(o.gateOutAt) + ' → ' : '인계 ' }}{{ dateTime(o.handedOverAt) }}<template v-if="o.distanceKm != null"> · {{ o.distanceKm }}km</template></span>
          <span class="small" style="color: var(--primary); font-weight: 700">인수증 보기 ›</span>
        </div>
      </template>
      <template v-else>
        <div v-for="t in data?.items || []" :key="t.assignmentId" class="card tight">
          <div class="between">
            <b style="font-size: 15px">LH#{{ t.assignmentId }} · {{ t.vehicleNo }}</b>
            <span class="pill ok">인수 확정</span>
          </div>
          <span style="font-size: 13px; color: var(--ink-2)">{{ t.fromCenterName }} → {{ t.toCenterName }}</span>
          <span class="small faint">롤테이너 {{ t.unitCount ?? t.plannedUnits ?? 0 }}개 · 씰 {{ t.domesticSealNo || '—' }}</span>
          <span class="small faint">출발 {{ dateTime(t.departedAt) }} → 도착 {{ dateTime(t.arrivedAt) }} → 인수 {{ dateTime(t.receivedAt) }}</span>
        </div>
      </template>
    </main>
    <div v-if="rc.open" class="overlay" role="dialog" aria-modal="true" aria-label="인수증" @click.self="closeReceipt">
      <div class="sheet" style="max-height: 92vh; gap: 10px">
        <div class="between"><b style="font-size: 17px">인수증</b><button class="btn sm" type="button" style="border: 0; background: transparent; font-size: 18px" aria-label="닫기" @click="closeReceipt">✕</button></div>
        <div v-if="rc.err" class="error-box">{{ rc.err }}</div>
        <div v-else-if="!rc.img" class="empty"><div class="spinner" style="margin: 0 auto"></div></div>
        <div v-else style="overflow: auto; background: #e9edf3; border-radius: 10px; padding: 8px"><img :src="rc.img" alt="인수증" style="width: 100%; display: block; background: #fff" /></div>
        <div class="row" style="gap: 8px">
          <button class="btn" type="button" style="flex: 1" @click="closeReceipt">닫기</button>
          <button v-if="!isNative && rc.canvas" class="btn primary" type="button" style="flex: 1" @click="savePdf">PDF 저장</button>
        </div>
        <span v-if="isNative && rc.img" class="small muted">인수증은 운송사 · 관리자 웹의 '인수 내역'에서 PDF 로 받을 수 있습니다.</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api, getApiBase, getToken, isNative, errText, Api } from '../lib/api'
import { drawReceipt, downloadPdf, receiptFileName } from '../lib/handoverReceipt'
import { dateTime } from '../lib/format'
import { isBranch } from '../lib/menu'

const branch = computed(() => isBranch())
const ranges = [{ key: 'today', label: '오늘' }, { key: 'week', label: '이번 주' }, { key: 'month', label: '이번 달' }]
const range = ref('today')
const rangeLabel = computed(() => (ranges.find(r => r.key === range.value) || {}).label || '')
const data = ref(null)
const loading = ref(false)
const error = ref('')
const rc = reactive({ open: false, img: '', err: '', canvas: null, row: null })

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = branch.value ? await Api.branchHistory(range.value) : await Api.history(range.value)
  } catch (e) { error.value = errText(e); data.value = null } finally { loading.value = false }
}

async function loadImg(u) {
  const r = await fetch(getApiBase() + u, { headers: { Authorization: `Bearer ${getToken()}` } })
  if (!r.ok) throw new Error('image ' + r.status)
  const src = URL.createObjectURL(await r.blob())
  return await new Promise((ok, no) => { const im = new Image(); im.onload = () => ok(im); im.onerror = no; im.src = src })
}
async function openReceipt(o) {
  Object.assign(rc, { open: true, img: '', err: '', canvas: null, row: null })
  try {
    const row = await api(`/api/app/handovers/${o.orderId}`)
    const c = await drawReceipt(row, loadImg)
    if (!rc.open) return
    Object.assign(rc, { row, canvas: c, img: c.toDataURL('image/png') })
  } catch (e) { rc.err = e?.status === 400 || e?.status === 404 ? '이 운송은 인수 기록이 없습니다.' : errText(e) }
}
function closeReceipt() { Object.assign(rc, { open: false, img: '', canvas: null }) }
function savePdf() { if (rc.canvas) downloadPdf(rc.canvas, receiptFileName(rc.row)) }
onMounted(load)
</script>
