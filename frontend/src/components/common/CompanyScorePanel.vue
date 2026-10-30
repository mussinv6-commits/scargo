<template>
  <!-- 26.10.24 업체별 실적: 오더 · 인계 · 지연률 · 불합격률 · 등급 (ADMIN · CONTROL) -->
  <div class="cs">
    <div class="cs-bar">
      <!-- 26.10.28 기간을 달력으로 / 26.10.29 서버가 시작일 · 종료일(from/to)을 받아 종료일도 고를 수 있음 -->
      <label>기간
        <span class="cs-range">
          <input v-model="from" type="date" class="cs-in" :max="to || today" />
          <span>~</span>
          <input v-model="to" type="date" class="cs-in" :min="from" :max="today" />
        </span>
      </label>
      <button class="cs-btn" :disabled="loading" @click="load">조회</button>
      <label class="cs-chk"><input v-model="onlyActive" type="checkbox" /> 실적 있는 업체만</label>
      <button class="cs-btn" :disabled="!shown.length" @click="exportRows">Excel 내보내기</button>
      <span v-if="message" class="cs-msg">{{ message }}</span>
    </div>
    <table class="cs-table">
      <thead><tr><th class="no">번호</th><th>업체</th><th>등급</th><th>오더</th><th>인계 완료</th><th>지연률</th><th>평균 운송(분)</th><th>점검</th><th>불합격률</th></tr></thead>
      <tbody>
        <tr v-if="!shown.length"><td colspan="9" class="cs-empty">{{ loading ? '불러오는 중...' : '표시할 업체가 없습니다.' }}</td></tr>
        <tr v-for="(r, i) in shown" :key="r.companyId">
          <td class="no">{{ i + 1 }}</td>
          <td>{{ r.companyName }}</td>
          <td><span class="cs-grade" :class="'g' + (r.grade === '-' ? 'x' : r.grade)">{{ r.grade }}</span></td>
          <td>{{ r.orders }}</td><td>{{ r.handedOver }}</td>
          <td :class="{ bad: r.lateRate > 15 }">{{ pct(r.lateRate) }}</td>
          <td>{{ r.avgTransitMin ?? '-' }}</td><td>{{ r.inspections }}</td>
          <td :class="{ bad: r.failRate > 15 }">{{ pct(r.failRate) }}</td>
        </tr>
      </tbody>
    </table>
    <p class="cs-note">등급: 지연률·불합격률 중 나쁜 쪽이 5% 이하 A, 15% 이하 B, 초과 C (자료 없으면 -)</p>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { companyScorecard } from '@/utils/opsApi.js'
import { exportExcel } from '@/utils/exportExcel.js'
import { friendlyError } from '@/utils/apiHelpers.js'

const ymd = (d) => new Date(d.getTime() + 9 * 3600 * 1000).toISOString().slice(0, 10) // 한국 날짜
const today = ymd(new Date())
const from = ref(ymd(new Date(Date.now() - 29 * 86400000)))
const to = ref(today)
const rows = ref([])
const loading = ref(false)
const message = ref('')
const onlyActive = ref(true)
// 26.10.28 정렬: 1차 업체명 가나다순, 같은 이름이면 2차 등급 높은 순 (A → B → C → 자료 없음)
const GRADE_ORDER = { A: 0, B: 1, C: 2 }
const shown = computed(() => {
  const list = onlyActive.value ? rows.value.filter((r) => r.orders > 0 || r.inspections > 0) : [...rows.value]
  return list.sort((a, b) => String(a.companyName || '').localeCompare(String(b.companyName || ''), 'ko') || (GRADE_ORDER[a.grade] ?? 9) - (GRADE_ORDER[b.grade] ?? 9))
})
const pct = (v) => (v === null || v === undefined ? '-' : v + '%')

async function load() {
  if (!from.value || !to.value) { message.value = '시작일과 종료일을 고르세요.'; return }
  if (from.value > to.value) { message.value = '종료일이 시작일보다 빠릅니다.'; return }
  loading.value = true
  message.value = ''
  // days: 아직 새로 빌드하지 않은 백엔드(from/to 모름)를 위한 예비값 (시작일 ~ 오늘)
  const days = Math.max(1, Math.round((new Date(today + 'T00:00:00') - new Date(from.value + 'T00:00:00')) / 86400000) + 1)
  try { rows.value = await companyScorecard({ from: from.value, to: to.value, days }) } catch (err) { rows.value = []; message.value = friendlyError(err, '업체별 실적을 불러오지 못했습니다.') } finally { loading.value = false }
}
function exportRows() {
  exportExcel('업체별실적', [
    { key: 'companyName', label: '업체' }, { key: 'grade', label: '등급' }, { key: 'orders', label: '오더' }, { key: 'handedOver', label: '인계 완료' },
    { key: 'lateRate', label: '지연률(%)' }, { key: 'avgTransitMin', label: '평균 운송(분)' }, { key: 'inspections', label: '점검' }, { key: 'failRate', label: '불합격률(%)' },
  ], shown.value)
}
onMounted(load)
</script>

<style scoped>
.cs { background: #fff; border: 1px solid #d5dbe5; border-radius: 10px; padding: 12px 14px; overflow-x: auto; }
.cs-bar { display: flex; gap: 12px; align-items: flex-end; flex-wrap: wrap; font-size: 13px; margin-bottom: 10px; }
.cs-bar label { display: flex; flex-direction: column; gap: 4px; } .cs-chk { flex-direction: row !important; align-items: center; }
.cs-in, .cs-btn { border: 1px solid #c9d3e0; background: #fff; border-radius: 6px; padding: 6px 10px; font-size: 13px; } .cs-btn { cursor: pointer; } .cs-btn:disabled { opacity: .5; }
.cs-msg { color: #c0392b; }
.cs-range { display: inline-flex; align-items: center; gap: 6px; } .cs-range input:disabled { background: #f1f4f8; color: #64748b; } /* 26.10.28 */
.cs-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.cs-table th, .cs-table td { padding: 7px 8px; border-bottom: 1px solid #e8ecf2; text-align: right; white-space: nowrap; }
.cs-table th:first-child, .cs-table td:first-child { text-align: left; } .cs-table th { color: #6b7a90; font-weight: 600; }
.cs-empty { text-align: center !important; color: #6b7a90; } .bad { color: #e5484d; font-weight: 700; }
.cs-grade { display: inline-block; min-width: 26px; text-align: center; border-radius: 6px; padding: 2px 8px; font-weight: 700; color: #fff; }
.gA { background: #2fb57a; } .gB { background: #ff9f1c; } .gC { background: #e5484d; } .gx { background: #9aa7b8; }
.cs-note { margin: 10px 0 0; color: #6b7a90; font-size: 12px; }
/* 26.10.28 번호 칸 */
.cs-table .no { width: 52px; text-align: center; color: #6b7a90; }
.cs-sort { display: inline-flex; border: 1px solid #c9d3e0; border-radius: 8px; overflow: hidden; align-self: flex-end; }
.cs-sort button { border: 0; background: #fff; color: #112847; padding: 6px 14px; font-size: 13px; font-weight: 600; cursor: pointer; }
.cs-sort button + button { border-left: 1px solid #c9d3e0; }
.cs-sort button.on { background: #112847; color: #fff; font-weight: 700; }
</style>
