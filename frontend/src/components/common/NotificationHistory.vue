<template>
  <!-- 26.10.28 알림 내역: 종 버튼은 최신 5건만 보여서, 지난 알림까지 모두 보고 찾을 수 있는 화면을 따로 둔다
       관리자 = /admin/notifications (관리자 사이드바 '설정 · 안내'), 그 밖의 계정 = /notifications -->
  <div class="nh" :class="{ standalone }">
    <AdminPageHeader title="알림 내역" description="받은 알림을 지난 것까지 모두 봅니다. 알림을 누르면 관련 화면으로 이동합니다." />

    <section class="nh-card">
    <!-- 26.10.28 탭과 내용을 하나의 흰 상자 안에 (지역센터 인수 현황과 같은 모양) -->
    <div class="nh-tabs" role="tablist">
        <button v-for="t in READ_TABS" :key="t.key" type="button" :class="{ on: readTab === t.key }" @click="readTab = t.key">
          {{ t.label }}<span class="n">{{ countOf(t.key) }}</span>
        </button>
      </div>
      <div class="nh-bar">
        <label>종류
          <select v-model="typeFilter" class="nh-in">
            <option value="">전체</option>
            <option v-for="(label, key) in NOTIF_TYPE" :key="key" :value="key">{{ label }}</option>
          </select>
        </label>
        <input v-model.trim="keyword" class="nh-in grow" placeholder="제목 · 내용 검색" />
        <button type="button" class="nh-btn" :disabled="loading" @click="load">{{ loading ? '불러오는 중...' : '새로고침' }}</button>
        <button type="button" class="nh-btn pri" :disabled="markingAll || !unread" @click="markAllRead">모두 읽음</button>
      </div>
      <p v-if="error" class="nh-msg">{{ error }}</p>
      <!-- 26.10.28 알림을 눌렀는데 따로 갈 화면이 없으면 여기서 그 알림 내용을 크게 보여 준다 (?id=) -->
      <div v-if="focused" class="nh-focus">
        <div class="nh-focus-h"><span class="badge">{{ NOTIF_TYPE[focused.notificationType] || focused.notificationType || '알림' }}</span><b>{{ focused.title }}</b><span class="tm">{{ fmt(focused.createdAt) }}</span><button type="button" class="nh-x" aria-label="닫기" @click="clearFocus">×</button></div>
        <p>{{ focused.message || '내용 없음' }}</p>
      </div>

      <table class="nh-table">
        <thead><tr><th class="no">번호</th><th class="st">상태</th><th class="ty">종류</th><th>제목</th><th>내용</th><th class="tm">받은 시각</th></tr></thead>
        <tbody>
          <tr v-if="!pg.rows.length" class="empty"><td colspan="6">{{ loading ? '불러오는 중...' : '알림이 없습니다.' }}</td></tr>
          <tr v-for="(n, i) in pg.rows" :key="n.notificationId" :class="{ unread: !n.read, focus: focused && focused.notificationId === n.notificationId }" @click="open(n)">
            <td class="no">{{ pg.no(i) }}</td>
            <td class="st"><span class="dot" :class="{ on: !n.read }"></span>{{ n.read ? '읽음' : '안 읽음' }}</td>
            <td class="ty"><span class="badge">{{ NOTIF_TYPE[n.notificationType] || n.notificationType || '알림' }}</span></td>
            <td class="ttl is-left">{{ n.title }}</td>
            <td class="msg is-left">{{ n.message }}</td>
            <td class="tm">{{ fmt(n.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <AdminPager v-model="pg.page" :page-count="pg.pageCount" />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authState } from '@/auth/authState.js'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'
import { resolveTarget, toDate, NOTIF_TYPE } from '@/utils/notificationTarget.js'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import AdminPager from '@/components/admin/AdminPager.vue'
import { usePaged } from '@/utils/usePaged.js'

defineProps({ standalone: { type: Boolean, default: false } })

const router = useRouter()
const route = useRoute()

const READ_TABS = [{ key: 'all', label: '전체' }, { key: 'unread', label: '안 읽음' }, { key: 'read', label: '읽음' }]
const rows = ref([])
const loading = ref(false)
const markingAll = ref(false)
const error = ref('')
const readTab = ref('all')
const typeFilter = ref('')
const keyword = ref('')

const unread = computed(() => rows.value.filter((n) => !n.read).length)
function countOf(k) {
  if (k === 'unread') return unread.value
  if (k === 'read') return rows.value.length - unread.value
  return rows.value.length
}
const filtered = computed(() => {
  const kw = keyword.value.toLowerCase()
  return rows.value
    .filter((n) => (readTab.value === 'unread' ? !n.read : readTab.value === 'read' ? n.read : true))
    .filter((n) => !typeFilter.value || n.notificationType === typeFilter.value)
    .filter((n) => !kw || `${n.title || ''} ${n.message || ''}`.toLowerCase().includes(kw))
})
const pg = usePaged(filtered, 10)
watch([readTab, typeFilter, keyword], () => { pg.page = 1 })
// 26.10.28 ?id= 로 들어오면 그 알림을 위에 크게 보여 주고, 목록에서도 그 줄이 있는 쪽으로 이동
const focusId = computed(() => (route.query.id != null ? String(route.query.id) : ''))
const focused = computed(() => (focusId.value ? rows.value.find((n) => String(n.notificationId) === focusId.value) || null : null))
function clearFocus() { const q = { ...route.query }; delete q.id; router.replace({ query: q }) }
function showFocus() {
  if (!focused.value) return
  readTab.value = 'all'; typeFilter.value = ''; keyword.value = ''
  const idx = filtered.value.findIndex((n) => n.notificationId === focused.value.notificationId)
  if (idx >= 0) setTimeout(() => { pg.page = Math.floor(idx / 10) + 1 })
}
watch(focused, showFocus)

const fmt = (d) => (d ? toDate(d).toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-')

async function load() {
  const id = authState.user?.accountId
  if (!id) { error.value = '로그인 정보가 없습니다.'; return }
  loading.value = true
  error.value = ''
  try {
    // 지난 알림까지 한 번에 받아 화면에서 나눠 보여 준다 (서버는 최신순)
    const res = await adminApi.get(`/api/notifications/account/${id}`, { params: { page: 0, size: 1000 } })
    const list = res.data?.content ?? res.data
    rows.value = Array.isArray(list) ? list : []
  } catch (err) {
    rows.value = []
    error.value = pickErrorMessage(err, '알림 내역을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

async function open(n) {
  if (!n.read) {
    try { await adminApi.patch(`/api/notifications/${n.notificationId}/read`); n.read = true } catch { /* 읽음 처리 실패해도 이동은 한다 */ }
  }
  const target = resolveTarget(n)
  // 따로 갈 화면이 없는 알림(알림 내역 자신)은 이 화면 위쪽에 내용을 보여 준다
  const self = typeof target === 'object' && target && /notifications$/.test(target.path)
  if (self) router.replace({ query: { ...route.query, id: n.notificationId } })
  else if (target) router.push(target)
}

async function markAllRead() {
  const id = authState.user?.accountId
  if (!id) return
  markingAll.value = true
  try {
    await adminApi.patch(`/api/notifications/account/${id}/read-all`)
    rows.value.forEach((n) => { n.read = true })
  } catch (err) {
    error.value = pickErrorMessage(err, '모두 읽음 처리에 실패했습니다.')
  } finally {
    markingAll.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.nh { color: #112847; }
.nh.standalone { max-width: 1200px; margin: 0 auto; padding: 24px 20px 40px; }
.nh-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; }
.nh-tabs button { border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 8px; padding: 7px 14px; font-size: 13.5px; font-weight: 600; cursor: pointer; display: inline-flex; gap: 6px; align-items: center; }
.nh-tabs button:hover:not(.on) { border-color: #1d4ed8; color: #1d4ed8; }
.nh-tabs button.on { background: #1d4ed8; border-color: #1d4ed8; color: #fff; font-weight: 700; }
.nh-tabs .n { min-width: 20px; height: 20px; padding: 0 6px; border-radius: 10px; background: #eef1f6; color: #0b1d36; font-size: 12px; display: inline-flex; align-items: center; justify-content: center; }
.nh-tabs button.on .n { background: rgba(255, 255, 255, .55); }
.nh-card { background: #fff; border-radius: 12px; padding: 16px 18px; box-shadow: 0 1px 3px rgba(17, 40, 71, .08); }
.nh-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.nh-bar label { display: flex; align-items: center; gap: 8px; font-size: 13.5px; font-weight: 700; margin: 0; }
.nh-in { border: 1px solid #c9d3e0; border-radius: 7px; padding: 6px 10px; font-size: 13px; height: 34px; }
.nh-in.grow { flex: 1 1 220px; min-width: 180px; }
.nh-btn { border: 1px solid #c9d3e0; background: #fff; color: #112847; border-radius: 7px; padding: 0 14px; height: 34px; font-size: 13px; font-weight: 700; cursor: pointer; }
.nh-btn:hover:not(:disabled) { border-color: #112847; background: #f4f7fb; }
.nh-btn.pri { background: #c9972f; border-color: #c9972f; color: #fff; }
.nh-btn.pri:hover:not(:disabled) { background: #dcb466; border-color: #dcb466; } /* 26.10.28 오버 시 연하게 */
.nh-btn:disabled { opacity: .5; cursor: default; }
.nh-msg { color: #dc3b3b; font-size: 13px; margin: 0 0 10px; }
.nh-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.nh-table th { background: #eef1f6; color: #64748b; font-weight: 700; font-size: 12.5px; padding: 10px; text-align: left; white-space: nowrap; }
.nh-table td { padding: 10px; border-bottom: 1px solid #e3e8ef; vertical-align: middle; }
.nh-table tbody tr { cursor: pointer; }
.nh-table tbody tr:hover td { background: #f8fbff; }
.nh-table tbody tr.empty { cursor: default; }
.nh-table tbody tr.empty:hover td { background: transparent; }
.nh-table tbody tr.empty td { text-align: center; color: #64748b; padding: 40px; }
.nh-table tr.unread .ttl { font-weight: 800; }
.nh-table .no { width: 60px; text-align: center; color: #64748b; }
.nh-table .st { width: 90px; white-space: nowrap; }
.nh-table .ty { width: 100px; }
.nh-table .tm { width: 170px; white-space: nowrap; color: #64748b; }
.nh-table .msg { color: #475569; }
.nh-focus { border: 1px solid #c9d8f5; background: #f4f8ff; border-radius: 10px; padding: 12px 14px; margin-bottom: 12px; } /* 26.10.28 선택한 알림 */
.nh-focus-h { display: flex; align-items: center; gap: 8px; }
.nh-focus-h b { font-size: 14.5px; color: #112847; }
.nh-focus-h .tm { margin-left: auto; color: #64748b; font-size: 12.5px; }
.nh-focus p { margin: 8px 0 0; color: #334155; font-size: 13.5px; line-height: 1.55; white-space: pre-line; }
.nh-x { border: 0; background: transparent; font-size: 20px; line-height: 1; color: #64748b; cursor: pointer; padding: 0 4px; }
.nh-x:hover { color: #112847; }
.nh-table tr.focus td { background: #eef4ff !important; }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #cbd5e1; margin-right: 6px; vertical-align: middle; }
.dot.on { background: #dc3b3b; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: 700; background: #e8f0fe; color: #1d4ed8; white-space: nowrap; }
</style>
