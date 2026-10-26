<template>
<section class="scr" id="W-14"><div class="card"><h3>고속도로 요금 (차종별)</h3><table><tr><th>차종</th><th>축</th><th>기본 요금</th><th>km당 요금</th><th></th></tr>
<tr v-for="r in rows" :key="r.cls"><td>{{ r.name }}</td><td>{{ r.axles }}</td><td>{{ r.base }}원</td><td><input v-if="role === 'ADMIN'" type="number" step="0.1" v-model.number="r.perKm" style="width:90px"><template v-else>{{ r.perKm }}</template>원 <span v-if="r.virtual" class="b gray">가상 요금</span></td><td><button v-if="role === 'ADMIN'" class="btn o" @click="save(r)">저장</button></td></tr></table>
<div v-if="msg" :class="'b ' + (ok ? 'ok' : 'crit')" style="margin-top:8px;display:inline-block">{{ msg }}</div></div></section>
</template>
<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC } from '../api.js'
import { authState } from '@/auth/authState.js'
const props = defineProps(['tick', 'role'])
// 고속도로 요금: GET /api/web/toll-fees, PUT /api/web/toll-fees/{cls} (ADMIN)
const rows = ref([]), msg = ref(''), ok = ref(false)
async function save(r) { const x = await req('PUT', '/api/web/toll-fees/' + r.cls, { perKm: r.perKm }); ok.value = x.ok; msg.value = x.ok ? r.name + ' 저장됨' : x.msg }
async function load() { if (props.role === 'ADMIN' && rows.value.length) return; const l = await get('/api/web/toll-fees'); if (Array.isArray(l)) rows.value = l }
load(); watch(() => props.tick, load)
</script>
