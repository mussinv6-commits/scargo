<template>
  <li class="nav-item notif-bell-wrap" v-click-outside="closePanel">
    <button class="notif-bell-btn" @click="togglePanel" aria-label="알림">
      <!-- 26.09.21 수정: assets/bell.png (프로젝트에 이미 저장된 종 이미지)로 교체 -->
      <img :src="bellIcon" alt="" class="notif-bell-icon" />
      <span v-if="unreadCount > 0" class="notif-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
    </button>

    <div v-if="panelOpen" class="notif-panel">
      <div class="notif-panel-header">
        <span>알림</span>
        <button
          class="notif-readall-btn"
          v-if="unreadCount > 0"
          :disabled="markingAll"
          @click="markAllRead"
        >
          {{ markingAll ? '처리 중...' : '모두 읽음' }}
        </button>
      </div>

      <div class="notif-list">
        <div v-if="loading" class="notif-empty">불러오는 중...</div>
        <div v-else-if="error" class="notif-empty">없음</div>
        <div v-else-if="notifications.length === 0" class="notif-empty">없음</div>
        <div
          v-for="n in notifications"
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
    </div>
  </li>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { authState } from '@/auth/authState.js'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'
// 26.09.21 추가: assets 폴더에 저장된 종 이미지(bell.png)를 아이콘으로 사용
import bellIcon from '@/assets/bell.png'

const panelOpen = ref(false)
const notifications = ref([])
const unreadCount = ref(0)
const loading = ref(false)
const markingAll = ref(false)
const error = ref('')

let pollTimer = null

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
    const res = await adminApi.get(`/api/notifications/account/${id}`, { params: { page: 0, size: 15 } })
    notifications.value = res.data.content ?? res.data
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
    const res = await adminApi.get(`/api/notifications/account/${id}`, { params: { page: 0, size: 15 } })
    notifications.value = res.data.content ?? res.data
  } catch (err) {
    console.log('알림 목록 재조회 실패:', err)
  }
}

function togglePanel() {
  panelOpen.value = !panelOpen.value
  if (panelOpen.value) fetchNotifications()
}

function closePanel() {
  panelOpen.value = false
}

async function handleItemClick(n) {
  if (n.read) return // 26.09.21 수정: 백엔드 응답 키가 isRead가 아니라 read임 (Jackson boolean 직렬화 규칙)
  try {
    await adminApi.patch(`/api/notifications/${n.notificationId}/read`)
    n.read = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch (err) {
    // 26.09.21 수정: 실패를 콘솔에만 조용히 남기지 않고 사용자에게도 알림
    alert(pickErrorMessage(err, '읽음 처리에 실패했습니다.'))
  }
}

async function markAllRead() {
  const id = accountId()
  if (!id) return
  markingAll.value = true
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
  const date = new Date(d)
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
  fetchUnreadCount()
  // 26.09.21 수정: 1분 → 5초 폴링으로 단축해서 새로고침 없이도 뱃지 숫자가 자동으로 올라가게 함
  // (백엔드에 WebSocket/SSE 같은 실시간 푸시가 없어서, 짧은 주기로 계속 물어보는 방식으로 "거의 실시간"을 구현)
  pollTimer = setInterval(fetchUnreadCount, 5000)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style src="@/components/CSS/notification.css"></style>