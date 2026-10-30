<template>
  <!-- 26.10.24 게이트 사전 예약: 업체 · 기사는 자기 업체 예약을 등록 · 취소, 관리자 · 관제는 전체를 보고 입차 확인 · 불참 처리 -->
  <div class="gr" :class="{ 'is-dark': dark }">
    <section class="gr-card">
      <h3>새 예약</h3>
      <form class="gr-form" @submit.prevent="create">
        <input v-model.trim="form.vehicleNo" class="gr-in" maxlength="20" placeholder="차량 번호 (예: 12가3456)" required />
        <input v-model.trim="form.containerNo" class="gr-in" maxlength="11" placeholder="컨테이너 번호(선택)" />
        <select v-model="form.direction" class="gr-in">
          <option v-for="(label, key) in RESERVATION_DIRECTION" :key="key" :value="key">{{ label }}</option>
        </select>
        <input v-model="form.plannedLocal" type="datetime-local" class="gr-in" required />
        <select v-if="staff && companies.length" v-model.number="form.companyId" class="gr-in">
          <option :value="null">업체 선택</option>
          <option v-for="c in companies" :key="c.companyId" :value="c.companyId">{{ c.companyName }}</option>
        </select>
        <input v-else-if="staff" v-model.number="form.companyId" type="number" min="1" class="gr-in" placeholder="업체 번호(선택)" />
        <input v-model.trim="form.memo" class="gr-in grow" maxlength="300" placeholder="메모(선택)" />
        <button type="submit" class="gr-btn primary" :disabled="busy">예약</button>
      </form>
    </section>

    <section class="gr-card">
      <div class="gr-bar">
        <h3>예약 목록 <small>{{ rows.length }}건</small></h3>
        <input v-model="filter.date" type="date" class="gr-in" @change="load" />
        <select v-model="filter.status" class="gr-in" @change="load">
          <option value="">전체 상태</option>
          <option v-for="(label, key) in RESERVATION_STATUS" :key="key" :value="key">{{ label }}</option>
        </select>
        <button class="gr-btn" @click="clearFilter">전체 보기</button>
        <button class="gr-btn" :disabled="!rows.length" @click="exportRows">Excel 내보내기</button>
      </div>
      <p v-if="message" class="gr-msg" :class="{ ok: messageOk }">{{ message }}</p>
      <table class="gr-table">
        <thead><tr><th>번호</th><th>예약 시각</th><th>구분</th><th>차량</th><th>컨테이너</th><th>업체</th><th>상태</th><th>메모</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!rows.length"><td colspan="9" class="gr-empty">{{ loading ? '불러오는 중...' : '예약이 없습니다.' }}</td></tr>
          <tr v-for="(r, i) in pg.rows" :key="r.reservationId" :class="{ done: r.status !== 'BOOKED' }">
            <td>{{ pg.no(i) }}</td><td>{{ formatDateTime(r.plannedAt) }}</td>
            <td>{{ RESERVATION_DIRECTION[r.direction] }}</td><td>{{ r.vehicleNo }}</td><td>{{ r.containerNo || '-' }}</td>
            <td>{{ r.companyName || '-' }}</td><td>{{ RESERVATION_STATUS[r.status] }}</td><td class="wrap is-left">{{ r.memo || '' }}</td>
            <td class="gr-actions">
              <template v-if="r.status === 'BOOKED'">
                <button v-if="staff" class="gr-btn primary" :disabled="busy" @click="change(r, 'CHECKED_IN')">입차 확인</button>
                <button v-if="staff" class="gr-btn" :disabled="busy" @click="change(r, 'NO_SHOW')">불참</button>
                <button class="gr-btn" :disabled="busy" @click="change(r, 'CANCELLED')">취소</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
      <AdminPager v-model="pg.page" :page-count="pg.pageCount" /><!-- 26.10.28 QA#5 5건 페이징 -->
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { authState } from '@/auth/authState.js'
import { RESERVATION_STATUS, RESERVATION_DIRECTION, listCompaniesForSelect, listReservations, createReservation, updateReservation } from '@/utils/opsApi.js'
import { formatDateTime } from '@/utils/containerInspectionApi.js'
import { exportExcel } from '@/utils/exportExcel.js'
import { friendlyError } from '@/utils/apiHelpers.js'
import AdminPager from '@/components/admin/AdminPager.vue'
import { usePaged } from '@/utils/usePaged.js'

defineProps({ dark: { type: Boolean, default: false } })

// 입차 확인 · 불참 버튼은 관리자 · 관제에게만 보인다 (실제 권한은 서버가 다시 확인)
const staff = computed(() => ['ADMIN', 'CONTROL'].includes(authState.user?.userType))

const rows = ref([])
// 26.10.28 QA#5 예약 번호 순 정렬 + 번호 1부터, 5건씩
const pg = usePaged(() => [...rows.value].sort((a, b) => (b.reservationId || 0) - (a.reservationId || 0)), 5)
const companies = ref([])
const loading = ref(false)
const busy = ref(false)
const message = ref('')
const messageOk = ref(false)
const filter = reactive({ date: '', status: '' })
const form = reactive({ vehicleNo: '', containerNo: '', direction: 'IN', plannedLocal: '', memo: '', companyId: null })

function note(text, ok = false) { message.value = text; messageOk.value = ok }

async function load() {
  loading.value = true
  try {
    rows.value = await listReservations({ date: filter.date, status: filter.status })
  } catch (err) {
    rows.value = []
    note(friendlyError(err, '예약 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

function clearFilter() {
  filter.date = ''
  filter.status = ''
  load()
}

async function create() {
  busy.value = true
  try {
    await createReservation({
      vehicleNo: form.vehicleNo,
      containerNo: form.containerNo || null,
      direction: form.direction,
      plannedAt: new Date(form.plannedLocal).toISOString(),
      memo: form.memo || null,
      companyId: form.companyId || null,
    })
    Object.assign(form, { vehicleNo: '', containerNo: '', memo: '', companyId: null })
    note('예약했습니다.', true)
    await load()
  } catch (err) {
    note(friendlyError(err, '예약하지 못했습니다.'))
  } finally {
    busy.value = false
  }
}

async function change(r, status) {
  const label = RESERVATION_STATUS[status]
  if (!confirm(`#${r.reservationId} ${r.vehicleNo} 예약을 '${label}' 처리할까요?`)) return
  busy.value = true
  try {
    await updateReservation(r.reservationId, status)
    note(`#${r.reservationId} ${label} 처리했습니다.`, true)
    await load()
  } catch (err) {
    note(friendlyError(err, '처리하지 못했습니다.'))
  } finally {
    busy.value = false
  }
}

function exportRows() {
  exportExcel('게이트예약', [
    { key: 'reservationId', label: '번호' },
    { key: 'plannedAt', label: '예약 시각', formatter: (v) => formatDateTime(v) },
    { key: 'direction', label: '구분', formatter: (v) => RESERVATION_DIRECTION[v] || v },
    { key: 'vehicleNo', label: '차량' }, { key: 'containerNo', label: '컨테이너' }, { key: 'companyName', label: '업체' },
    { key: 'status', label: '상태', formatter: (v) => RESERVATION_STATUS[v] || v }, { key: 'memo', label: '메모' },
  ], rows.value)
}

onMounted(async () => {
  load()
  if (staff.value) {
    try { companies.value = await listCompaniesForSelect() } catch { /* 관제는 업체 목록 권한이 없어 번호 입력으로 대신한다 */ }
  }
})
</script>

<style scoped>
.gr { display: flex; flex-direction: column; gap: 12px; }
.gr-card { background: #fff; border: 1px solid #d5dbe5; border-radius: 10px; padding: 12px 14px; overflow-x: auto; }
.gr-card h3 { margin: 0 0 8px; font-size: 15px; } .gr-card h3 small { color: #6b7a90; font-weight: 400; }
.gr-form, .gr-bar { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; } .gr-bar h3 { margin: 0; }
.gr-bar { margin-bottom: 12px; } /* 26.10.28 예약 목록 줄과 표 사이 간격 */
.gr-in { border: 1px solid #c9d3e0; border-radius: 6px; padding: 6px 8px; font-size: 13px; background: #fff; color: inherit; } .gr-in.grow { flex: 1; min-width: 160px; }
.gr-btn { border: 1px solid #c9d3e0; background: #fff; color: inherit; border-radius: 6px; padding: 6px 12px; font-size: 13px; cursor: pointer; }
.gr-btn.primary { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; font-weight: 700; } .gr-btn:disabled { opacity: .5; cursor: default; }
.gr-msg { margin: 6px 0; color: #c0392b; font-size: 13px; } .gr-msg.ok { color: #1e8e3e; }
.gr-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.gr-table th, .gr-table td { padding: 7px 8px; border-bottom: 1px solid #e8ecf2; text-align: left; white-space: nowrap; }
.gr-table td.wrap { white-space: normal; } .gr-table th { color: #6b7a90; font-weight: 600; }
.gr-table tr.done td { opacity: .6; } .gr-empty { text-align: center !important; color: #6b7a90; } .gr-actions { display: flex; gap: 6px; }
.is-dark .gr-card { background: #0f2a4a; border-color: #1d3b60; color: #e6eff7; }
.is-dark .gr-in, .is-dark .gr-btn { background: #0b1d36; border-color: #2a4d76; color: #e6eff7; }
.is-dark .gr-btn.primary { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; }
.is-dark .gr-table th, .is-dark .gr-table td { border-bottom-color: #1d3b60; }
.is-dark .gr-card h3 small, .is-dark .gr-table th, .is-dark .gr-empty { color: #9fb0c6; }
</style>
