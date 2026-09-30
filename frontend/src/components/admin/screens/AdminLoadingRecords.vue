<template>
  <div>
    <div class="admin-page-header">
      <h1>적재 기록 조회</h1>
      <p>차량-컨테이너 적재(하역) 기록을 조회합니다. 오기 입력 건은 삭제할 수 있습니다.</p>
    </div>

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
              <th>적재일시</th>
              <th>관리</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="6" class="crud-empty">불러오는 중...</td></tr>
            <tr v-else-if="records.length === 0"><td colspan="6" class="crud-empty">적재 기록이 없습니다.</td></tr>
            <tr v-for="r in records" :key="r.recordId">
              <td>{{ r.recordId }}</td>
              <td>{{ r.vehicleNo }}</td>
              <td>{{ r.containerNo }}</td>
              <td>{{ r.locationName || (r.locationId ? `#${r.locationId}` : '-') }}</td>
              <td>{{ formatDate(r.loadedAt) }}</td>
              <td><button class="btn-admin btn-admin-danger" @click="remove(r)">삭제</button></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="crud-toolbar" style="margin-top: 12px;" v-if="totalPages > 1">
        <button class="btn-admin btn-admin-ghost" :disabled="page === 0" @click="changePage(page - 1)">이전</button>
        <span style="font-size: 12.5px; color: var(--a-text-muted);">{{ page + 1 }} / {{ totalPages }} 페이지</span>
        <button class="btn-admin btn-admin-ghost" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">다음</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'

const records = ref([])
const loading = ref(true)
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)

function formatDate(d) {
  return d ? new Date(d).toLocaleString('ko-KR') : '-'
}

async function loadRecords(p = 0) {
  loading.value = true
  try {
    const res = await adminApi.get('/api/loading-records', { params: { page: p, size: 20 } })
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
  if (p < 0 || p >= totalPages.value) return
  loadRecords(p)
}

async function remove(record) {
  if (!confirm(`적재 기록 #${record.recordId} 를 삭제하시겠습니까?`)) return
  try {
    await adminApi.delete(`/api/loading-records/${record.recordId}`)
    await loadRecords(page.value)
  } catch (err) {
    alert(pickErrorMessage(err, '삭제 중 오류가 발생했습니다.'))
  }
}

onMounted(() => loadRecords(0))
</script>
