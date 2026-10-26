<template>
<section class="scr" id="W-16"><div class="card"><h3>직원관리 <span class="meta">· 웹 관제 계정</span></h3><table :style="pg.reset"><tr><th>이름 (아이디)</th><th class="c">권한</th><th>소속</th><th class="c">연락처</th><th class="c">관리</th></tr>
<tr v-for="r in pg.rows" :key="r.accountId"><td>{{ r.userName }} <span class="meta">({{ r.userId }})</span></td><td class="c"><span class="b info">{{ ROLE[r.role] || r.role }}</span></td><td>{{ r.site || '—' }}</td><td class="c">{{ r.phone || '—' }}</td>
<td class="c"><button class="btn o" @click="edit(r)"><i class="bi bi-pencil"></i> 수정</button> <button class="btn r" @click="del = r"><i class="bi bi-trash"></i> 삭제</button></td></tr>
<tr v-if="!rows.length"><td colspan="5" class="meta">등록된 직원이 없습니다.</td></tr></table><Pager :p="pg"/></div>
<div v-if="f" class="mdl-bg" @click.self="f = null"><div class="mdl"><div class="mh">직원 정보 수정 · {{ f.userId }}</div><div class="mb">
<label class="meta">이름</label><input v-model="f.userName">
<label class="meta">연락처</label><input v-model="f.phone" placeholder="010-0000-0000">
<label class="meta">권한</label><select v-model="f.role"><option v-for="k in STAFF" :key="k" :value="k">{{ ROLE[k] }}</option></select>
<template v-if="f.role === 'HUB_OP'"><label class="meta">소속 허브</label><select v-model="f.hubId"><option :value="null">선택</option><option v-for="h in opt.hubs" :key="h.id" :value="h.id">{{ h.name }}</option></select></template>
<template v-if="f.role === 'CENTER_OP'"><label class="meta">소속 센터</label><select v-model="f.centerId"><option :value="null">선택</option><option v-for="c in opt.centers" :key="c.id" :value="c.id">{{ c.name }}</option></select></template>
<div v-if="err" class="err">{{ err }}</div></div>
<div class="mf"><button class="btn o" @click="f = null">취소</button><button class="btn y" :disabled="busy" @click="save">저장</button></div></div></div>
<div v-if="del" class="mdl-bg" @click.self="del = null"><div class="mdl"><div class="mh">직원 삭제</div><div class="mb"><div><b>{{ del.userName }}</b> ({{ del.userId }} · {{ ROLE[del.role] }}) 계정을 삭제할까요?</div><div class="meta">삭제하면 되돌릴 수 없습니다. 처리 이력이 있는 계정은 삭제되지 않습니다.</div><div v-if="err" class="err">{{ err }}</div></div>
<div class="mf"><button class="btn o" @click="del = null; err = ''">취소</button><button class="btn r" :disabled="busy" @click="remove">삭제</button></div></div></div>
<div v-if="toast" class="toast">{{ toast }}</div></section>
</template>
<script setup>
import Pager from '../comp/Pager.vue'
import { usePage } from '../comp/pager.js'
import { ref, watch } from 'vue'
import { get, req } from '../api.js'
const props = defineProps(['tick', 'role'])
// 직원관리: GET /api/web/staff, PUT/DELETE /api/web/staff/{id}, GET /api/web/staff/options (ADMIN)
const ROLE = { ADMIN: '총관리자', CONTROL: '통합 관제사', HUB_OP: '허브 관제사', CENTER_OP: '센터 관제사', CORPORATE_APPROVED: '운송사', CORPORATE_PENDING: '운송사 (승인 대기)', CONTAINER_DRIVER: '컨테이너 기사', BRANCH_DRIVER: '지선 기사' }
const STAFF = ['CONTROL', 'HUB_OP', 'CENTER_OP']
const rows = ref([]), opt = ref({ hubs: [], centers: [] }), f = ref(null), del = ref(null), err = ref(''), busy = ref(false), toast = ref('')
function say(m) { toast.value = m; setTimeout(() => (toast.value = ''), 2500) }
function edit(r) { err.value = ''; f.value = { ...r } }
async function save() { busy.value = true; err.value = ''; const x = await req('PUT', '/api/web/staff/' + f.value.accountId, f.value); busy.value = false; if (!x.ok) { err.value = x.msg; return } rows.value = x.data; f.value = null; say('수정되었습니다.') }
async function remove() { busy.value = true; err.value = ''; const x = await req('DELETE', '/api/web/staff/' + del.value.accountId); busy.value = false; if (!x.ok) { err.value = x.msg; return } rows.value = x.data; del.value = null; say('삭제되었습니다.') }
async function load() { if (f.value || del.value) return; const l = await get('/api/web/staff'); if (Array.isArray(l)) rows.value = l }
get('/api/web/staff/options').then(o => { if (o) opt.value = o })
load(); watch(() => props.tick, load)
// fix_1434: 표 페이지 나누기
const pg = usePage(rows, 10)
</script>
