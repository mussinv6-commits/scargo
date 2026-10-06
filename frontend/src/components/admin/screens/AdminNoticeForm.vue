<template>
  <div class="nw-page">
    <div class="nw-head">
      <i class="bi bi-megaphone"></i>
      <div>
        <h1>공지사항 {{ isEdit ? '수정' : '작성' }}</h1>
        <p>공지사항 <span>›</span> {{ isEdit ? '수정' : '작성' }}</p>
      </div>
    </div>

    <div v-if="loading" class="nw-card nw-loading">불러오는 중...</div>

    <form v-else class="nw-card" novalidate @submit.prevent="submit">
      <div class="nw-field">
        <div class="nw-label-row">
          <label for="nw-title">제목 <em>*</em></label>
          <span>{{ title.length }}/100</span>
        </div>
        <input id="nw-title" v-model="title" type="text" maxlength="100" placeholder="제목을 입력하세요." />
      </div>

      <div class="nw-field">
        <div class="nw-label-row">
          <span>공지 구분 <em>*</em></span>
        </div>
        <div class="nw-levels">
          <button
            v-for="item in levels"
            :key="item.value"
            type="button"
            class="nw-level"
            :class="['is-' + item.value.toLowerCase(), { on: noticeLevel === item.value }]"
            @click="noticeLevel = item.value"
          >
            <i :class="item.icon"></i>
            <strong>{{ item.label }}</strong>
            <small>{{ item.desc }}</small>
          </button>
        </div>
      </div>

      <div class="nw-field">
        <div class="nw-label-row">
          <span>내용 <em>*</em></span>
          <span>{{ contentLength }}/2000</span>
        </div>
        <div class="nw-editor-box">
          <div class="nw-toolbar">
            <select aria-label="본문 스타일" @change="formatBlock($event.target.value); $event.target.value = 'p'">
              <option value="p">본문</option>
              <option value="h3">제목</option>
            </select>
            <button type="button" aria-label="굵게" @mousedown.prevent @click="cmd('bold')"><b>B</b></button>
            <button type="button" aria-label="기울임" @mousedown.prevent @click="cmd('italic')"><i>I</i></button>
            <button type="button" aria-label="밑줄" @mousedown.prevent @click="cmd('underline')"><u>U</u></button>
            <button type="button" aria-label="글머리 기호" @mousedown.prevent @click="cmd('insertUnorderedList')"><i class="bi bi-list-ul"></i></button>
            <button type="button" aria-label="번호 매기기" @mousedown.prevent @click="cmd('insertOrderedList')"><i class="bi bi-list-ol"></i></button>
            <button type="button" aria-label="링크" @mousedown.prevent @click="addLink"><i class="bi bi-link-45deg"></i></button>
            <button type="button" aria-label="본문에 이미지" @mousedown.prevent @click="pickImage"><i class="bi bi-image"></i></button>
            <button type="button" aria-label="표 넣기" @mousedown.prevent @click="addTable"><i class="bi bi-table"></i></button>
            <button type="button" aria-label="행 추가" @mousedown.prevent @click="addTableRow">행+</button>
            <button type="button" aria-label="열 추가" @mousedown.prevent @click="addTableCol">열+</button>
          </div>
          <div
            ref="editorRef"
            class="nw-editor"
            contenteditable="true"
            role="textbox"
            aria-multiline="true"
            data-placeholder="내용을 입력하세요."
            @input="syncContent"
          ></div>
        </div>
      </div>

      <div class="nw-field">
        <div class="nw-label-row">
          <span>첨부파일</span>
        </div>
        <ul v-if="existingFiles.length" class="nw-files">
          <li v-for="f in existingFiles" :key="f.attachmentId" :class="{ off: deleteFileIds.includes(f.attachmentId) }">
            <span>{{ f.originalName }}</span>
            <button type="button" @click="toggleDelete(f.attachmentId)">
              {{ deleteFileIds.includes(f.attachmentId) ? '삭제 취소' : '삭제' }}
            </button>
          </li>
        </ul>
        <div
          class="nw-drop"
          :class="{ over: dragOver }"
          @click="pickFiles"
          @dragover.prevent="dragOver = true"
          @dragleave.prevent="dragOver = false"
          @drop.prevent="onDrop"
        >
          <i class="bi bi-cloud-arrow-up"></i>
          <p>파일을 드래그하거나 클릭하여 업로드하세요.</p>
          <small>(최대 10MB, 여러 파일 업로드 가능)</small>
        </div>
        <input ref="fileRef" type="file" multiple hidden @change="onPick" />
        <input ref="imageRef" type="file" accept="image/*" hidden @change="onPickImage" />
        <ul v-if="files.length" class="nw-files">
          <li v-for="(file, index) in files" :key="file.name + index">
            <span>{{ file.name }}</span>
            <button type="button" @click="files.splice(index, 1)">삭제</button>
          </li>
        </ul>
      </div>

      <p v-if="errorMsg" class="nw-error">{{ errorMsg }}</p>

      <div class="nw-actions">
        <button type="button" class="nw-cancel" @click="cancel">취소</button>
        <button type="submit" class="nw-submit" :disabled="saving">
          <i class="bi bi-send"></i> {{ saving ? '저장 중...' : isEdit ? '수정하기' : '등록하기' }}
        </button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { postsApi, currentUser, canManagePost, parseNoticeLevel } from '@/utils/postsApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'

const MAX_FILE = 10 * 1024 * 1024
const levels = [
  { value: 'GENERAL', label: '일반', desc: '일반 안내 사항', icon: 'bi bi-info-circle' },
  { value: 'IMPORTANT', label: '중요', desc: '중요한 안내 사항', icon: 'bi bi-exclamation-circle' },
  { value: 'URGENT', label: '필독', desc: '반드시 확인해야 할 사항', icon: 'bi bi-exclamation-lg' },
]

const route = useRoute()
const router = useRouter()
const postId = computed(() => (route.name === 'admin-notice-update' ? route.params.id : null))
const isEdit = computed(() => !!postId.value)
const listRoute = { name: 'admin-notices' }
function detailRoute(id) {
  return { name: 'admin-notice-detail', params: { id } }
}

const title = ref('')
const content = ref('')
const noticeLevel = ref('GENERAL')
const files = ref([])
const existingFiles = ref([])
const deleteFileIds = ref([])
const loading = ref(false)
const saving = ref(false)
const errorMsg = ref('')
const dragOver = ref(false)
const editorRef = ref(null)
const fileRef = ref(null)
const imageRef = ref(null)
let savedRange = null

const contentLength = computed(() => content.value.replace(/\u00a0/g, ' ').trim().length)
const isPinned = computed(() => noticeLevel.value !== 'GENERAL')

function syncContent() {
  content.value = editorRef.value?.innerText || ''
}
function hasBody() {
  const text = (editorRef.value?.innerText || '').replace(/\u00a0/g, ' ').trim()
  return !!text || !!editorRef.value?.querySelector('img, table')
}
function saveRange() {
  const sel = window.getSelection()
  if (!sel?.rangeCount || !editorRef.value) return
  const range = sel.getRangeAt(0)
  if (editorRef.value.contains(range.commonAncestorContainer)) savedRange = range.cloneRange()
}
function restoreRange() {
  editorRef.value?.focus()
  if (!savedRange) return
  const sel = window.getSelection()
  sel.removeAllRanges()
  sel.addRange(savedRange)
}
function cmd(command, value = null) {
  editorRef.value?.focus()
  document.execCommand(command, false, value)
  savedRange = null
  syncContent()
}
function insertHtml(html) {
  restoreRange()
  savedRange = null
  const ok = document.execCommand('insertHTML', false, html)
  if (!ok && editorRef.value) editorRef.value.insertAdjacentHTML('beforeend', html)
  syncContent()
}
function pickImage() {
  saveRange()
  imageRef.value?.click()
}
function readImage(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(new Error('read'))
    reader.onload = () => {
      const img = new Image()
      img.onerror = () => reject(new Error('image'))
      img.onload = () => {
        const maxW = 1200
        const scale = Math.min(1, maxW / (img.width || maxW))
        const w = Math.max(1, Math.round(img.width * scale))
        const h = Math.max(1, Math.round(img.height * scale))
        const canvas = document.createElement('canvas')
        canvas.width = w
        canvas.height = h
        const ctx = canvas.getContext('2d')
        ctx.fillStyle = '#fff'
        ctx.fillRect(0, 0, w, h)
        ctx.drawImage(img, 0, 0, w, h)
        resolve(canvas.toDataURL('image/jpeg', 0.85))
      }
      img.src = reader.result
    }
    reader.readAsDataURL(file)
  })
}
async function onPickImage(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  errorMsg.value = ''
  if (!file.type.startsWith('image/')) {
    errorMsg.value = '이미지 파일만 본문에 넣을 수 있습니다.'
    return
  }
  if (file.size > MAX_FILE) {
    errorMsg.value = '이미지는 10MB 이하만 넣을 수 있습니다.'
    return
  }
  try {
    const src = await readImage(file)
    insertHtml(`<img src="${src}" alt="">`)
  } catch {
    errorMsg.value = '이미지를 본문에 넣지 못했습니다.'
  }
}
function askCount(message, fallback, max) {
  const text = window.prompt(message, String(fallback))
  if (text == null) return null
  const n = Number(text)
  if (!Number.isInteger(n) || n < 1 || n > max) return 0
  return n
}
function addTable() {
  saveRange()
  const rows = askCount('행 수를 입력하세요. (1~10)', 3, 10)
  if (rows == null) return
  if (!rows) {
    errorMsg.value = '행 수는 1부터 10까지 입력할 수 있습니다.'
    return
  }
  const cols = askCount('열 수를 입력하세요. (1~8)', 3, 8)
  if (cols == null) return
  if (!cols) {
    errorMsg.value = '열 수는 1부터 8까지 입력할 수 있습니다.'
    return
  }
  errorMsg.value = ''
  const cell = (tag) => `<${tag}><br></${tag}>`
  const head = `<tr>${Array.from({ length: cols }, () => cell(rows > 1 ? 'th' : 'td')).join('')}</tr>`
  let body = ''
  for (let r = 1; r < rows; r += 1) {
    body += `<tr>${Array.from({ length: cols }, () => cell('td')).join('')}</tr>`
  }
  const table = rows > 1
    ? `<table><thead>${head}</thead><tbody>${body}</tbody></table><p><br></p>`
    : `<table><tbody>${head}</tbody></table><p><br></p>`
  insertHtml(table)
}
function tableRow() {
  const sel = window.getSelection()
  const node = sel?.anchorNode
  const el = node?.nodeType === 1 ? node : node?.parentElement
  return el?.closest?.('tr') || null
}
function addTableRow() {
  const row = tableRow()
  if (!row || !editorRef.value?.contains(row)) {
    errorMsg.value = '표를 클릭한 뒤 행을 추가하세요.'
    return
  }
  errorMsg.value = ''
  const tr = document.createElement('tr')
  for (let i = 0; i < row.cells.length; i += 1) {
    const td = document.createElement('td')
    td.innerHTML = '<br>'
    tr.appendChild(td)
  }
  row.after(tr)
  syncContent()
}
function addTableCol() {
  const row = tableRow()
  const table = row?.closest('table')
  if (!table || !editorRef.value?.contains(table)) {
    errorMsg.value = '표를 클릭한 뒤 열을 추가하세요.'
    return
  }
  errorMsg.value = ''
  table.querySelectorAll('tr').forEach((tr) => {
    const tag = tr.parentElement?.tagName === 'THEAD' ? 'th' : 'td'
    const cell = document.createElement(tag)
    cell.innerHTML = '<br>'
    tr.appendChild(cell)
  })
  syncContent()
}
function formatBlock(tag) {
  cmd('formatBlock', tag)
}
function addLink() {
  saveRange()
  const url = window.prompt('링크 주소를 입력하세요.')
  if (!url) return
  restoreRange()
  savedRange = null
  document.execCommand('createLink', false, url)
  syncContent()
}
function pickFiles() {
  fileRef.value?.click()
}
function addFiles(list) {
  const next = [...files.value]
  for (const file of list) {
    if (file.size > MAX_FILE) {
      errorMsg.value = `${file.name} 파일은 10MB를 넘습니다.`
      continue
    }
    next.push(file)
  }
  files.value = next
}
function onPick(e) {
  addFiles(Array.from(e.target.files || []))
  e.target.value = ''
}
function onDrop(e) {
  dragOver.value = false
  addFiles(Array.from(e.dataTransfer?.files || []))
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
    const { data } = await postsApi.get(`/${postId.value}`)
    if (!canManagePost(data)) {
      alert('본인이 작성한 글만 수정할 수 있습니다.')
      router.replace(detailRoute(postId.value))
      return
    }
    title.value = data.title || ''
    content.value = data.contentText || ''
    noticeLevel.value = parseNoticeLevel(data)
    existingFiles.value = data.attachments || []
  } catch (err) {
    alert(`글을 불러오지 못했습니다.\n${friendlyError(err)}`)
    router.replace(listRoute)
  } finally {
    loading.value = false
    await nextTick()
    if (!editorRef.value) return
    const text = content.value
    editorRef.value.innerHTML = /<\/?[a-z][\s\S]*>/i.test(text) ? text : ''
    if (!editorRef.value.innerHTML) editorRef.value.innerText = text
    syncContent()
  }
})

async function submit() {
  errorMsg.value = ''
  syncContent()
  const user = currentUser()
  if (!user?.accountId) return (errorMsg.value = '로그인 정보가 없습니다. 다시 로그인해주세요.')
  if (!title.value.trim()) return (errorMsg.value = '제목을 입력해주세요.')
  if (!hasBody()) return (errorMsg.value = '내용을 입력해주세요.')
  if (contentLength.value > 2000) return (errorMsg.value = '내용은 2000자까지 입력할 수 있습니다.')

  const html = editorRef.value?.innerHTML || content.value
  const fd = new FormData()
  fd.append('title', title.value.trim())
  fd.append('contentText', html)
  fd.append('category', 'NOTICE')
  fd.append('isPinned', isPinned.value)
  fd.append('noticeLevel', noticeLevel.value)

  saving.value = true
  try {
    let savedId = postId.value
    if (isEdit.value) {
      fd.append('currentAccountId', user.accountId)
      fd.append('isAdmin', user.userType === 'ADMIN')
      deleteFileIds.value.forEach((id) => fd.append('deleteFileIds', id))
      files.value.forEach((f) => fd.append('newFiles', f))
      await postsApi.put(`/${savedId}`, fd)
    } else {
      fd.append('accountId', user.accountId)
      files.value.forEach((f) => fd.append('files', f))
      const { data } = await postsApi.post('', fd)
      savedId = data?.postId
    }
    alert(isEdit.value ? '수정되었습니다.' : '등록되었습니다.')
    router.push(savedId ? detailRoute(savedId) : listRoute)
  } catch (err) {
    errorMsg.value = `공지사항 ${isEdit.value ? '수정' : '등록'}에 실패했습니다.\n${friendlyError(err)}`
  } finally {
    saving.value = false
  }
}

function cancel() {
  router.push(isEdit.value ? detailRoute(postId.value) : listRoute)
}
</script>

<style scoped>
.nw-page { padding-top: 8px; }
.nw-head { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.nw-head > i {
  width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center;
  background: #fff3ea; color: #ff6b00; font-size: 20px;
}
.nw-head h1 { margin: 0; font-size: 22px; font-weight: 800; color: #0a2540; }
.nw-head p { margin: 2px 0 0; font-size: 13px; color: #8aa0b8; }
.nw-head p span { margin: 0 4px; }
.nw-card {
  background: #fff; border: 1px solid #e8edf4; border-radius: 18px;
  box-shadow: 0 10px 30px rgba(10, 37, 64, 0.05); padding: 28px 32px 24px;
}
.nw-loading { color: #8aa0b8; text-align: center; }
.nw-field { margin-bottom: 22px; }
.nw-label-row { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px; font-weight: 800; color: #0a2540; }
.nw-label-row span:last-child, .nw-label-row em { font-weight: 700; }
.nw-label-row span:last-child { color: #8aa0b8; font-size: 12.5px; }
.nw-label-row em { color: #e11d48; font-style: normal; }
.nw-field > input {
  width: 100%; height: 44px; border: 1px solid #e4e9f0; border-radius: 10px;
  padding: 0 14px; font-size: 14px; color: #0a2540;
}
.nw-levels { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.nw-level {
  display: flex; flex-direction: column; align-items: flex-start; gap: 1px;
  padding: 8px 12px; border: 1px solid #e4e9f0; border-radius: 10px; background: #fff;
  text-align: left; cursor: pointer;
}
.nw-level i { font-size: 15px; color: #8aa0b8; }
.nw-level strong { color: #0a2540; font-size: 13.5px; }
.nw-level small { color: #8aa0b8; font-size: 11.5px; }
.nw-level.on.is-general { border-color: #3b82f6; background: #f3f8ff; }
.nw-level.on.is-general i { color: #3b82f6; }
.nw-level.on.is-important { border-color: #ff6b00; background: #fff7f0; }
.nw-level.on.is-important i { color: #ff6b00; }
.nw-level.on.is-urgent { border-color: #e11d48; background: #fff5f6; }
.nw-level.on.is-urgent i { color: #e11d48; }
.nw-editor-box { border: 1px solid #e4e9f0; border-radius: 12px; overflow: hidden; }
.nw-toolbar {
  display: flex; align-items: center; gap: 4px; padding: 8px 10px;
  border-bottom: 1px solid #eef2f6; background: #fafbfd;
}
.nw-toolbar select, .nw-toolbar button {
  height: 32px; border: 0; background: transparent; border-radius: 8px;
  color: #334e68; cursor: pointer; padding: 0 8px;
}
.nw-toolbar button:hover, .nw-toolbar select:hover { background: #eef2f6; }
.nw-editor {
  min-height: 340px; padding: 14px 16px; outline: none; color: #243b53;
  font-size: 14.5px; line-height: 1.7;
}
.nw-editor:empty:before { content: attr(data-placeholder); color: #a0b0c2; }
.nw-editor :deep(img) { max-width: 100%; height: auto; display: block; margin: 8px 0; border-radius: 8px; }
.nw-editor :deep(table) { width: 100%; border-collapse: collapse; margin: 10px 0; }
.nw-editor :deep(th), .nw-editor :deep(td) {
  border: 1px solid #d5deea; padding: 8px 10px; min-width: 48px; vertical-align: top;
}
.nw-editor :deep(th) { background: #f4f7fb; font-weight: 700; }
.nw-drop {
  border: 1.5px dashed #d5deea; border-radius: 10px; background: #fbfcfe;
  padding: 12px 14px; text-align: center; cursor: pointer; color: #7b8da3;
}
.nw-drop.over { border-color: #3b82f6; background: #f3f8ff; }
.nw-drop i { font-size: 18px; color: #60a5fa; }
.nw-drop p { margin: 4px 0 0; font-size: 13px; font-weight: 700; color: #334e68; }
.nw-drop small { color: #8aa0b8; }
.nw-files { list-style: none; padding: 0; margin: 0 0 10px; }
.nw-files li {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; background: #f4f7fb; border-radius: 10px; margin-bottom: 6px; font-size: 13.5px;
}
.nw-files li.off { opacity: 0.5; text-decoration: line-through; }
.nw-files button { border: 0; background: transparent; color: #e11d48; font-weight: 700; cursor: pointer; }
.nw-error { white-space: pre-line; color: #e11d48; font-size: 13px; font-weight: 700; }
.nw-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.nw-cancel, .nw-submit {
  height: 34px; padding: 0 14px; border-radius: 8px; font-size: 13px; font-weight: 800; cursor: pointer;
}
.nw-cancel { border: 1px solid #e4e9f0; background: #fff; color: #334e68; }
.nw-submit { border: 0; background: #0a2540; color: #fff; }
.nw-submit:disabled { opacity: 0.6; cursor: default; }
@media (max-width: 800px) {
  .nw-levels { grid-template-columns: 1fr; }
  .nw-card { padding: 20px 16px; }
}
</style>
