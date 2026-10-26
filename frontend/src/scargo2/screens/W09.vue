<template>
<section class="scr" id="W-09"><div class="card"><h3>예외 알림 센터</h3><table :style="pg.reset"><tr><th>등급</th><th>유형</th><th>발견</th><th>거점 · 대상</th><th>담당</th><th>발생</th><th>상태</th><th></th></tr><tbody id="exfull">
<tr v-for="e in pg.rows" :key="e.exceptionId"><td><span :class="'b ' + lvc(e.severity)">{{ lv[e.severity] || e.severity }}</span></td><td><span :class="'b gray' + (String(EXL[e.type] || e.type).includes(' ') ? ' wrap2' : '')">{{ EXL[e.type] || e.type }}</span></td><td>{{ SRC[e.sourceTable] || e.sourceTable || '—' }}</td><td>{{ e.centerName || '' }} {{ e.message }}</td><td>{{ e.assignee || '—' }}</td><td>{{ md(e.occurredAt) }}</td><td><span class="b gray">{{ stl(e.status) }}</span></td><td><button v-if="e.status !== 'RESOLVED' && role !== 'ADMIN' && role !== 'carrier'" class="btn o" @click="done(e)">처리 완료</button></td></tr>
<tr v-if="!ex.length"><td colspan="8" class="meta">예외가 없습니다.</td></tr></tbody></table><Pager :p="pg"/></div>
<div v-if="cur" class="mdl-bg" @click.self="close"><div class="mdl"><div class="mh">예외 처리 완료</div><div class="mb">
<div><span :class="'b ' + lvc(cur.severity)">{{ lv[cur.severity] || cur.severity }}</span> <b>{{ EXL[cur.type] || cur.type }}</b></div>
<div class="meta">{{ cur.centerName || '' }} {{ cur.message }}</div>
<label class="meta" for="w09note">처리 내용 (필수)</label><textarea id="w09note" v-model="note" placeholder="예) 현장 확인 후 재봉인 완료" :disabled="busy"></textarea>
<div v-if="err" class="err">{{ err }}</div></div>
<div class="mf"><button class="btn o" @click="close" :disabled="busy">취소</button><button class="btn y" @click="submit" :disabled="busy">{{ busy ? '처리 중…' : '확인' }}</button></div></div></div>
<div v-if="toast" class="toast">{{ toast }}</div></section>
</template>
<script setup>
import Pager from '../comp/Pager.vue'
import { usePage } from '../comp/pager.js'
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC, stl } from '../api.js'
import { authState } from '@/auth/authState.js'
const props = defineProps(['tick', 'role'])
// 예외 알림 센터: GET /api/web/exceptions/view (권한별 범위). 처리 PUT /api/web/exceptions/{id} (OPEN→IN_PROGRESS→RESOLVED, 처리 내용 필수). ADMIN·운송사는 조회만
const ex = ref([])
const cur = ref(null), note = ref(''), err = ref(''), busy = ref(false), toast = ref('')
function done(e) { cur.value = e; note.value = ''; err.value = '' }
function close() { if (!busy.value) cur.value = null }
async function submit() {
  const e = cur.value; if (!note.value.trim()) { err.value = '처리 내용을 입력하세요.'; return }
  busy.value = true; err.value = ''
  try {
    if (e.status === 'OPEN') { const r = await req('PUT', '/api/web/exceptions/' + e.exceptionId); if (!r.ok) { err.value = r.msg; return } }
    const r = await req('PUT', '/api/web/exceptions/' + e.exceptionId, { note: note.value.trim() }); if (!r.ok) { err.value = r.msg; return }
    cur.value = null; toast.value = '처리 완료되었습니다.'; setTimeout(() => (toast.value = ''), 2500); load()
  } finally { busy.value = false }
}
async function load() { const l = await get('/api/web/exceptions/view'); if (Array.isArray(l)) ex.value = l }
load(); watch(() => props.tick, load)
// fix_1434: 표 페이지 나누기
const pg = usePage(ex, 10)
</script>
