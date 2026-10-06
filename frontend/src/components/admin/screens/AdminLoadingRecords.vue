<template>
  <div>
    <AdminPageHeader title="적재 기록 조회" description="차량-컨테이너 적재(하역) 기록을 조회합니다. 잘못 입력된 기록은 삭제할 수 있습니다." />

    <div class="crud-table-wrap">
      <div class="crud-toolbar">
        <div class="crud-toolbar-left">
          <h2 class="crud-title">적재 기록</h2>
          <span v-if="!loading" class="crud-count">총 {{ totalElements }}건</span>
        </div>
      </div>

      <div class="crud-table-scroll">
        <table class="crud-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>차량번호</th>
              <th>컨테이너번호</th>
              <th>적재위치</th>
              <th class="is-center">상태</th>
              <th>적재일시</th>
              <th class="is-center" style="width:1%;">관리</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="7" class="crud-empty">불러오는 중...</td></tr>
            <tr v-else-if="records.length === 0"><td colspan="7" class="crud-empty">적재 기록이 없습니다.</td></tr>
            <tr v-for="(r, idx) in records" :key="r.recordId">
              <td>{{ page * PAGE_SIZE + idx + 1 }}</td>
              <td>{{ r.vehicleNo }}</td>
              <td>{{ r.containerNo }}</td>
              <td>{{ r.locationName || (r.locationId ? `#${r.locationId}` : '-') }}</td>
              <td class="is-center"><span class="pill" :class="statusTone(r.status)">{{ statusLabel(r.status) }}</span></td>
              <td>{{ formatDate(r.loadedAt) }}</td>
              <td class="is-center"><button class="btn-admin btn-admin-danger" @click="remove(r)">삭제</button></td>
            </tr>
          </tbody>
        </table>
      </div>

      <AdminPager
        :model-value="page + 1"
        :page-count="pagerCount"
        @update:model-value="(p) => loadRecords(p - 1)"
      />
    </div>
  </div>
</template>

<script setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import AdminPager from '@/components/admin/AdminPager.vue'
import { computed, onMounted, ref, watch } from 'vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'
import { isViewToday, viewDate } from '@/components/admin/adminViewDate.js'

const PAGE_SIZE = 10
const records = ref([])
const loading = ref(true)
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const pagerCount = computed(() => Math.max(1, totalPages.value || 1))

function statusLabel(s) {
  return { PENDING: '대기', IN_PROGRESS: '진행중', COMPLETED: '완료', CANCELED: '취소' }[s] || s || '-'
}
function statusTone(s) {
  return { PENDING: 'pill-warn', IN_PROGRESS: 'pill-warn', COMPLETED: 'pill-on', CANCELED: 'pill-muted' }[s] || 'pill-muted'
}

function formatDate(d) {
  return d ? new Date(d).toLocaleString('ko-KR') : '-'
}

function dayBounds() {
  const pad = (n) => String(n).padStart(2, '0')
  const stamp = (x) => {
    const off = -x.getTimezoneOffset()
    const sign = off >= 0 ? '+' : '-'
    const abs = Math.abs(off)
    return `${x.getFullYear()}-${pad(x.getMonth() + 1)}-${pad(x.getDate())}T${pad(x.getHours())}:${pad(x.getMinutes())}:${pad(x.getSeconds())}${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
  }
  const start = new Date(viewDate.value)
  start.setHours(0, 0, 0, 0)
  const end = new Date(viewDate.value)
  end.setHours(23, 59, 59, 0)
  return { start: stamp(start), end: stamp(end) }
}

async function loadRecords(p = 0) {
  loading.value = true
  try {
    const past = !isViewToday.value
    const res = await adminApi.get(past ? '/api/loading-records/period' : '/api/loading-records', {
      params: past ? { ...dayBounds(), page: p, size: PAGE_SIZE } : { page: p, size: PAGE_SIZE },
    })
    records.value = res.data.content
    totalPages.value = res.data.totalPages
    totalElements.value = res.data.totalElements
    page.value = res.data.number
  } catch (err) {
    alert(pickErrorMessage(err, '적재 기록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

function changePage(p) {
  if (p < 0 || (totalPages.value > 0 && p >= totalPages.value)) return
  loadRecords(p)
}

async function remove(record) {
  if (!confirm(`적재 기록 #${record.recordId} 를 삭제하시겠습니까?`)) return
  try {
    await adminApi.delete(`/api/loading-records/${record.recordId}`)
    await loadRecords(page.value)
  } catch (err) {
    alert(`적재 기록 삭제에 실패했습니다.\n${pickErrorMessage(err)}`)
  }
}

watch(viewDate, () => loadRecords(0))
onMounted(() => loadRecords(0))
</script>
