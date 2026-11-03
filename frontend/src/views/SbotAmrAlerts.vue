<template>
  <!-- 26.11.01 S-Bot AMR 알림 내역 (목업 SBot_AMR알림내역_미리보기_1101 반영) -->
  <div class="saa">
    <div class="sum-cards">
      <button v-for="c in cards" :key="c.key" type="button" class="sum-card" :class="{ on: filter === c.key }" :style="{ '--lc': c.color }" @click="setFilter(c.key)">
        <div class="lab">{{ c.label }}</div>
        <div class="num">{{ summary[c.key] ?? 0 }}</div>
        <div class="sub">{{ c.sub }}</div>
      </button>
    </div>

    <section class="nh-card">
      <div class="nh-tabs" role="tablist">
        <button v-for="t in readTabs" :key="t.key" type="button" :class="{ on: readTab === t.key }" @click="readTab = t.key; load()">
          {{ t.label }}<span class="n">{{ t.key === 'all' ? (summary.all || 0) : t.key === 'unread' ? (summary.unread || 0) : Math.max(0, (summary.all || 0) - (summary.unread || 0)) }}</span>
        </button>
      </div>
      <div class="type-filters">
        <button v-for="c in cards" :key="'p'+c.key" type="button" :class="{ on: filter === c.key }" @click="setFilter(c.key)">{{ c.label }}</button>
      </div>
      <div class="nh-bar">
        <label>종류
          <select v-model="filter" class="nh-in" @change="load">
            <option value="all">전체</option>
            <option value="fire">화재 (hub)</option>
            <option value="batt">배터리 화재 (batt)</option>
            <option value="estop">비상 정지</option>
            <option value="temp">온도 이상</option>
          </select>
        </label>
        <input v-model.trim="keyword" class="nh-in grow" placeholder="제목 · 내용 · 장비 · 구역 검색" @keyup.enter="load" />
        <button type="button" class="nh-btn" :disabled="loading" @click="load">{{ loading ? '불러오는 중…' : '새로고침' }}</button>
        <button type="button" class="nh-btn pri" :disabled="marking" @click="readAll">모두 읽음</button>
      </div>
      <p v-if="error" class="err">{{ error }}</p>
      <table class="nh-table">
        <thead>
          <tr>
            <th class="no">번호</th><th class="st">상태</th><th class="ty">종류</th><th>제목</th><th class="msg">내용</th>
            <th>장비</th><th>구역</th><th class="tm">발령 시각</th><th class="tm">해제 시각</th><th class="tm">지속</th><th class="tm">받은 시각</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!rows.length"><td class="empty" colspan="11">{{ loading ? '불러오는 중…' : '알림이 없습니다.' }}</td></tr>
          <tr v-for="(r, i) in rows" :key="r.id" :class="{ unread: !r.read }" @click="markOne(r)">
            <td class="no">{{ rows.length - i }}</td>
            <td class="st"><span class="dot" :class="{ on: !r.read }"></span>{{ r.read ? '읽음' : '안 읽음' }}</td>
            <td class="ty"><span class="badge" :class="r.type">{{ typeLabel(r.type) }}</span></td>
            <td class="ttl">{{ r.title }}</td>
            <td class="msg">{{ r.message }}</td>
            <td>{{ r.equipment || '—' }}</td>
            <td>{{ r.zone || '—' }}</td>
            <td class="tm">{{ fmt(r.startedAt) }}</td>
            <td class="tm">{{ r.endedAt ? fmt(r.endedAt) : '—' }}</td>
            <td class="tm">{{ dur(r) }}</td>
            <td class="tm">{{ fmt(r.createdAt || r.startedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup>
import { onSafetyPoke } from '@/utils/fireSignal.js';
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'

const cards = [
  { key: 'all', label: '전체', sub: '최근 알림', color: '#1d4ed8' },
  { key: 'fire', label: '화재', sub: 'kind = hub', color: '#d32f2f' },
  { key: 'batt', label: '배터리 화재', sub: 'kind = batt', color: '#e65100' },
  { key: 'estop', label: '비상 정지', sub: 'setEstop', color: '#c2416b' },
  { key: 'temp', label: '온도 이상', sub: 'bfire stage 1', color: '#d18a00' },
]
const readTabs = [
  { key: 'all', label: '전체' },
  { key: 'unread', label: '안 읽음' },
  { key: 'read', label: '읽음' },
]
const TYPE = { fire: '화재', batt: '배터리 화재', estop: '비상 정지', temp: '온도 이상' }
const typeLabel = (t) => TYPE[t] || t

const rows = ref([])
const summary = ref({ all: 0, fire: 0, batt: 0, estop: 0, temp: 0, unread: 0 })
const filter = ref('all')
const readTab = ref('all')
const keyword = ref('')
const loading = ref(false)
const marking = ref(false)
const error = ref('')
let timer = 0

function setFilter(k) { filter.value = k; load() }

function fmt(v) {
  if (!v) return '—'
  const d = new Date(typeof v === 'number' && v < 1e12 ? v * 1000 : v)
  if (Number.isNaN(d.getTime())) return '—'
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
function dur(r) {
  const s = r.durationSec
  if (r.active && !r.endedAt) return '진행 중'
  if (s == null) return '—'
  const m = Math.floor(s / 60), sec = s % 60
  return m > 0 ? `${m}분 ${sec}초` : `${sec}초`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: 0, size: 100 }
    if (filter.value && filter.value !== 'all') params.type = filter.value
    if (readTab.value && readTab.value !== 'all') params.read = readTab.value
    if (keyword.value) params.q = keyword.value
    const res = await adminApi.get('/api/amr/alerts', { params })
    rows.value = res.data?.content || []
    summary.value = res.data?.summary || summary.value
  } catch (e) {
    rows.value = []
    error.value = pickErrorMessage(e, 'AMR 알림 내역을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

async function markOne(r) {
  if (r.read) return
  try {
    await adminApi.post(`/api/amr/alerts/${r.id}/read`)
    r.read = true
    if (summary.value.unread > 0) summary.value.unread--
  } catch { /* 무시 */ }
}
async function readAll() {
  marking.value = true
  try {
    await adminApi.post('/api/amr/alerts/read-all')
    await load()
  } catch (e) {
    error.value = pickErrorMessage(e, '모두 읽음 처리에 실패했습니다.')
  } finally {
    marking.value = false
  }
}

watch(readTab, () => load())
let offPoke = () => {}
onMounted(() => { load(); timer = setInterval(load, 4000); offPoke = onSafetyPoke(load) }) // 26.11.03 8초 → 4초 + 화재 시작 · 종료 직후 즉시 조회
onBeforeUnmount(() => { clearInterval(timer); offPoke() })
</script>

<style scoped>
.saa { color: #112847; display: flex; flex-direction: column; gap: 14px; padding: 4px 2px 16px; }
.sum-cards { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; }
.sum-card {
  background: #fff; border: 1px solid #e3e8ef; border-left: 4px solid var(--lc, #1d4ed8);
  border-radius: 12px; padding: 12px 14px; cursor: pointer; text-align: left;
  box-shadow: 0 1px 3px rgba(17,40,71,.06); font: inherit; color: inherit;
}
.sum-card:hover { box-shadow: 0 6px 16px rgba(17,40,71,.1); }
.sum-card.on { box-shadow: 0 0 0 2px rgba(17,40,71,.12); border-color: #c9d3e0; background: #fff; }
.sum-card .lab { font-size: 12.5px; font-weight: 700; color: #334155; }
.sum-card .num { font-size: 22px; font-weight: 800; color: var(--lc); line-height: 1.25; font-variant-numeric: tabular-nums; }
.sum-card .sub { font-size: 11.5px; color: #64748b; margin-top: 2px; }
.nh-card { background: #fff; border-radius: 12px; padding: 16px 18px; box-shadow: 0 1px 3px rgba(17,40,71,.08); }
.nh-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; }
.nh-tabs button { border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 8px; padding: 7px 14px; font-size: 13.5px; font-weight: 600; cursor: pointer; display: inline-flex; gap: 6px; align-items: center; }
.nh-tabs button.on { background: #1d4ed8; border-color: #1d4ed8; color: #fff; font-weight: 700; }
.nh-tabs .n { min-width: 20px; height: 20px; padding: 0 6px; border-radius: 10px; background: #eef1f6; color: #0b1d36; font-size: 12px; display: inline-flex; align-items: center; justify-content: center; }
.nh-tabs button.on .n { background: rgba(255,255,255,.55); }
.type-filters { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 12px; }
.type-filters button { border: 1px solid #cfd6df; background: #fff; color: #334155; border-radius: 999px; padding: 6px 12px; font-size: 12.5px; font-weight: 700; cursor: pointer; }
.type-filters button.on { border-color: #112847; color: #112847; box-shadow: inset 0 0 0 1px #112847; background: #fff; }
.nh-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.nh-bar label { display: flex; align-items: center; gap: 8px; font-size: 13.5px; font-weight: 700; margin: 0; }
.nh-in { border: 1px solid #c9d3e0; border-radius: 7px; padding: 6px 10px; font-size: 13px; height: 34px; }
.nh-in.grow { flex: 1 1 220px; min-width: 160px; }
.nh-btn { border: 1px solid #c9d3e0; background: #fff; color: #112847; border-radius: 7px; padding: 0 14px; height: 34px; font-size: 13px; font-weight: 700; cursor: pointer; }
.nh-btn.pri { background: #c9972f; border-color: #c9972f; color: #fff; }
.nh-btn:disabled { opacity: .5; }
.err { color: #dc3b3b; font-size: 13px; margin: 0 0 10px; }
.nh-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.nh-table th { background: #eef1f6; color: #64748b; font-weight: 700; font-size: 12.5px; padding: 10px; text-align: left; white-space: nowrap; }
.nh-table td { padding: 10px; border-bottom: 1px solid #e3e8ef; vertical-align: middle; }
.nh-table tbody tr { cursor: pointer; }
.nh-table tbody tr:hover td { background: #f8fbff; }
.nh-table tr.unread .ttl { font-weight: 800; }
.nh-table .no { width: 52px; text-align: center; color: #64748b; }
.nh-table .st { width: 78px; white-space: nowrap; }
.nh-table .ty { width: 110px; }
.nh-table .tm { white-space: nowrap; color: #64748b; font-size: 12.5px; }
.nh-table .msg { color: #475569; max-width: 260px; }
.nh-table .empty { text-align: center; color: #64748b; padding: 40px; }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; background: #cbd5e1; vertical-align: middle; }
.dot.on { background: #dc3b3b; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 11.5px; font-weight: 700; background: #eef1f6; color: #334155; }
.badge.fire { background: #fdecec; color: #8f1d1d; }
.badge.batt { background: #fff1e6; color: #9a3412; }
.badge.estop { background: #fbe9ef; color: #9d174d; }
.badge.temp { background: #fff4dc; color: #92400e; }
@media (max-width: 1100px) { .sum-cards { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 800px) { .sum-cards { grid-template-columns: repeat(2, 1fr); } }
</style>
