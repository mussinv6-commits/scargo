<template>
  <!-- 26.10.29 인수 내역: 허브 인계(앱 인수자 서명 · 웹 인계 확정) 기록과 인수증 (A4 양식 PDF 다운로드 · 인쇄) -->
  <section class="hv">
    <div class="hv-bar">
      <label>기간
        <span class="hv-range"><input v-model="from" type="date" class="hv-in" :max="to || today" /><span>~</span><input v-model="to" type="date" class="hv-in" :min="from" :max="today" /></span>
      </label>
      <label class="hv-q">검색<input v-model.trim="q" class="hv-in" placeholder="오더 · 컨테이너 · 차량 · 기사 · 인수자 · 씰" @keyup.enter="load" /></label>
      <button class="hv-btn primary" :disabled="loading" @click="load">조회</button>
      <span class="hv-count">{{ loading ? '불러오는 중…' : `${rows.length}건` }}</span>
      <span v-if="message" class="hv-msg">{{ message }}</span>
    </div>
    <div class="hv-scroll">
      <table class="hv-table nonum">
        <thead><tr><th class="no">번호</th><th>인계 일시</th><th>오더</th><th>컨테이너</th><th>차량</th><th v-if="!carrier">운송사</th><th>기사</th><th>인수처</th><th>인수자</th><th>서명</th><th>확인</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!pg.rows.length"><td :colspan="carrier ? 11 : 12" class="hv-empty">{{ loading ? '불러오는 중…' : '인수 기록이 없습니다.' }}</td></tr>
          <tr v-for="(r, i) in pg.rows" :key="r.handoverId">
            <td class="no">{{ pg.no(i) }}</td>
            <td>{{ fmtDateTime(r.completedAt) }}</td>
            <td>{{ r.orderNo }}</td>
            <td><b>{{ r.containerNo }}</b></td>
            <td>{{ r.vehicleNo || '-' }}</td>
            <td v-if="!carrier">{{ r.companyName || '-' }}</td>
            <td>{{ r.driverName || '-' }}</td>
            <td>{{ r.hubName || '-' }}</td>
            <td>{{ r.receiverName || '-' }}</td>
            <td><span class="hv-pill" :class="r.signatureUrl ? 'ok' : 'no'">{{ r.signatureUrl ? '있음' : '없음' }}</span></td>
            <td><span class="hv-pill" :class="allOk(r) ? 'ok' : 'warn'">{{ allOk(r) ? '이상 없음' : '특이사항' }}</span></td>
            <td><button class="hv-btn" @click="open(r)">인수증</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <AdminPager v-model="pg.page" :page-count="pg.pageCount" />

    <div v-if="cur" class="hv-modal" @click.self="close">
      <div class="hv-dlg" role="dialog" aria-modal="true" aria-label="인수증">
        <div class="hv-dlg-head">
          <b>인수증 · {{ cur.containerNo }}</b>
          <span class="hv-sub">{{ receiptNo(cur) }}</span>
          <span class="sp"></span>
          <button class="hv-btn primary" :disabled="!canvas" @click="pdf"><i class="bi bi-download"></i> PDF 다운로드</button>
          <button class="hv-btn" :disabled="!canvas" @click="print"><i class="bi bi-printer"></i> 인쇄</button>
          <button class="hv-btn" @click="close">닫기</button>
        </div>
        <div class="hv-paper">
          <p v-if="!preview" class="hv-empty">인수증을 만드는 중…</p>
          <img v-else :src="preview" alt="인수증 미리보기" />
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AdminPager from '@/components/admin/AdminPager.vue'
import { usePaged } from '@/utils/usePaged.js'
import { friendlyError } from '@/utils/apiHelpers.js'
import { authState } from '@/auth/authState.js'
import { handoverList, loadUploadImage } from '@/utils/handoverApi.js'
import { drawReceipt, downloadPdf, printCanvas, receiptFileName, receiptNo, fmtDateTime } from '@/utils/handoverReceipt.js'

const ymd = (d) => new Date(d.getTime() + 9 * 3600 * 1000).toISOString().slice(0, 10) // 한국 날짜
const today = ymd(new Date())
const from = ref(ymd(new Date(Date.now() - 29 * 86400000)))
const to = ref(today)
const q = ref('')
const rows = ref([])
const loading = ref(false)
const message = ref('')
const carrier = computed(() => authState.user?.userType === 'CORPORATE_APPROVED') // 운송사는 자기 회사만 → 운송사 칸 숨김
const pg = usePaged(rows, 10)
const allOk = (r) => r.containerNoConfirmed && r.sealOk && r.damageFree

async function load() {
  if (from.value && to.value && from.value > to.value) { message.value = '종료일이 시작일보다 빠릅니다.'; return }
  loading.value = true; message.value = ''
  try { rows.value = await handoverList({ from: from.value, to: to.value, q: q.value }); pg.page = 1 }
  catch (e) { rows.value = []; message.value = friendlyError(e, '인수 내역을 불러오지 못했습니다.') }
  finally { loading.value = false }
}

const cur = ref(null), canvas = ref(null), preview = ref('')
async function open(r) {
  cur.value = r; canvas.value = null; preview.value = ''
  try { const c = await drawReceipt(r, loadUploadImage); if (cur.value !== r) return; canvas.value = c; preview.value = c.toDataURL('image/png') }
  catch (e) { message.value = friendlyError(e, '인수증을 만들지 못했습니다.'); cur.value = null }
}
function close() { cur.value = null; canvas.value = null; preview.value = '' }
function pdf() { if (canvas.value) downloadPdf(canvas.value, receiptFileName(cur.value)) }
function print() { if (canvas.value && !printCanvas(canvas.value, receiptNo(cur.value))) message.value = '팝업이 막혀 인쇄 창을 열지 못했습니다. 브라우저에서 팝업을 허용해 주세요.' }

onMounted(load)
</script>

<style scoped>
.hv { background: #fff; border: 1px solid #e3e8ef; border-radius: 12px; padding: 14px 16px; color: #1f2937; }
.hv-bar { display: flex; gap: 12px; align-items: flex-end; flex-wrap: wrap; font-size: 13px; margin-bottom: 10px; }
.hv-bar label { display: flex; flex-direction: column; gap: 4px; }
.hv-range { display: inline-flex; align-items: center; gap: 6px; }
.hv-q input { width: 260px; }
.hv-in { border: 1px solid #c9d3e0; background: #fff; border-radius: 6px; padding: 6px 10px; font-size: 13px; }
.hv-btn { border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 6px; padding: 6px 12px; font-size: 13px; cursor: pointer; white-space: nowrap; }
.hv-btn:hover:not(:disabled) { background: #eef2f7; }
.hv-btn.primary { background: #1d4ed8; border-color: #1d4ed8; color: #fff; }
.hv-btn.primary:hover:not(:disabled) { background: #3b6ef0; }
.hv-btn:disabled { opacity: .55; cursor: default; }
.hv-count { color: #6b7280; padding-bottom: 7px; }
.hv-msg { color: #c81e2c; padding-bottom: 7px; }
.hv-scroll { overflow-x: auto; }
.hv-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.hv-table th { background: #f3f5f9; font-weight: 700; color: #3d4863; }
.hv-table th, .hv-table td { padding: 8px; border-bottom: 1px solid #eef1f5; text-align: left; white-space: nowrap; }
.hv-table .no { text-align: center; width: 52px; }
.hv-empty { text-align: center !important; color: #6b7280; padding: 18px !important; }
.hv-pill { display: inline-block; padding: 1px 9px; border-radius: 999px; font-size: 12px; font-weight: 700; }
.hv-pill.ok { background: #e3f6ea; color: #1b7a3d; } .hv-pill.no { background: #eef1f6; color: #6b7590; } .hv-pill.warn { background: #fde4e4; color: #c62828; }
.hv-modal { position: fixed; inset: 0; background: rgba(15, 23, 42, .55); z-index: 1200; display: flex; align-items: center; justify-content: center; padding: 20px; }
.hv-dlg { background: #fff; border-radius: 12px; width: min(900px, 100%); max-height: calc(100vh - 40px); display: flex; flex-direction: column; overflow: hidden; }
.hv-dlg-head { display: flex; align-items: center; gap: 8px; padding: 12px 16px; border-bottom: 1px solid #e3e8ef; flex-wrap: wrap; }
.hv-dlg-head b { font-size: 15px; } .hv-sub { color: #6b7280; font-size: 12.5px; } .sp { flex: 1; }
.hv-paper { flex: 1 1 auto; min-height: 0; overflow: auto; background: #e9edf3; padding: 16px; display: flex; justify-content: center; align-items: flex-start; }
.hv-paper img { display: block; flex: none; align-self: flex-start; width: 100%; max-width: 760px; height: auto !important; max-height: none !important; object-fit: contain; box-shadow: 0 4px 16px rgba(20, 33, 61, .18); background: #fff; }
</style>
