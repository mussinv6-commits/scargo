<template>
  <!-- 26.11.02 보고서 미리보기 창: 인수증과 같은 버튼(PDF 다운로드 · 인쇄 · 닫기). 내용은 엑셀로 내려받는 보고서와 같다 -->
  <div v-if="reportView.open" class="rpd-modal" @click.self="close">
    <div class="rpd-dlg" role="dialog" aria-modal="true" aria-label="보고서 미리보기">
      <div class="rpd-head">
        <b>{{ title }}</b>
        <span class="rpd-sub">{{ pages.length ? pages.length + '쪽' : '' }}</span>
        <span class="sp"></span>
        <span class="rpd-zoom"><button class="rpd-btn" :disabled="zoom <= 70" @click="zoom -= 10" title="축소">−</button><span>{{ zoom }}%</span><button class="rpd-btn" :disabled="zoom >= 200" @click="zoom += 10" title="확대">＋</button></span>
        <button class="rpd-btn primary" :disabled="!pages.length" @click="pdf"><i class="bi bi-download"></i> PDF 다운로드</button>
        <button class="rpd-btn" :disabled="!pages.length" @click="print"><i class="bi bi-printer"></i> 인쇄</button>
        <button class="rpd-btn" @click="close">닫기</button>
      </div>
      <p v-if="msg" class="rpd-msg">{{ msg }}</p>
      <div class="rpd-paper">
        <p v-if="!previews.length" class="rpd-empty">보고서를 만드는 중…</p>
        <img :style="{ width: imgW }" v-for="(u, i) in previews" :key="i" :src="u" :alt="'보고서 ' + (i + 1) + '쪽'" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { reportView } from '@/utils/xlsxLite.js'
import { drawReportPages, downloadPagesPdf, printPages } from '@/utils/sheetCanvas.js'

const pages = ref([]), previews = ref([]), msg = ref(''), zoom = ref(100)
const imgW = computed(() => zoom.value === 100 ? 'min(1100px, 100%)' : Math.round(11 * zoom.value) + 'px')
const title = computed(() => {
  const r = reportView.sheets && reportView.sheets[0] && reportView.sheets[0].rows && reportView.sheets[0].rows[0]
  const c = r && r[0]; const t = c && typeof c === 'object' ? c.v : c
  return t ? String(t) : '보고서'
})
const fileName = () => (reportView.name || title.value || '보고서') + '.pdf'

function close() { reportView.open = false; pages.value = []; previews.value = []; msg.value = '' }
function pdf() { if (pages.value.length) downloadPagesPdf(pages.value, fileName()) }
function print() { if (pages.value.length && !printPages(pages.value, title.value)) msg.value = '팝업이 막혀 인쇄 창을 열지 못했습니다. 브라우저에서 팝업을 허용해 주세요.' }

watch(() => reportView.open, (on) => {
  if (!on) return
  pages.value = []; previews.value = []; msg.value = ''; zoom.value = 100
  setTimeout(() => { // 창이 먼저 뜬 뒤 그린다
    try { const cv = drawReportPages(reportView.sheets || []); pages.value = cv; previews.value = cv.map((c) => c.toDataURL('image/png')) }
    catch (e) { msg.value = '보고서를 만들지 못했습니다. ' + (e && e.message ? e.message : ''); }
  }, 30)
})
const onKey = (e) => { if (e.key === 'Escape' && reportView.open) close() }
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<style scoped>
.rpd-modal { position: fixed; inset: 0; background: rgba(15, 23, 42, .55); z-index: 3000; display: flex; align-items: center; justify-content: center; padding: 20px; }
.rpd-dlg { background: #fff; border-radius: 12px; width: min(1240px, 100%); height: calc(100vh - 40px); display: flex; flex-direction: column; overflow: hidden; }
.rpd-head { display: flex; align-items: center; gap: 8px; padding: 12px 16px; border-bottom: 1px solid #e3e8ef; flex-wrap: wrap; }
.rpd-head b { font-size: 15px; } .rpd-sub { color: #6b7280; font-size: 12.5px; } .sp { flex: 1; }
.rpd-btn { border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 6px; padding: 6px 12px; font-size: 13px; cursor: pointer; white-space: nowrap; }
.rpd-btn:hover:not(:disabled) { background: #eef2f7; }
.rpd-btn.primary { background: #1d4ed8; border-color: #1d4ed8; color: #fff; }
.rpd-btn.primary:hover:not(:disabled) { background: #3b6ef0; }
.rpd-btn:disabled { opacity: .55; cursor: default; }
.rpd-msg { margin: 0; padding: 8px 16px; color: #c81e2c; font-size: 13px; }
.rpd-paper { flex: 1 1 auto; min-height: 0; overflow: auto; background: #e9edf3; padding: 16px; display: flex; flex-direction: column; align-items: stretch; gap: 16px; }
.rpd-paper img { display: block; flex: none; margin: 0 auto; max-width: none; height: auto; background: #fff; box-shadow: 0 4px 16px rgba(20, 33, 61, .18); }
.rpd-zoom { display: inline-flex; align-items: center; gap: 4px; font-size: 12.5px; color: #374151; } .rpd-zoom .rpd-btn { padding: 4px 9px; }
.rpd-empty { color: #6b7280; margin: 40px 0; }
</style>
