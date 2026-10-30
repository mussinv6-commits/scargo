<template>
<section class="scr" id="W-10"><div class="card"><h3>기사 가입 승인 <span style="margin-left:auto;font-size:14px;font-weight:600;color:#4a5570">상태: <select v-model="st" @change="load"><option v-for="t in tabs" :key="t[0]" :value="t[0]">{{ t[1] }}</option></select></span></h3>
<table :style="pg.reset"><thead><tr><th>이름 (아이디)</th><th>권한</th><th>소속 운송사</th><th>차량</th><th class="c">면허번호</th><th class="c">신청</th><th class="c">처리</th></tr></thead>
<tbody><tr v-for="r in pg.rows" :key="r.accountId"><td>{{ r.userName }} ({{ r.userId }})</td><td>{{ r.userType === 'CONTAINER_DRIVER' ? '컨테이너 운송기사' : '지선 운송기사' }}</td><td>{{ r.companyName }}</td><td>{{ r.vehicleNo || '—' }} · {{ r.truckType || '' }}</td><td>{{ r.licenseNo || '—' }}</td><td>{{ md(r.createdAt) }}</td>
<td><template v-if="r.status === 'PENDING'"><button class="btn-act edit" @click="act(r, 'approve')">승인</button> <button class="btn-act del" @click="act(r, 'reject')">반려</button></template><span v-else :class="'b ' + (r.status === 'APPROVED' ? 'ok' : 'crit')">{{ r.status === 'APPROVED' ? '승인' : '반려' }}</span><div v-if="r.rejectReason" class="meta l">{{ r.rejectReason }}</div></td></tr>
<tr v-if="!rows.length"><td colspan="7" class="meta">해당 기사가 없습니다.</td></tr></tbody></table><Pager :p="pg"/></div>
<div v-if="dlg" class="mdl-bg" @click.self="dlg = null"><div class="mdl"><div class="mh">{{ dlg.title }}</div><div class="mb">
<div>{{ dlg.text }}</div><template v-if="dlg.reason !== undefined"><label class="meta">반려 사유 (필수)</label><textarea v-model="dlg.reason" placeholder="예) 면허 정보 불일치"></textarea></template>
<div v-if="dlg.err" class="err">{{ dlg.err }}</div></div><div class="mf"><button class="btn o" @click="dlg = null">취소</button><button class="btn y" @click="dlg.ok()">확인</button></div></div></div>
<div v-if="toast" :class="'toast' + (bad ? ' bad' : '')">{{ toast }}</div></section>
</template>
<script setup>
import Pager from '../comp/Pager.vue'
import { usePage } from '../comp/pager.js'
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC } from '../api.js'
import { authState } from '@/auth/authState.js'
const props = defineProps(['tick', 'role'])
// 기사 가입 승인: GET /api/web/driver-approvals?status=, POST /api/web/driver-approvals/{id}/approve|reject
const tabs = [['PENDING', '승인대기'], ['APPROVED', '승인'], ['REJECTED', '반려']], st = ref('PENDING'), rows = ref([])
const dlg = ref(null), toast = ref(''), bad = ref(false)
function say(m, b) { toast.value = m; bad.value = !!b; setTimeout(() => (toast.value = ''), 2600) }
const url = (r, a) => '/api/web/driver-approvals/' + r.accountId + '/' + a
async function send(r, a, body) {
  let x = await req('POST', url(r, a), body)
  if (!x.ok && props.role === 'ADMIN' && /운송사에서 승인/.test(x.msg)) {
    dlg.value = { title: '총관리자 대리 처리', text: x.msg + ' 총관리자로 대리 처리할까요?', ok: async () => { const y = await req('POST', url(r, a), { ...body, force: 'true' }); dlg.value = null; y.ok ? say('처리되었습니다.') : say(y.msg, true); load() } }
    return
  }
  x.ok ? say(a === 'approve' ? '승인되었습니다.' : '반려되었습니다.') : say(x.msg, true); load()
}
function act(r, a) {
  if (a === 'approve') { dlg.value = { title: '가입 승인', text: r.userName + ' (' + r.userId + ') 기사를 승인할까요?', ok: () => { dlg.value = null; send(r, a, {}) } }; return }
  dlg.value = { title: '가입 반려', text: r.userName + ' (' + r.userId + ') 기사를 반려합니다.', reason: '', err: '', ok: () => { const s = (dlg.value.reason || '').trim(); if (!s) { dlg.value.err = '반려 사유를 입력하세요.'; return } dlg.value = null; send(r, a, { reason: s }) } }
}
async function load() { const l = await get('/api/web/driver-approvals?status=' + st.value); if (Array.isArray(l)) rows.value = l }
load(); watch(() => props.tick, load)
// fix_1434: 표 페이지 나누기
const pg = usePage(rows, 10)
</script>
