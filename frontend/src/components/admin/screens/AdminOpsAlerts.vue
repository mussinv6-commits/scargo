<template>
  <!-- 26.10.28 경보 상세: 운영 센터 '지금 확인할 경보'의 긴급 / 주의를 누르면 들어오는 화면. 경보 내용을 모두 보고, 처리할 탭으로 바로 이동 -->
  <div class="oa">
    <AdminPageHeader title="경보 상세" description="운영 관리의 '지금 확인할 경보' 전체 내용입니다. 1분마다 새로 읽고, '처리하러 가기'를 누르면 해당 탭으로 이동합니다." />
    <div class="tab-box">
      <div class="oa-bar">
        <div class="oa-tabs tabs-sticky" role="tablist">
          <button v-for="t in TABS" :key="t.key" type="button" :class="[t.key.toLowerCase(), { on: level === t.key }]" @click="go(t.key)">
            {{ t.label }}<span class="n">{{ count(t.key) }}</span>
          </button>
        </div>
        <span class="oa-muted">{{ updated ? updated.toLocaleTimeString('ko-KR') + ' 기준' : '불러오는 중...' }}</span>
        <button type="button" class="btn-act" :disabled="loading" @click="load">새로고침</button>
        <router-link to="/admin/ops-center" class="btn-act">← 운영 관리</router-link>
      </div>
      <p v-if="error" class="oa-err">{{ error }}</p>
      <table class="oa-table">
        <thead><tr><th class="no">번호</th><th class="lv">등급</th><th>경보</th><th>내용</th><th class="tb">확인할 곳</th><th class="go"></th></tr></thead>
        <tbody>
          <tr v-if="!pg.rows.length"><td colspan="6" class="oa-empty">{{ loading ? '불러오는 중...' : (level === 'CRITICAL' ? '긴급' : '주의') + ' 경보가 없습니다.' }}</td></tr>
          <tr v-for="(a, i) in pg.rows" :key="i">
            <td class="no">{{ pg.no(i) }}</td>
            <td class="lv"><span class="oa-lv" :class="a.level === 'CRITICAL' ? 'c' : 'w'">{{ a.level === 'CRITICAL' ? '긴급' : '주의' }}</span></td>
            <td class="ttl is-left">{{ a.title }}</td>
            <td class="dt is-left">{{ a.detail || '-' }}</td>
            <td class="tb">{{ TAB_LABEL[a.tab] || '-' }}</td>
            <td class="go"><router-link v-if="a.tab" :to="{ path: '/admin/ops-center', query: { tab: a.tab } }" class="btn-act edit">처리하러 가기</router-link></td>
          </tr>
        </tbody>
      </table>
      <AdminPager v-model="pg.page" :page-count="pg.pageCount" />
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import AdminPager from '@/components/admin/AdminPager.vue'
import { opsAlerts } from '@/utils/opsApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'
import { usePaged } from '@/utils/usePaged.js'

const TABS = [{ key: 'CRITICAL', label: '긴급' }, { key: 'WARN', label: '주의' }]
// 운영 센터 탭 이름 (경보가 가리키는 곳)
const TAB_LABEL = { board: '지금 처리할 것', incidents: '예외·사고', reservations: '게이트 예약', report: '일일 보고서', kpi: 'KPI 추이', score: '업체별 실적' }
const route = useRoute()
const router = useRouter()
const level = computed(() => (route.query.level === 'WARN' ? 'WARN' : 'CRITICAL'))
function go(k) { router.replace({ query: { ...route.query, level: k } }) }

const items = ref([])
const loading = ref(false)
const error = ref('')
const updated = ref(null)
const isLevel = (a, k) => (k === 'CRITICAL' ? a.level === 'CRITICAL' : a.level !== 'CRITICAL')
const count = (k) => items.value.filter((a) => isLevel(a, k)).length
const pg = usePaged(() => items.value.filter((a) => isLevel(a, level.value)), 10)
watch(level, () => { pg.page = 1 })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const r = await opsAlerts()
    items.value = Array.isArray(r?.items) ? r.items : []
    updated.value = new Date()
  } catch (err) {
    error.value = friendlyError(err, '경보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}
let timer = null
onMounted(() => { load(); timer = setInterval(load, 60000) })
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.oa-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; }
.oa-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-right: auto; }
.oa-tabs button { display: inline-flex; align-items: center; gap: 6px; border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 8px; padding: 7px 16px; font-size: 13.5px; font-weight: 700; cursor: pointer; }
.oa-tabs button.critical:hover:not(.on) { border-color: #e5484d; color: #c62f2f; }
.oa-tabs button.warn:hover:not(.on) { border-color: #ff9f1c; color: #b86e00; }
.oa-tabs button.critical.on { background: #e5484d; border-color: #e5484d; color: #fff; }
.oa-tabs button.warn.on { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; }
.oa-tabs .n { min-width: 20px; height: 20px; padding: 0 6px; border-radius: 10px; background: #eef1f6; color: #0b1d36; font-size: 12px; display: inline-flex; align-items: center; justify-content: center; }
.oa-tabs button.on .n { background: rgba(255, 255, 255, .6); }
.oa-muted { color: #64748b; font-size: 12.5px; }
.oa-bar .btn-act { text-decoration: none; }
.oa-err { color: #c62f2f; font-size: 13px; margin: 0 0 10px; }
.oa-table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
.oa-table th { background: #eef1f6; color: #4a5872; font-weight: 700; font-size: 12.5px; padding: 10px; text-align: center; white-space: nowrap; }
.oa-table td { padding: 10px; border-bottom: 1px solid #e3e8ef; text-align: center; vertical-align: middle; }
.oa-table td.ttl, .oa-table td.dt { text-align: left; }
.oa-table td.ttl { font-weight: 700; color: #112847; }
.oa-table td.dt { color: #475569; }
.oa-table .no { width: 60px; color: #64748b; } .oa-table .lv { width: 80px; } .oa-table .tb { width: 130px; color: #475569; } .oa-table .go { width: 130px; }
.oa-table td.go .btn-act { text-decoration: none; display: inline-block; }
.oa-empty { color: #64748b; padding: 36px !important; }
.oa-lv { display: inline-block; padding: 2px 10px; border-radius: 10px; font-size: 12px; font-weight: 700; }
.oa-lv.c { background: #fdecec; color: #c62f2f; } .oa-lv.w { background: #fff3e0; color: #b86e00; }
</style>
