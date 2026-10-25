<template>
  <!-- 26.10.24 일일 처리량 보고서: 날짜를 고르면 하루치 집계를 보여 주고 Excel(.xlsx)로 내려받는다 (읽기 전용) -->
  <div class="drp" :class="{ 'is-dark': dark }">
    <div class="drp-bar">
      <label>보고 일자
        <input v-model="date" type="date" class="drp-input" :max="today" />
      </label>
      <button class="drp-btn" :disabled="loading" @click="load">조회</button>
      <button class="drp-btn primary" :disabled="loading || !report" @click="download">Excel 내려받기</button>
      <span v-if="report" class="drp-muted">{{ report.scope }}</span>
    </div>
    <p v-if="message" class="drp-msg">{{ message }}</p>
    <table v-if="report" class="drp-table">
      <thead><tr><th>구분</th><th>항목</th><th class="num">건수</th></tr></thead>
      <tbody>
        <tr v-for="(r, i) in report.rows" :key="i">
          <td>{{ i === 0 || report.rows[i - 1].section !== r.section ? r.section : '' }}</td>
          <td>{{ r.label }}</td>
          <td class="num">{{ r.count }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else-if="!loading" class="drp-muted">불러오는 중...</p>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { dailyReport, downloadDailyReport } from '@/utils/dailyReportApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'

defineProps({ dark: { type: Boolean, default: false } })

const today = new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10) // 한국 날짜
const date = ref(today)
const report = ref(null)
const loading = ref(false)
const message = ref('')

async function load() {
  loading.value = true
  message.value = ''
  try {
    report.value = await dailyReport(date.value)
  } catch (err) {
    report.value = null
    message.value = friendlyError(err, '보고서를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

async function download() {
  try {
    await downloadDailyReport(date.value)
  } catch (err) {
    message.value = friendlyError(err, 'Excel 파일을 받지 못했습니다.')
  }
}

onMounted(load)
</script>

<style scoped>
.drp { display: flex; flex-direction: column; gap: 10px; }
.drp-bar { display: flex; gap: 8px; align-items: flex-end; flex-wrap: wrap; }
.drp-bar label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
.drp-input { border: 1px solid #c9d3e0; border-radius: 6px; padding: 6px 8px; font-size: 13px; }
.drp-btn { border: 1px solid #c9d3e0; background: #fff; border-radius: 6px; padding: 7px 14px; font-size: 13px; cursor: pointer; }
.drp-btn.primary { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; font-weight: 700; }
.drp-btn:disabled { opacity: .5; cursor: default; }
.drp-muted { color: #6b7a90; font-size: 12.5px; }
.drp-msg { margin: 0; color: #c0392b; font-size: 13px; }
.drp-table { width: 100%; max-width: 560px; border-collapse: collapse; font-size: 13.5px; }
.drp-table th, .drp-table td { padding: 8px 10px; border-bottom: 1px solid #e3e8ef; text-align: left; }
.drp-table th { color: #6b7a90; font-weight: 600; }
.drp-table .num { text-align: right; font-variant-numeric: tabular-nums; }
.is-dark .drp-input, .is-dark .drp-btn { background: #0b1d36; border-color: #2a4d76; color: #e6eff7; }
.is-dark .drp-btn.primary { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; }
.is-dark .drp-table th, .is-dark .drp-table td { border-bottom-color: #1d3b60; }
.is-dark .drp-muted { color: #9fb0c6; }
.is-dark .drp-msg { color: #fca5a5; }
</style>
