<template>
<section class="scr" id="W-11"><div class="card"><h3>운영 기준 설정</h3><div class="setg"><div v-for="s in rows" :key="s.key" class="setc"><div class="sl">{{ s.label }}</div><div class="sv"><input v-if="role === 'ADMIN'" type="number" min="1" v-model.number="s.value" style="width:90px"><b v-else>{{ s.value }}</b> <span class="meta">{{ s.unit }}</span></div></div></div>
<div v-if="role === 'ADMIN'" class="savebar"><button class="btn y" @click="save">저장</button><span v-if="msg" :class="'b ' + (ok ? 'ok' : 'crit')">{{ msg }}</span></div></div></section>
</template>
<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC } from '../api.js'
import { authState } from '@/auth/authState.js'
const props = defineProps(['tick', 'role'])
// 기준정보: GET /api/web/settings, PUT /api/web/settings (ADMIN)
const rows = ref([]), msg = ref(''), ok = ref(false); let editing = false
async function save() { const r = await req('PUT', '/api/web/settings', Object.fromEntries(rows.value.map(s => [s.key, s.value]))); ok.value = r.ok; msg.value = r.ok ? '저장됨' : r.msg; if (r.ok) rows.value = r.data }
async function load() { if (msg.value && !ok.value) return; const l = await get('/api/web/settings'); if (Array.isArray(l)) rows.value = l }
load(); watch(() => props.tick, load)
</script>
