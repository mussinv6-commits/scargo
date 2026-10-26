<template>
  <!-- 26.10.21 보안: AMR 앱 출입증 관리 (총관리자 · S-Bot 관리자). 누가 보여 준 코드로 발급됐는지, 언제 마지막으로 썼는지 확인하고 폐기 -->
  <section class="amr-tok" aria-labelledby="amr-tok-title">
    <header>
      <h2 id="amr-tok-title">AMR 앱 출입증</h2>
      <span class="sub">24시간 유효 · 폐기하면 즉시 차단</span>
      <button type="button" class="btn" @click="load" :disabled="busy">새로고침</button>
      <button type="button" class="btn danger" @click="revokeAll" :disabled="busy || !rows.length">전체 폐기</button>
    </header>
    <p v-if="err" class="err" role="alert">{{ err }}</p>
    <table>
      <thead><tr><th>번호</th><th>종류</th><th>코드 제공 관리자</th><th>발급</th><th>만료</th><th>마지막 사용</th><th>IP · 기기</th><th></th></tr></thead>
      <tbody>
        <tr v-for="t in rows" :key="t.ref">
          <td class="mono">{{ t.ref }}</td><td>{{ t.client }}</td><td>{{ t.issuedByUserId || '-' }}</td>
          <td>{{ fmt(t.issuedAt) }}</td><td>{{ fmt(t.expiresAt) }}</td><td>{{ fmt(t.lastUsedAt) }}</td>
          <td class="ua" :title="t.userAgent">{{ t.ip }} · {{ short(t.userAgent) }}</td>
          <td><button type="button" class="btn danger sm" @click="revoke(t.ref)" :disabled="busy" :aria-label="'출입증 ' + t.ref + ' 폐기'">폐기</button></td>
        </tr>
        <tr v-if="!rows.length"><td colspan="8" class="empty">살아 있는 출입증이 없습니다</td></tr>
      </tbody>
    </table>
    <details class="fails" @toggle="(e) => e.target.open && loadFails()">
      <summary>코드 실패 기록 (최근 50건)</summary>
      <ul><li v-for="f in fails" :key="f.failureId"><span class="mono">{{ fmt(f.occurredAt) }}</span> · {{ f.reason }} · {{ f.ip }}</li>
        <li v-if="!fails.length">기록 없음</li></ul>
    </details>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { API_BASE } from '@/utils/apiBase.js'

const rows = ref([]), fails = ref([]), err = ref(''), busy = ref(false)
const opt = { withCredentials: true }
// 서버 시각은 epoch 초(숫자) → ms 로 바꿔 KST 표시
const fmt = (v) => (v == null ? '-' : new Date(typeof v === 'number' ? v * 1000 : v).toLocaleString('ko-KR', { hour12: false, timeZone: 'Asia/Seoul' }))
const short = (ua) => (!ua ? '-' : /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS' : /Chrome/.test(ua) ? 'Chrome' : ua.slice(0, 18))
const msg = (e) => e.response?.data?.message || '요청 실패 (관리자 로그인 필요)'

async function load() { busy.value = true; err.value = ''
  try { rows.value = (await axios.get(`${API_BASE}/api/amr/auth/tokens`, opt)).data } catch (e) { err.value = msg(e) } finally { busy.value = false } }
async function loadFails() { try { fails.value = (await axios.get(`${API_BASE}/api/amr/auth/failures`, opt)).data } catch (e) { err.value = msg(e) } }
async function revoke(ref_) { if (!confirm(`출입증 ${ref_} 을(를) 폐기할까요? 그 기기는 즉시 차단됩니다.`)) return
  busy.value = true; try { await axios.post(`${API_BASE}/api/amr/auth/tokens/${ref_}/revoke`, {}, opt) } catch (e) { err.value = msg(e) } busy.value = false; load() }
async function revokeAll() { if (!confirm('모든 AMR 앱 출입증을 폐기할까요?')) return
  busy.value = true; try { await axios.post(`${API_BASE}/api/amr/auth/tokens/revoke-all`, {}, opt) } catch (e) { err.value = msg(e) } busy.value = false; load() }
onMounted(load)
</script>

<style scoped>
.amr-tok { margin: 16px; padding: 16px; border-radius: 12px; background: #0f2a4a; color: #e6eff7; border: 1px solid #1d3b60; }
.amr-tok header { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 10px; }
.amr-tok h2 { margin: 0; font-size: 18px; }
.sub { color: #9fb0c6; font-size: 13px; margin-right: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 8px 6px; border-bottom: 1px solid #1d3b60; text-align: left; }
th { color: #9fb0c6; font-weight: 600; }
.mono { font-family: ui-monospace, monospace; }
.ua { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty { color: #9fb0c6; text-align: center; }
.btn { border: 1px solid #3a5a80; background: #16365c; color: #e6eff7; border-radius: 8px; padding: 6px 12px; cursor: pointer; }
.btn.danger { border-color: #b42318; background: #5a1a14; }
.btn.sm { padding: 3px 10px; }
.btn:disabled { opacity: .5; cursor: default; }
.err { color: #ff9a8a; }
.fails { margin-top: 12px; color: #c9d8ea; font-size: 13px; }
</style>
