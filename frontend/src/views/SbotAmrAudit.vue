<template>
  <!-- 26.10.23 AMR 앱 출입증 감사 (/sbot/amr-audit). S-Bot 관리자 = 전체 출입증 + 실패 기록, HUB_OP = 소속 허브 출입증
       토큰 · 코드 값은 서버가 내려주지 않는다. CONTROL 은 서버가 403
       26.10.23 감사 이력은 만료 출입증 정리와 별도로 보존 (tokenRetained = false: 출입증 행은 정리되고 감사 기록만 남음) -->
  <div class="aud">
    <div class="aud-head">
      <h2>앱 출입증 감사</h2>
      <span class="aud-sub">{{ data.hubId ? '소속 허브 #' + data.hubId : '전체 허브' }}</span>
      <button class="aud-btn" :disabled="loading" @click="load">새로고침</button>
    </div>
    <p v-if="error" class="aud-err">{{ error }}</p>

    <section class="aud-card">
      <h3>출입증 발급 · 폐기 ({{ data.tokens.length }})</h3>
      <table class="aud-table">
        <thead><tr><th>발급</th><th>허브</th><th>기기</th><th>IP</th><th>코드 보여 준 계정</th><th>마지막 사용</th><th>상태</th><th>폐기</th></tr></thead>
        <tbody>
          <tr v-if="!data.tokens.length"><td colspan="8" class="aud-empty">출입증 기록이 없습니다.</td></tr>
          <tr v-for="(t, i) in data.tokens" :key="i">
            <td>{{ formatDateTime(t.createdAt) }}</td>
            <td>{{ t.hubId ?? '전체' }}</td>
            <td :title="t.userAgent">{{ t.client }}{{ t.userAgent ? ' · ' + shortAgent(t.userAgent) : '' }}</td>
            <td>{{ t.ip || '-' }}</td>
            <td>{{ t.issuedByUserId || '-' }}</td>
            <td>{{ t.tokenRetained === false ? '-' : formatDateTime(t.lastUsedAt) }}</td>
            <td>{{ t.revoked ? '폐기' : (isExpired(t) ? (t.tokenRetained === false ? '만료 · 출입증 정리됨' : '만료') : '사용 중') }}</td>
            <td>{{ t.revoked ? (REVOKE_REASON_LABEL[t.revokeReason] || t.revokeReason || '-') + (t.revokedBy ? ' · ' + t.revokedBy : '') + ' · ' + formatDateTime(t.revokedAt) : '-' }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="!data.hubId" class="aud-card">
      <h3>연결 실패 ({{ data.failures.length }})</h3>
      <table class="aud-table">
        <thead><tr><th>시각</th><th>IP</th><th>사유</th><th>기기</th></tr></thead>
        <tbody>
          <tr v-if="!data.failures.length"><td colspan="4" class="aud-empty">실패 기록이 없습니다.</td></tr>
          <tr v-for="(f, i) in data.failures" :key="i">
            <td>{{ formatDateTime(f.occurredAt) }}</td><td>{{ f.ip || '-' }}</td>
            <td class="is-left">{{ FAILURE_REASON_LABEL[f.reason] || f.reason }}</td><td :title="f.userAgent">{{ shortAgent(f.userAgent) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { amrAuthAudit, REVOKE_REASON_LABEL, FAILURE_REASON_LABEL } from '@/utils/amrAuditApi.js'
import { formatDateTime } from '@/utils/containerInspectionApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'

const data = reactive({ tokens: [], failures: [], hubId: null })
const loading = ref(false)
const error = ref('')

function shortAgent(v) {
  if (!v) return '-'
  return v.length > 40 ? v.slice(0, 40) + '…' : v
}
function isExpired(t) {
  const v = t.expiresAt
  const d = new Date(typeof v === 'number' && v < 1e12 ? v * 1000 : v)
  return d.getTime() < Date.now()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    Object.assign(data, await amrAuthAudit())
  } catch (err) {
    error.value = friendlyError(err, '출입증 감사 기록을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.aud { display: flex; flex-direction: column; gap: 12px; }
.aud-head { display: flex; align-items: center; gap: 12px; }
.aud-head h2 { margin: 0; font-size: 19px; }
.aud-sub { color: #9fb0c6; font-size: 13px; }
.aud-head .aud-btn { margin-left: auto; }
.aud-card { background: #0f2a4a; border: 1px solid #1d3b60; border-radius: 12px; padding: 12px 14px; overflow-x: auto; }
.aud-card h3 { margin: 0 0 8px; font-size: 15px; }
.aud-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.aud-table th, .aud-table td { padding: 7px 8px; border-bottom: 1px solid #1d3b60; text-align: left; white-space: nowrap; }
.aud-table th { color: #9fb0c6; font-weight: 600; }
.aud-empty { text-align: center !important; color: #9fb0c6; }
.aud-btn { background: #12304f; border: 1px solid #2a4d76; color: #e6eff7; border-radius: 6px; padding: 6px 12px; font-size: 13px; cursor: pointer; }
.aud-err { margin: 0; color: #fca5a5; font-size: 13px; }
</style>
