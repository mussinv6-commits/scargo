<template>
  <!-- 26.10.24 예외·사고 접수 · 처리. 처리 버튼(상태 · 담당자 · 조치)은 서버 권한(관리자 · 관제 · 소속 허브)이 있을 때만 동작 -->
  <div class="ip2" :class="{ 'is-dark': dark }">
    <section class="ip2-card">
      <h3>새 접수</h3>
      <form class="ip2-form" @submit.prevent="create">
        <select v-model="form.type" class="ip2-in" required>
          <option v-for="(label, key) in INCIDENT_TYPE" :key="key" :value="key">{{ label }}</option>
        </select>
        <select v-model="form.severity" class="ip2-in">
          <option v-for="(label, key) in SEVERITY" :key="key" :value="key">중요도 {{ label }}</option>
        </select>
        <input v-model.trim="form.title" class="ip2-in grow" maxlength="100" placeholder="제목 (예: 7번 컨테이너 우측면 찌그러짐)" required />
        <input v-model.trim="form.containerNo" class="ip2-in" maxlength="11" placeholder="컨테이너 번호(선택)" />
        <button type="submit" class="ip2-btn primary" :disabled="busy">접수</button>
        <textarea v-model="form.detail" class="ip2-in full" rows="2" maxlength="2000" placeholder="상세 내용(선택)"></textarea>
      </form>
    </section>

    <section class="ip2-card">
      <div class="ip2-bar">
        <h3>접수 목록 <small>{{ rows.length }}건</small></h3>
        <select v-model="filter" class="ip2-in" @change="load">
          <option value="">전체</option>
          <option v-for="(label, key) in INCIDENT_STATUS" :key="key" :value="key">{{ label }}</option>
        </select>
        <button class="ip2-btn" :disabled="!rows.length" @click="exportRows">Excel 내보내기</button>
      </div>
      <p v-if="message" class="ip2-msg" :class="{ ok: messageOk }">{{ message }}</p>
      <table class="ip2-table">
        <thead><tr><th>번호</th><th>중요도</th><th>유형</th><th>제목</th><th>컨테이너</th><th>상태</th><th>담당</th><th>접수</th><th>처리</th></tr></thead>
        <tbody>
          <tr v-if="!rows.length"><td colspan="9" class="ip2-empty">{{ loading ? '불러오는 중...' : '접수된 건이 없습니다.' }}</td></tr>
          <template v-for="r in rows" :key="r.incidentId">
            <tr :class="{ sel: open === r.incidentId, done: r.status === 'RESOLVED' }">
              <td>#{{ r.incidentId }}</td>
              <td><span class="ip2-sev" :class="'s-' + r.severity">{{ SEVERITY[r.severity] }}</span></td>
              <td>{{ INCIDENT_TYPE[r.type] || r.type }}</td>
              <td class="wrap">{{ r.title }}</td><td>{{ r.containerNo || '-' }}</td>
              <td>{{ INCIDENT_STATUS[r.status] }}</td><td>{{ r.assignee || '-' }}</td>
              <td>{{ formatDateTime(r.createdAt) }}</td>
              <td><button class="ip2-btn" @click="toggle(r)">{{ open === r.incidentId ? '닫기' : '열기' }}</button></td>
            </tr>
            <tr v-if="open === r.incidentId" class="ip2-detail">
              <td colspan="9">
                <p v-if="r.detail" class="ip2-text">{{ r.detail }}</p>
                <p class="ip2-muted">접수자 {{ r.reportedBy || '-' }}<template v-if="r.resolvedAt"> · 해결 {{ formatDateTime(r.resolvedAt) }}</template></p>
                <div class="ip2-edit">
                  <select v-model="edit.status" class="ip2-in">
                    <option v-for="(label, key) in INCIDENT_STATUS" :key="key" :value="key">{{ label }}</option>
                  </select>
                  <input v-model.trim="edit.assignee" class="ip2-in" maxlength="50" placeholder="담당자" />
                  <input v-model.trim="edit.resolution" class="ip2-in grow" placeholder="조치 내용 (해결 처리 때 필수)" />
                  <button class="ip2-btn primary" :disabled="busy" @click="save(r)">저장</button>
                </div>
                <p class="ip2-muted">상태 · 담당자 · 조치 변경은 관리자 · 관제 · 소속 허브 담당자만 할 수 있습니다.</p>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { INCIDENT_TYPE, SEVERITY, INCIDENT_STATUS, listIncidents, createIncident, updateIncident } from '@/utils/opsApi.js'
import { formatDateTime } from '@/utils/containerInspectionApi.js'
import { exportExcel } from '@/utils/exportExcel.js'
import { friendlyError } from '@/utils/apiHelpers.js'

defineProps({ dark: { type: Boolean, default: false } })

const rows = ref([])
const loading = ref(false)
const busy = ref(false)
const filter = ref('')
const open = ref(null)
const message = ref('')
const messageOk = ref(false)
const form = reactive({ type: 'DAMAGE', severity: 'MEDIUM', title: '', containerNo: '', detail: '' })
const edit = reactive({ status: 'OPEN', assignee: '', resolution: '' })

function note(text, ok = false) { message.value = text; messageOk.value = ok }

async function load() {
  loading.value = true
  try {
    rows.value = await listIncidents(filter.value)
  } catch (err) {
    rows.value = []
    note(friendlyError(err, '접수 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

async function create() {
  busy.value = true
  try {
    await createIncident({ ...form, containerNo: form.containerNo || null, detail: form.detail || null })
    Object.assign(form, { type: 'DAMAGE', severity: 'MEDIUM', title: '', containerNo: '', detail: '' })
    note('접수했습니다.', true)
    await load()
  } catch (err) {
    note(friendlyError(err, '접수하지 못했습니다.'))
  } finally {
    busy.value = false
  }
}

function toggle(r) {
  if (open.value === r.incidentId) { open.value = null; return }
  open.value = r.incidentId
  Object.assign(edit, { status: r.status, assignee: r.assignee || '', resolution: r.resolution || '' })
}

async function save(r) {
  busy.value = true
  try {
    await updateIncident(r.incidentId, { status: edit.status, assignee: edit.assignee, resolution: edit.resolution })
    note(`#${r.incidentId} 저장했습니다.`, true)
    await load()
  } catch (err) {
    note(friendlyError(err, '저장하지 못했습니다.'))
  } finally {
    busy.value = false
  }
}

function exportRows() {
  exportExcel('예외사고', [
    { key: 'incidentId', label: '번호' },
    { key: 'severity', label: '중요도', formatter: (v) => SEVERITY[v] || v },
    { key: 'type', label: '유형', formatter: (v) => INCIDENT_TYPE[v] || v },
    { key: 'title', label: '제목' },
    { key: 'containerNo', label: '컨테이너' },
    { key: 'status', label: '상태', formatter: (v) => INCIDENT_STATUS[v] || v },
    { key: 'assignee', label: '담당' },
    { key: 'resolution', label: '조치 내용' },
    { key: 'createdAt', label: '접수', formatter: (v) => formatDateTime(v) },
  ], rows.value)
}

onMounted(load)
</script>

<style scoped>
.ip2 { display: flex; flex-direction: column; gap: 12px; }
.ip2-card { background: #fff; border: 1px solid #d5dbe5; border-radius: 10px; padding: 12px 14px; overflow-x: auto; }
.ip2-card h3 { margin: 0 0 8px; font-size: 15px; } .ip2-card h3 small { color: #6b7a90; font-weight: 400; }
.ip2-form, .ip2-edit, .ip2-bar { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.ip2-bar h3 { margin: 0; } .ip2-bar .ip2-btn:last-child { margin-left: auto; }
.ip2-in { border: 1px solid #c9d3e0; border-radius: 6px; padding: 6px 8px; font-size: 13px; background: #fff; color: inherit; }
.ip2-in.grow { flex: 1; min-width: 200px; } .ip2-in.full { flex-basis: 100%; }
.ip2-btn { border: 1px solid #c9d3e0; background: #fff; border-radius: 6px; padding: 6px 12px; font-size: 13px; cursor: pointer; color: inherit; }
.ip2-btn.primary { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; font-weight: 700; }
.ip2-btn:disabled { opacity: .5; cursor: default; }
.ip2-msg { margin: 6px 0; color: #c0392b; font-size: 13px; } .ip2-msg.ok { color: #1e8e3e; }
.ip2-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.ip2-table th, .ip2-table td { padding: 7px 8px; border-bottom: 1px solid #e8ecf2; text-align: left; white-space: nowrap; }
.ip2-table td.wrap { white-space: normal; min-width: 180px; } .ip2-table th { color: #6b7a90; font-weight: 600; }
.ip2-table tr.done td { opacity: .6; } .ip2-empty { text-align: center !important; color: #6b7a90; }
.ip2-sev { padding: 2px 8px; border-radius: 10px; font-size: 12px; background: #eef1f6; }
.ip2-sev.s-HIGH { background: #fde2e3; color: #b42318; } .ip2-sev.s-MEDIUM { background: #fff1d6; color: #9a5b00; }
.ip2-detail td { background: #f7f9fc; white-space: normal; } .ip2-text { margin: 0 0 6px; white-space: pre-line; }
.ip2-muted { margin: 6px 0 0; color: #6b7a90; font-size: 12.5px; }
.is-dark .ip2-card { background: #0f2a4a; border-color: #1d3b60; color: #e6eff7; }
.is-dark .ip2-in, .is-dark .ip2-btn { background: #0b1d36; border-color: #2a4d76; color: #e6eff7; }
.is-dark .ip2-btn.primary { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; }
.is-dark .ip2-table th, .is-dark .ip2-table td { border-bottom-color: #1d3b60; }
.is-dark .ip2-detail td { background: #0b1d36; } .is-dark .ip2-sev { background: #12304f; }
.is-dark .ip2-card h3 small, .is-dark .ip2-muted, .is-dark .ip2-table th, .is-dark .ip2-empty { color: #9fb0c6; }
</style>
