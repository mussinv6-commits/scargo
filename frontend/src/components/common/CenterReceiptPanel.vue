<template>
  <!-- 26.10.23 지역센터 인수 확정 (지선 운행 ARRIVED → RECEIVED). CENTER_OP = 소속 센터, ADMIN = 전체. 확정 가능 여부는 서버가 다시 확인 -->
  <section class="crp">
    <div class="crp-toolbar">
      <div class="crp-tabs">
        <button v-for="t in TABS" :key="t.key" :class="{ on: status === t.key }" @click="status = t.key; load()">
          {{ t.label }}
        </button>
      </div>
      <button class="crp-btn" :disabled="loading" @click="load">새로고침</button>
    </div>
    <p v-if="message" class="crp-msg" :class="{ err: isError }">{{ message }}</p>
    <div class="crp-scroll">
      <table class="crp-table nonum">
        <thead><!-- 26.10.29 nonum: 관제사 화면(.sc2)에서 자동 번호 칸이 하나 더 생기지 않게 -->
          <tr><th>번호</th><th>운행</th><th>차량</th><th>출발</th><th>도착 센터</th><th>물품</th><th>상태</th><th>센터 도착</th><th>인수 확정</th><th>확정자</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td colspan="11" class="crp-empty">불러오는 중...</td></tr>
          <tr v-else-if="!rows.length"><td colspan="11" class="crp-empty">해당하는 지선 운행이 없습니다.</td></tr>
          <tr v-for="(r, i) in pg.rows" :key="r.assignmentId">
            <td>{{ pg.no(i) }}</td><!-- 26.10.28 QA#4 번호 1부터 -->
            <td>#{{ r.assignmentId }}</td>
            <td>{{ r.vehicleNo }}</td>
            <td>{{ r.fromCenterName }}</td>
            <td>{{ r.toCenterName }}</td>
            <td>{{ r.parcelCount }}건</td>
            <td><span class="crp-badge" :class="'s-' + r.status">{{ LEG_STATUS_LABEL[r.status] || r.status }}</span></td>
            <td>{{ formatDateTime(r.arrivedAt) }}</td>
            <td>{{ formatDateTime(r.receivedAt) }}</td>
            <!-- 26.10.23 확정자 (15장 이전 인수 기록은 확정자 없음) -->
            <td>{{ r.receivedByUserId || (r.status === 'RECEIVED' ? '기록 없음' : '-') }}</td>
            <td>
              <button v-if="canConfirm && r.status === 'ARRIVED'" class="crp-btn primary" :disabled="busy" @click="confirm(r)">인수 확정</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <AdminPager v-model="pg.page" :page-count="pg.pageCount" /><!-- 26.10.28 QA#4 10건 페이징 -->
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { LEG_STATUS_LABEL, centerReceipts, confirmCenterReceipt } from '@/utils/centerReceiptApi.js'
import { formatDateTime } from '@/utils/containerInspectionApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'
import AdminPager from '@/components/admin/AdminPager.vue'
import { usePaged } from '@/utils/usePaged.js'

defineProps({
  canConfirm: { type: Boolean, default: true },
})

const TABS = [
  { key: 'ARRIVED', label: '인수 대기' },
  { key: 'RECEIVED', label: '인수 완료' },
  { key: 'IN_TRANSIT', label: '오는 중' },
  { key: '', label: '전체' },
]
const status = ref('ARRIVED')
const rows = ref([])
const pg = usePaged(rows, 10)
const loading = ref(false)
const busy = ref(false)
const message = ref('')
const isError = ref(false)

async function load() {
  loading.value = true
  try {
    rows.value = await centerReceipts(status.value)
  } catch (err) {
    rows.value = []
    message.value = friendlyError(err, '지선 운행을 불러오지 못했습니다.')
    isError.value = true
  } finally {
    loading.value = false
  }
}

async function confirm(r) {
  if (!window.confirm(`${r.vehicleNo} (운행 #${r.assignmentId}) 센터 인수를 확정할까요?`)) return
  busy.value = true
  try {
    await confirmCenterReceipt(r.assignmentId)
    message.value = `운행 #${r.assignmentId} 센터 인수를 확정했습니다.`
    isError.value = false
    await load()
  } catch (err) {
    message.value = friendlyError(err, '인수 확정을 하지 못했습니다.')
    isError.value = true
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.crp { background: #fff; border: 1px solid #e3e8ef; border-radius: 12px; padding: 14px 16px; color: #1f2937; }
.crp-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.crp-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
.crp-tabs button { border: 1px solid #cfd6df; background: #f8fafc; border-radius: 8px; padding: 6px 12px; font-size: 13px; cursor: pointer; }
.crp-tabs button.on { background: #1d4ed8; border-color: #1d4ed8; color: #fff; }
.crp-btn { border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 6px; padding: 6px 12px; font-size: 13px; cursor: pointer; }
.crp-btn.primary { background: #1d4ed8; border-color: #1d4ed8; color: #fff; }
.crp-btn:disabled { opacity: .6; cursor: default; }
.crp-msg { margin: 0 0 8px; font-size: 13px; color: #166534; }
.crp-msg.err { color: #c81e2c; }
.crp-scroll { overflow-x: auto; }
.crp-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.crp-table th, .crp-table td { padding: 7px 8px; border-bottom: 1px solid #eef1f5; text-align: left; white-space: nowrap; }
.crp-empty { text-align: center !important; color: #6b7280; }
.crp-badge { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: 12px; background: #e5e7eb; }
.s-ARRIVED { background: #fef3c7; color: #92400e; } .s-RECEIVED { background: #dcfce7; color: #166534; } .s-IN_TRANSIT { background: #e0e7ff; color: #3730a3; }
</style>
