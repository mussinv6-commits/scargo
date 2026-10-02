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
          <h2 class="crud-title">당일 내역</h2>
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
            <tr v-else-if="!todayRows.length"><td :colspan="colCount" class="crud-empty">당일 배차 내역이 없습니다.</td></tr>
            <tr v-for="(r, idx) in todayRows" :key="'t-' + r.recordId">
              <td class="is-center">{{ idx + 1 }}</td>
              <td v-if="showCompany">{{ r.companyName }}</td>
              <td>{{ r.vehicleNo }}</td>
              <td>{{ r.containerNo }}</td>
              <td>{{ r.locationLabel }}</td>
              <td>{{ formatLoadedAt(r.loadedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="crud-table-wrap">
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
            <tr v-for="(r, idx) in pastRows" :key="'p-' + r.recordId">
              <td class="is-center">{{ idx + 1 }}</td>
              <td v-if="showCompany">{{ r.companyName }}</td>
              <td>{{ r.vehicleNo }}</td>
              <td>{{ r.containerNo }}</td>
              <td>{{ r.locationLabel }}</td>
              <td>{{ formatLoadedAt(r.loadedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
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
const emptyHint = computed(() =>
  props.scope === 'admin'
    ? '차량번호를 입력하지 않으면 모든 업체 차량의 당일·과거 내역이 표시됩니다.'
    : '차량번호를 입력하지 않으면 소속 차량의 당일·과거 내역이 표시됩니다.'
)
const showCompany = computed(() => props.scope === 'admin')
const colCount = computed(() => (showCompany.value ? 6 : 5))

const loading = ref(true)
const error = ref('')
const allRows = ref([])
const draft = ref('')
const applied = ref('')

const filtered = computed(() => filterRowsByVehicleNo(allRows.value, applied.value))
const todayRows = computed(() => splitTodayAndPast(filtered.value).todayRows)
const pastRows = computed(() => splitTodayAndPast(filtered.value).pastRows)

function applySearch() {
  applied.value = draft.value
}
function resetSearch() {
  draft.value = ''
  applied.value = ''
}

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
