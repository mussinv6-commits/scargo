<template>
  <div class="notice-slide-pin">
    <div class="notice-slide">
      <div class="notice-content">
        <span class="notice-type">{{ currentNotice.type }}</span>
        <span
          class="notice-title"
          :class="{ 'is-clickable': !!currentNotice.seq }"
          @click="goDetail(currentNotice.seq)"
        >
          {{ currentNotice.title }}
        </span>
      </div>
      <div class="notice-date">{{ currentNotice.date }}</div>
    </div>

    <div v-if="modalNotice" class="notice-page" @click.self="closeModal">
      <div class="notice-popup">
        <div class="popup-header">
          <h1>{{ modalNotice.type }} 공지</h1>
          <button type="button" class="close-button" aria-label="닫기" @click="closeModal">&times;</button>
        </div>
        <div class="notice-list">
          <p class="notice-title" style="cursor:pointer;" @click="goDetail(modalNotice.seq)">{{ modalNotice.title }}</p>
          <p class="notice-date">{{ modalNotice.date }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { postsApi, NOTICE_CATEGORY, noticeTypeLabel, isAlertNotice, fmtDate } from '@/utils/postsApi.js'

const SEEN_KEY = 'scargo-notice-modal'
const router = useRouter()
const notices = ref([])
const modalNotice = ref(null)

const emptyNotice = { type: '안내', title: '등록된 공지사항이 없습니다.', date: '', seq: null }
const currentNotice = computed(() => notices.value[0] || emptyNotice)

function toSlideItem(post) {
  return {
    seq: post.postId,
    type: noticeTypeLabel(post),
    title: post.title,
    date: fmtDate(post.createdAt),
  }
}

async function loadLatest() {
  try {
    const { data } = await postsApi.get(`/category/${NOTICE_CATEGORY}`, { params: { page: 0, size: 20 } })
    const rows = data.content || []
    notices.value = rows.length ? [toSlideItem(rows[0])] : []

    const alertPost = rows.find(isAlertNotice)
    if (!alertPost) return
    const seen = sessionStorage.getItem(SEEN_KEY)
    if (seen === String(alertPost.postId)) return
    modalNotice.value = toSlideItem(alertPost)
  } catch (err) {
    console.error('최신 공지 조회 실패:', err)
    notices.value = []
  }
}

function closeModal() {
  const item = modalNotice.value
  if (item && item.seq) sessionStorage.setItem(SEEN_KEY, String(item.seq))
  modalNotice.value = null
}

function goDetail(seq) {
  if (!seq) return
  closeModal()
  router.push({ name: 'noticeDetail', params: { id: seq } })
}

onMounted(loadLatest)
</script>

<style scoped src="./noticeSlide.css"></style>
<style scoped src="./noticeList.css"></style>
