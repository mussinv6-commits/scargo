<template>
  <div>
    <AdminPageHeader :title="title" :description="description" />

    <div class="crud-table-wrap loc-lookup">
      <form class="loc-lookup-search" @submit.prevent="applySearch">
        <label>
          <span>차량번호</span>
          <input
            v-model.trim="draft"
            class="crud-input"
            type="text"
            placeholder="예: 11가1234 (비우면 전체)"
          />
        </label>
        <button type="submit" class="btn-admin btn-admin-accent">조회</button>
        <button type="button" class="btn-admin" @click="resetSearch">전체</button>
      </form>
      <p v-if="applied" class="loc-lookup-hint">차량번호 ‘{{ applied }}’ 조회 결과입니다.</p>
      <p v-else class="loc-lookup-hint">{{ emptyHint }}</p>
    </div>

    <p v-if="error" class="crud-empty" style="color:#c81e2c;">{{ error }}</p>

    <section class="crud-table-wrap">
      <div class="crud-toolbar">
        <div class="crud-toolbar-left">
          <h2 class="crud-title">{{ dayTitle }}</h2>
          <span class="crud-count">{{ todayRows.length }}건</span>
        </div>
      </div>
      <div class="crud-table-scroll">
        <table class="crud-table">
          <thead>
            <tr>
              <th class="is-center" style="width:64px;">번호</th>
              <th v-if="showCompany">업체</th>
              <th>차량번호</th>
              <th>컨테이너 번호</th>
              <th>컨테이너 위치</th>
              <th>배정 일시</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td :colspan="colCount" class="crud-empty">불러오는 중...</td></tr>
            <tr v-else-if="!todayRows.length"><td :colspan="colCount" class="crud-empty">{{ singleDay && !isViewToday ? '선택한 날짜의 배차 내역이 없습니다.' : '당일 배차 내역이 없습니다.' }}</td></tr>
            <tr v-for="(r, idx) in pagedTodayRows" :key="'t-' + r.recordId">
              <td class="is-center">{{ rowNo(todayPage, idx) }}</td>
              <td v-if="showCompany">{{ r.companyName }}</td>
              <td>{{ r.vehicleNo }}</td>
              <td>{{ r.containerNo }}</td>
              <td>{{ r.locationLabel }}</td>
              <td>{{ formatLoadedAt(r.loadedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <AdminPager v-if="todayPageCount > 1" v-model="todayPage" :page-count="todayPageCount" />
    </section>

    <section v-if="!singleDay" class="crud-table-wrap">
      <div class="crud-toolbar">
        <div class="crud-toolbar-left">
          <h2 class="crud-title">과거 내역</h2>
          <span class="crud-count">{{ pastRows.length }}건</span>
        </div>
      </div>
      <div class="crud-table-scroll">
        <table class="crud-table">
          <thead>
            <tr>
              <th class="is-center" style="width:64px;">번호</th>
              <th v-if="showCompany">업체</th>
              <th>차량번호</th>
              <th>컨테이너 번호</th>
              <th>컨테이너 위치</th>
              <th>배정 일시</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td :colspan="colCount" class="crud-empty">불러오는 중...</td></tr>
            <tr v-else-if="!pastRows.length"><td :colspan="colCount" class="crud-empty">과거 배차 내역이 없습니다.</td></tr>
            <tr v-for="(r, idx) in pagedPastRows" :key="'p-' + r.recordId">
              <td class="is-center">{{ rowNo(pastPage, idx) }}</td>
              <td v-if="showCompany">{{ r.companyName }}</td>
              <td>{{ r.vehicleNo }}</td>
              <td>{{ r.containerNo }}</td>
              <td>{{ r.locationLabel }}</td>
              <td>{{ formatLoadedAt(r.loadedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <AdminPager v-if="pastPageCount > 1" v-model="pastPage" :page-count="pastPageCount" />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { isSameViewDay, isViewToday, viewDate } from '@/components/admin/adminViewDate.js'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import AdminPager from '@/components/admin/AdminPager.vue'
import {
  fetchContainerLocationRows,
  filterRowsByVehicleNo,
  splitTodayAndPast,
  formatLoadedAt,
} from '@/utils/containerLocations.js'
import { fetchCompanyTrucks } from '@/utils/companyDrivers.js'
import { friendlyError } from '@/utils/apiHelpers.js'

const props = defineProps({
  scope: { type: String, required: true },
})

const title = '컨테이너 위치 조회'
const description = computed(() =>
  props.scope === 'admin'
    ? '전체 업체의 차량-컨테이너 매핑과 적재 위치를 조회합니다.'
    : '소속 차량의 컨테이너 번호와 적재 위치를 조회합니다.'
)
const emptyHint = computed(() => {
  if (props.scope === 'company') return '차량번호를 입력하지 않으면 소속 차량의 선택 날짜 내역이 표시됩니다.'
  return isViewToday.value
    ? '차량번호를 입력하지 않으면 모든 업체 차량의 당일·과거 내역이 표시됩니다.'
    : '차량번호를 입력하지 않으면 모든 업체 차량의 선택 날짜 내역이 표시됩니다.'
})
const showCompany = computed(() => props.scope === 'admin')
const colCount = computed(() => (showCompany.value ? 6 : 5))

const loading = ref(true)
const error = ref('')
const allRows = ref([])
const draft = ref('')
const applied = ref('')

const PAGE_SIZE = 5
const todayPage = ref(1)
const pastPage = ref(1)

const singleDay = computed(() => props.scope === 'company' || !isViewToday.value)
const dayTitle = computed(() => (isViewToday.value ? '당일 내역' : '선택 날짜 내역'))
const filtered = computed(() => filterRowsByVehicleNo(allRows.value, applied.value))
const dated = computed(() => (singleDay.value ? filtered.value.filter((r) => isSameViewDay(r.loadedAt)) : filtered.value))
const todayRows = computed(() => (singleDay.value ? dated.value : splitTodayAndPast(dated.value).todayRows))
const pastRows = computed(() => (singleDay.value ? [] : splitTodayAndPast(dated.value).pastRows))
const todayPageCount = computed(() => Math.max(1, Math.ceil(todayRows.value.length / PAGE_SIZE) || 1))
const pastPageCount = computed(() => Math.max(1, Math.ceil(pastRows.value.length / PAGE_SIZE) || 1))
const pagedTodayRows = computed(() => slicePage(todayRows.value, todayPage.value))
const pagedPastRows = computed(() => slicePage(pastRows.value, pastPage.value))

function slicePage(rows, page) {
  const start = (page - 1) * PAGE_SIZE
  return rows.slice(start, start + PAGE_SIZE)
}
function rowNo(page, idx) {
  return (page - 1) * PAGE_SIZE + idx + 1
}

function applySearch() {
  applied.value = draft.value
  todayPage.value = 1
  pastPage.value = 1
}
function resetSearch() {
  draft.value = ''
  applied.value = ''
  todayPage.value = 1
  pastPage.value = 1
}

watch(viewDate, () => { todayPage.value = 1; pastPage.value = 1 })
watch(todayPageCount, (n) => { if (todayPage.value > n) todayPage.value = n })
watch(pastPageCount, (n) => { if (pastPage.value > n) pastPage.value = n })

async function load() {
  loading.value = true
  error.value = ''
  try {
    let vehicleNos = null
    if (props.scope === 'company') {
      const trucks = await fetchCompanyTrucks()
      vehicleNos = trucks.map((t) => t.vehicleNo).filter(Boolean)
    }
    allRows.value = await fetchContainerLocationRows({
      vehicleNos,
      includeCompany: props.scope === 'admin',
    })
  } catch (err) {
    error.value = friendlyError(err, '컨테이너 위치 내역을 불러오지 못했습니다.')
    allRows.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.loc-lookup-search {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 10px;
  padding: 16px 18px 8px;
}
.loc-lookup-search label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-muted, #6b7280);
}
.loc-lookup-search .crud-input { min-width: 220px; }
.loc-lookup-hint {
  margin: 0 18px 16px;
  font-size: 13px;
  color: var(--text-muted, #6b7280);
}
</style>
