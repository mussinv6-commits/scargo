<template>
  <li class="nav-item notif-bell-wrap" v-click-outside="closePanel">
    <button class="notif-bell-btn" @click="togglePanel" aria-label="알림">
      <!-- 26.09.21 수정: assets/bell.png (프로젝트에 이미 저장된 종 이미지)로 교체 -->
      <img :src="bellIcon" alt="" class="notif-bell-icon" />
      <span v-if="totalUnread > 0" class="notif-badge">{{ totalUnread > 99 ? '99+' : totalUnread }}</span>
    </button>

    <div v-if="panelOpen" class="notif-panel">
      <div class="notif-panel-header">
        <span>알림</span>
        <button
          class="notif-readall-btn"
          v-if="totalUnread > 0"
          :disabled="markingAll"
          @click="markAllRead"
        >
          {{ markingAll ? '처리 중...' : '모두 읽음' }}
        </button>
      </div>

      <div class="notif-list">
        <div v-if="loading" class="notif-empty">불러오는 중...</div>
        <div v-else-if="error" class="notif-empty">{{ error }}</div>
        <div v-else-if="shown.length === 0" class="notif-empty">없음</div>
        <div
          v-for="n in shown"
          :key="n.notificationId"
          class="notif-item"
          :class="{ unread: !n.read }"
          @click="handleItemClick(n)"
        >
          <div class="notif-item-top">
            <span class="notif-title">{{ n.title }}</span>
            <span class="notif-time">{{ formatTime(n.createdAt) }}</span>
          </div>
          <p class="notif-message">{{ n.message }}</p>
        </div>
      </div>
      <!-- 26.10.28 지난 알림까지 모두 보는 화면으로 -->
      <button type="button" class="notif-all-btn" @click="openHistory">전체 알림 내역 보기 ›</button>
    </div>
  </li>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { authState } from '@/auth/authState.js'
import { API_BASE } from '@/utils/apiBase.js'
import { resolveTarget, historyPath } from '@/utils/notificationTarget.js'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'
// 26.09.21 추가: assets 폴더에 저장된 종 이미지(bell.png)를 아이콘으로 사용
import bellIcon from '@/assets/bell.png'

const router = useRouter()
const panelOpen = ref(false)
const notifications = ref([])
const unreadCount = ref(0)
const loading = ref(false)
const markingAll = ref(false)
const error = ref('')

let pollTimer = null

// 26.10.29 추가: 화재 경보(서버 /api/amr/safety/alert)가 진행 중이면 종 알림 맨 위에 긴급 알림으로 함께 표시
//  (상단 공지 띠·팝업과 같은 경보를 알림 목록에서도 확인). 경보가 끝나면 자동으로 사라짐
const live = ref(null)
const liveRead = ref(false)
let liveSince = 0
async function fetchLive() {
  try {
    const c = await fetch(`${API_BASE}/api/amr/safety/alert`, { credentials: 'include' }).then((r) => (r.ok ? r.json() : null))
    if (c && c.active) {
      if (!live.value) { liveSince = Date.now(); liveRead.value = false }
      live.value = c
    } else { live.value = null; liveRead.value = false }
  } catch { /* 서버 꺼짐 */ }
}
const liveItem = computed(() => {
  const c = live.value
  if (!c) return null
  const batt = c.kind === 'batt'
  return {
    notificationId: 'sf-live', sf: true, read: liveRead.value, notificationType: 'SYSTEM',
    title: batt ? '[긴급] AMR 배터리 화재가 발생했습니다' : '[긴급] 허브에 화재가 발생했습니다',
    message: batt ? '허브 내 AMR 배터리 화재 발생 — 허브 진입·운행 시 서행하고 관제 안내에 따라 주세요.' : '허브 내 화재 발생 — 허브 진입·운행 시 차량 운행에 주의하고 관제 안내에 따라 주세요.',
    createdAt: c.startedAt || c.createdAt || c.at || liveSince,
  }
})
const shown = computed(() => (liveItem.value ? [liveItem.value, ...notifications.value] : notifications.value))
const totalUnread = computed(() => unreadCount.value + (liveItem.value && !liveRead.value ? 1 : 0))

function accountId() {
  return authState.user?.accountId
}

async function fetchUnreadCount() {
  const id = accountId()
  if (!id) return
  try {
    const res = await adminApi.get(`/api/notifications/account/${id}/count`)
    unreadCount.value = res.data
  } catch (err) {
    // 뱃지 갱신 실패는 조용히 무시 (로그인 직후 세션 반영 지연 등)
    console.log('알림 개수 조회 실패:', err)
  }
  // 26.09.21 수정: 1초 폴링 중 패널이 열려있을 때마다 목록 전체를 다시 불러오면
  // loading 상태가 매초 토글되면서 화면이 깜빡였음(불러오는 중 ↔ 목록 반복).
  // 뱃지 숫자만 가볍게 갱신하고, 목록은 패널을 열 때 / 읽음 처리 직후에만 갱신하도록 되돌림.
}

async function fetchNotifications() {
  const id = accountId()
  if (!id) return
  loading.value = true
  error.value = ''
  try {
    const res = await adminApi.get(`/api/notifications/account/${id}`, { params: { page: 0, size: 5 } }) // 26.10.28 종 버튼은 최신 5건만 (나머지는 알림 내역에서)
    notifications.value = [].concat(res.data.content ?? res.data ?? []).slice(0, 5)
  } catch (err) {
    error.value = pickErrorMessage(err, '알림을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

// 26.09.21 추가: 읽음/모두읽음 처리 직후 실제 서버 상태를 다시 확인하기 위한 "조용한" 재조회.
// fetchNotifications()와 달리 loading을 건드리지 않아 깜빡임이 없다.
async function refreshNotificationsSilently() {
  const id = accountId()
  if (!id) return
  try {
    const res = await adminApi.get(`/api/notifications/account/${id}`, { params: { page: 0, size: 5 } }) // 26.10.28 종 버튼은 최신 5건만 (나머지는 알림 내역에서)
    notifications.value = [].concat(res.data.content ?? res.data ?? []).slice(0, 5)
  } catch (err) {
    console.log('알림 목록 재조회 실패:', err)
  }
}

function togglePanel() {
  panelOpen.value = !panelOpen.value
  if (panelOpen.value) { fetchNotifications(); fetchLive() }
}

function closePanel() {
  panelOpen.value = false
}

// 26.10.28 알림을 누르면 이동할 화면 규칙은 utils/notificationTarget.js 로 옮김 (알림 내역 화면과 같이 사용)

async function handleItemClick(n) {
  if (n.sf) {
    liveRead.value = true
    panelOpen.value = false
    if (authState.user?.userType === 'ADMIN') router.push('/sbot/hub')
    return
  }
  // 백엔드 응답 키가 isRead 가 아니라 read (Jackson boolean 직렬화 규칙)
  if (!n.read) {
    try {
      await adminApi.patch(`/api/notifications/${n.notificationId}/read`)
      n.read = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch (err) {
      console.log('읽음 처리 실패:', err)
    }
  }
  const target = resolveTarget(n)
  panelOpen.value = false
  if (target) router.push(target)
}

function openHistory() {
  panelOpen.value = false
  router.push(historyPath())
}

async function markAllRead() {
  const id = accountId()
  if (!id) return
  markingAll.value = true
  liveRead.value = true
  try {
    await adminApi.patch(`/api/notifications/account/${id}/read-all`)
    // 26.09.21 수정: 낙관적 업데이트만 믿지 않고, 서버에 실제로 반영됐는지 조용히 재확인
    await refreshNotificationsSilently()
    unreadCount.value = 0
  } catch (err) {
    alert(pickErrorMessage(err, '모두 읽음 처리에 실패했습니다.'))
  } finally {
    markingAll.value = false
  }
}

function formatTime(d) {
  if (!d) return ''
  // 26.10.23 수정: 서버가 시각을 epoch 초(예: 1792742008.12)로 내려주는데 밀리초로 읽어 1970년으로 보이던 문제
  //  숫자면 초 단위로 보고 1000을 곱한다 (문자열 ISO 시각은 그대로)
  const date = new Date(typeof d === 'number' && d < 1e12 ? d * 1000 : d)
  const diffMs = Date.now() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '방금 전'
  if (diffMin < 60) return `${diffMin}분 전`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour}시간 전`
  return date.toLocaleDateString('ko-KR')
}

// 바깥 영역 클릭 시 패널 닫기 (간단한 로컬 디렉티브)
const vClickOutside = {
  mounted(el, binding) {
    el.__clickOutsideHandler__ = (e) => {
      if (!el.contains(e.target)) binding.value(e)
    }
    document.addEventListener('click', el.__clickOutsideHandler__, true)
  },
  unmounted(el) {
    document.removeEventListener('click', el.__clickOutsideHandler__, true)
  },
}

onMounted(() => {
  fetchUnreadCount(); fetchLive()
  // 26.09.21 수정: 1분 → 5초 폴링으로 단축해서 새로고침 없이도 뱃지 숫자가 자동으로 올라가게 함
  // (백엔드에 WebSocket/SSE 같은 실시간 푸시가 없어서, 짧은 주기로 계속 물어보는 방식으로 "거의 실시간"을 구현)
  pollTimer = setInterval(() => { fetchUnreadCount(); fetchLive() }, 5000)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style src="@/components/CSS/notification.css"></style>
<style>
/* 26.10.28 알림 패널 아래 '전체 알림 내역 보기' */
.notif-all-btn { display: block; width: 100%; border: 0; border-top: 1px solid #e3e8ef; background: #f8fafc; color: #1d4ed8; font-size: 13px; font-weight: 700; padding: 10px 12px; cursor: pointer; text-align: center; }
.notif-all-btn:hover { background: #eef3fb; }
</style>