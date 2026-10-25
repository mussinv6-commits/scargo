<template>
  <!-- 26.10.24 업체별 실적: 오더 · 인계 · 지연률 · 불합격률 · 등급 (ADMIN · CONTROL) -->
  <div class="cs">
    <div class="cs-bar">
      <label>기간
        <select v-model.number="days" class="cs-in" @change="load">
          <option :value="7">최근 7일</option><option :value="30">최근 30일</option><option :value="90">최근 90일</option>
        </select>
      </label>
      <label class="cs-chk"><input v-model="onlyActive" type="checkbox" /> 실적 있는 업체만</label>
      <button class="cs-btn" :disabled="!shown.length" @click="exportRows">Excel 내보내기</button>
      <span v-if="message" class="cs-msg">{{ message }}</span>
    </div>
    <table class="cs-table">
      <thead><tr><th>업체</th><th>등급</th><th>오더</th><th>인계 완료</th><th>지연률</th><th>평균 운송(분)</th><th>점검</th><th>불합격률</th></tr></thead>
      <tbody>
        <tr v-if="!shown.length"><td colspan="8" class="cs-empty">{{ loading ? '불러오는 중...' : '표시할 업체가 없습니다.' }}</td></tr>
        <tr v-for="r in shown" :key="r.companyId">
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

const days = ref(30)
const rows = ref([])
const loading = ref(false)
const message = ref('')
const onlyActive = ref(true)
const shown = computed(() => (onlyActive.value ? rows.value.filter((r) => r.orders > 0 || r.inspections > 0) : rows.value))
const pct = (v) => (v === null || v === undefined ? '-' : v + '%')

async function load() {
  loading.value = true
  message.value = ''
  try { rows.value = await companyScorecard(days.value) } catch (err) { rows.value = []; message.value = friendlyError(err, '업체별 실적을 불러오지 못했습니다.') } finally { loading.value = false }
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
.cs-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.cs-table th, .cs-table td { padding: 7px 8px; border-bottom: 1px solid #e8ecf2; text-align: right; white-space: nowrap; }
.cs-table th:first-child, .cs-table td:first-child { text-align: left; } .cs-table th { color: #6b7a90; font-weight: 600; }
.cs-empty { text-align: center !important; color: #6b7a90; } .bad { color: #e5484d; font-weight: 700; }
.cs-grade { display: inline-block; min-width: 26px; text-align: center; border-radius: 6px; padding: 2px 8px; font-weight: 700; color: #fff; }
.gA { background: #2fb57a; } .gB { background: #ff9f1c; } .gC { background: #e5484d; } .gx { background: #9aa7b8; }
.cs-note { margin: 10px 0 0; color: #6b7a90; font-size: 12px; }
</style>
