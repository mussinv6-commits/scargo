<template>
  <!-- 26.09.30 추가: 게시판/공지사항 공용 작성·수정 폼 (백엔드 /api/v1/posts, multipart) -->
  <div class="board-page">
    <div class="board-head">
      <div>
        <h2 class="board-title">{{ heading }}</h2>
        <p class="board-sub">작성자: {{ userName }}</p>
      </div>
    </div>

    <div v-if="loading" class="board-form text-center text-muted">불러오는 중...</div>

    <form v-else class="board-form" novalidate @submit.prevent="submit">
      <div class="mb-3">
        <label class="form-label" for="pe-title">제목</label>
        <input id="pe-title" v-model="title" type="text" class="form-control" placeholder="제목을 입력하세요." maxlength="200" />
      </div>

      <div v-if="allowPin" class="mb-3">
        <label class="form-label" for="pe-level">공지 구분</label>
        <select id="pe-level" v-model="noticeLevel" class="form-select">
          <option value="GENERAL">일반</option>
          <option value="IMPORTANT">중요</option>
          <option value="URGENT">긴급</option>
        </select>
        <p class="form-text mb-0">중요·긴급 공지는 홈 화면에서 모달로 안내됩니다.</p>
      </div>

      <div class="mb-3">
        <label class="form-label" for="pe-content">내용</label>
        <textarea id="pe-content" v-model="content" class="form-control" rows="12" placeholder="내용을 입력하세요."></textarea>
      </div>

      <div v-if="existingFiles.length" class="mb-3">
        <label class="form-label">기존 첨부파일</label>
        <ul class="board-file-list">
          <li v-for="f in existingFiles" :key="f.attachmentId" :style="deleteFileIds.includes(f.attachmentId) ? 'opacity:.5; text-decoration:line-through;' : ''">
            <span><i class="bi bi-paperclip"></i> {{ f.originalName }}</span>
            <button type="button" class="btn btn-sm btn-link p-0" :class="deleteFileIds.includes(f.attachmentId) ? 'text-secondary' : 'text-danger'" @click="toggleDelete(f.attachmentId)">
              {{ deleteFileIds.includes(f.attachmentId) ? '삭제 취소' : '삭제' }}
            </button>
          </li>
        </ul>
      </div>

      <div class="mb-2">
        <label class="form-label" for="pe-files">{{ isEdit ? '파일 추가' : '첨부파일' }}</label>
        <input id="pe-files" type="file" class="form-control" multiple @change="fileChange" />
        <ul v-if="files.length" class="board-file-list">
          <li v-for="(file, index) in files" :key="index">
            {{ file.name }}
            <button type="button" class="btn btn-sm btn-link text-danger p-0" @click="files.splice(index, 1)">삭제</button>
          </li>
        </ul>
      </div>

      <p v-if="errorMsg" class="text-danger small mb-0" style="white-space: pre-line;">{{ errorMsg }}</p>

      <div class="board-form-actions">
        <button type="button" class="btn btn-outline-secondary" @click="cancel">취소</button>
        <button type="submit" class="btn btn-primary" :disabled="saving">
          {{ saving ? '저장 중...' : isEdit ? '수정 완료' : '등록' }}
        </button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { postsApi, currentUser, isAdminUser, canManagePost, parseNoticeLevel } from '@/utils/postsApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'

const props = defineProps({
  category: { type: String, required: true }, // NOTICE | FREE
  postId: { type: [String, Number], default: null },
  label: { type: String, default: '게시글' },
  listRoute: { type: Object, required: true },
  detailRoute: { type: Function, required: true }, // (postId) => route
})

const router = useRouter()
const isEdit = computed(() => !!props.postId)
const heading = computed(() => `${props.label} ${isEdit.value ? '수정' : '작성'}`)
const userName = computed(() => currentUser()?.userName || '-')
const allowPin = computed(() => props.category === 'NOTICE' && isAdminUser())

const title = ref('')
const content = ref('')
const noticeLevel = ref('GENERAL')
const isPinned = computed(() => noticeLevel.value !== 'GENERAL')
const files = ref([])
const existingFiles = ref([])
const deleteFileIds = ref([])
const loading = ref(false)
const saving = ref(false)
const errorMsg = ref('')

function fileChange(e) {
  files.value = Array.from(e.target.files || [])
}
function toggleDelete(id) {
  const i = deleteFileIds.value.indexOf(id)
  if (i >= 0) deleteFileIds.value.splice(i, 1)
  else deleteFileIds.value.push(id)
}

onMounted(async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const { data } = await postsApi.get(`/${props.postId}`)
    if (!canManagePost(data)) {
      alert('본인이 작성한 글만 수정할 수 있습니다.')
      router.replace(props.detailRoute(props.postId))
      return
    }
    title.value = data.title || ''
    content.value = data.contentText || ''
    noticeLevel.value = parseNoticeLevel(data)
    existingFiles.value = data.attachments || []
  } catch (err) {
    alert(`글을 불러오지 못했습니다.\n${friendlyError(err)}`)
    router.replace(props.listRoute)
  } finally {
    loading.value = false
  }
})

async function submit() {
  errorMsg.value = ''
  const user = currentUser()
  if (!user?.accountId) return (errorMsg.value = '로그인 정보가 없습니다. 다시 로그인해주세요.')
  if (!title.value.trim()) return (errorMsg.value = '제목을 입력해주세요.')
  if (!content.value.trim()) return (errorMsg.value = '내용을 입력해주세요.')

  const fd = new FormData()
  fd.append('title', title.value.trim())
  fd.append('contentText', content.value)
  fd.append('category', props.category)
  if (allowPin.value) {
    fd.append('isPinned', isPinned.value)
    fd.append('noticeLevel', noticeLevel.value)
  }

  saving.value = true
  try {
    let postId = props.postId
    if (isEdit.value) {
      fd.append('currentAccountId', user.accountId)
      fd.append('isAdmin', user.userType === 'ADMIN')
      deleteFileIds.value.forEach((id) => fd.append('deleteFileIds', id))
      files.value.forEach((f) => fd.append('newFiles', f))
      await postsApi.put(`/${postId}`, fd)
    } else {
      // 26.09.30 수정: 작성자 accountId 를 1 로 고정하던 문제 → 로그인 사용자
      fd.append('accountId', user.accountId)
      files.value.forEach((f) => fd.append('files', f))
      const { data } = await postsApi.post('', fd)
      postId = data?.postId
    }
    alert(isEdit.value ? '수정되었습니다.' : '등록되었습니다.')
    router.push(postId ? props.detailRoute(postId) : props.listRoute)
  } catch (err) {
    errorMsg.value = `${props.label} ${isEdit.value ? '수정' : '등록'}에 실패했습니다.\n${friendlyError(err)}`
  } finally {
    saving.value = false
  }
}

function cancel() {
  router.push(isEdit.value ? props.detailRoute(props.postId) : props.listRoute)
}
</script>

<style scoped src="@/components/CSS/board.css"></style>
