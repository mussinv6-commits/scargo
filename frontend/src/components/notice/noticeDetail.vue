<template>
  <div id="notice-detail">
    <div class="notice-detail">
      <div class="border-bottom pb-3 mb-4">
        <div class="mb-2">
          <span class="badge-type" :class="noticeTypeClass(notice)">{{ noticeTypeLabel(notice) }}</span>
        </div>
        <h3 class="fw-bold mb-3">{{ notice.title }}</h3>
        <div class="notice-meta text-muted small">
          <span>작성자 {{ author }}</span>
          <span>등록일 {{ formatDate(notice.createdAt) }}</span>
          <span v-if="notice.updatedAt">수정일 {{ formatDate(notice.updatedAt) }}</span>
          <span v-if="notice.viewCount !== undefined">조회수 {{ notice.viewCount }}</span>
        </div>
      </div>

      <div v-if="isHtml" class="content-body is-html py-4 mb-4" v-html="safeHtml"></div>
      <div v-else class="content-body py-4 mb-4">{{ notice.contentText }}</div>

      <div v-if="attachments.length" class="attachment-box border-top border-bottom py-3 mb-4">
        <h6 class="fw-bold mb-3">첨부파일</h6>
        <div v-for="file in attachments" :key="file.attachmentId" class="mb-2">
          <a href="#" @click.prevent="downloadAttachment(file)">📎 {{ file.originalName }}</a>
        </div>
      </div>

      <table class="table border-top border-bottom mb-4">
        <tbody>
          <tr>
            <th class="text-secondary" style="width:120px;">이전글</th>
            <td v-if="prevNext.prevId" class="cursor-pointer text-truncate" @click="moveDetail(prevNext.prevId)">{{ prevNext.prevTitle }}</td>
            <td v-else class="text-muted">이전글이 없습니다.</td>
          </tr>
          <tr>
            <th class="text-secondary">다음글</th>
            <td v-if="prevNext.nextId" class="cursor-pointer text-truncate" @click="moveDetail(prevNext.nextId)">{{ prevNext.nextTitle }}</td>
            <td v-else class="text-muted">다음글이 없습니다.</td>
          </tr>
        </tbody>
      </table>

      <div class="notice-actions">
        <button type="button" class="btn btn-outline-secondary" @click="goList">목록</button>
        <div v-if="canEdit">
          <button type="button" class="btn btn-outline-primary me-2" @click="goUpdate">수정</button>
          <button type="button" class="btn btn-outline-danger" @click="deleteNotice">삭제</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import axios from 'axios'
import { postsApi, NOTICE_CATEGORY, noticeTypeLabel, noticeTypeClass, authorName, canManagePost, currentUser, fmtDate } from '@/utils/postsApi.js'
import { API_BASE } from '@/utils/apiBase.js'
import { friendlyError } from '@/utils/apiHelpers.js'

export default {
  data() {
    return {
      postId: this.$route.params.id,
      notice: {},
      attachments: [],
      author: '관리자',
      prevNext: {},
    }
  },
  computed: {
    canEdit() { return canManagePost(this.notice) },
    isHtml() { return /<\/?[a-z][\s\S]*>/i.test(this.notice.contentText || '') },
    safeHtml() {
      const doc = new DOMParser().parseFromString(this.notice.contentText || '', 'text/html')
      doc.querySelectorAll('script,style,iframe,object').forEach((node) => node.remove())
      doc.querySelectorAll('*').forEach((el) => {
        [...el.attributes].forEach((attr) => {
          if (attr.name.startsWith('on') || attr.name === 'srcdoc') el.removeAttribute(attr.name)
          if (attr.name === 'href' && /^\s*javascript:/i.test(attr.value)) el.removeAttribute(attr.name)
          if (attr.name === 'src' && !/^(https?:|data:image\/(png|jpe?g|gif|webp);base64,)/i.test(attr.value.trim())) el.removeAttribute(attr.name)
        })
      })
      return doc.body.innerHTML
    },
  },
  watch: {
    '$route.params.id'(id) {
      if (!id) return
      this.postId = id
      this.load()
    },
  },
  mounted() { this.load() },
  methods: {
    noticeTypeLabel,
    noticeTypeClass,
    formatDate(d) { return fmtDate(d, true) },
    async load() {
      await this.getNoticeDetail()
      this.getPrevNextPosts()
    },
    async getNoticeDetail() {
      try {
        const { data } = await postsApi.get(`/${this.postId}`)
        this.notice = data || {}
        this.attachments = data.attachments || []
        this.author = await authorName(data.accountId)
      } catch (err) {
        console.error('공지사항 상세 조회 실패:', err)
        alert('공지사항을 불러오지 못했습니다.')
        this.goList()
      }
    },
    async getPrevNextPosts() {
      try {
        const { data } = await postsApi.get(`/category/${NOTICE_CATEGORY}`, { params: { page: 0, size: 1000, sort: 'postId,asc' } })
        const posts = data.content || []
        const currentId = Number(this.postId)
        const previousPost = posts.filter((p) => Number(p.postId) < currentId).sort((a, b) => Number(b.postId) - Number(a.postId))[0]
        const nextPost = posts.filter((p) => Number(p.postId) > currentId).sort((a, b) => Number(a.postId) - Number(b.postId))[0]
        this.prevNext = {
          prevId: previousPost?.postId || null,
          prevTitle: previousPost?.title || '',
          nextId: nextPost?.postId || null,
          nextTitle: nextPost?.title || '',
        }
      } catch {
        this.prevNext = {}
      }
    },
    moveDetail(postId) {
      if (!postId) return
      this.$router.push({ name: 'noticeDetail', params: { id: postId } })
    },
    goUpdate() {
      this.$router.push({ name: 'noticeUpdate', params: { id: this.postId } })
    },
    async deleteNotice() {
      if (!confirm('정말 삭제하시겠습니까?')) return
      const user = currentUser()
      if (!user?.accountId) {
        alert('로그인이 필요합니다.')
        return
      }
      try {
        await postsApi.delete(`/${this.postId}`, {
          params: { currentAccountId: user.accountId, isAdmin: user.userType === 'ADMIN' },
        })
        alert('삭제되었습니다.')
        this.goList()
      } catch (err) {
        alert(`공지사항 삭제에 실패했습니다.\n${friendlyError(err)}`)
      }
    },
    async downloadAttachment(file) {
      try {
        const { data } = await axios.get(
          `${API_BASE}/api/v1/posts/${this.postId}/attachments/${file.attachmentId}`,
          { responseType: 'blob', withCredentials: true }
        )
        const blobUrl = window.URL.createObjectURL(new Blob([data]))
        const link = document.createElement('a')
        link.href = blobUrl
        link.download = file.originalName || 'attachment'
        document.body.appendChild(link)
        link.click()
        link.remove()
        window.URL.revokeObjectURL(blobUrl)
      } catch (err) {
        console.error('첨부파일 다운로드 실패:', err)
        alert('첨부파일 다운로드에 실패했습니다.')
      }
    },
    goList() {
      this.$router.push({ name: 'notice' })
    },
  },
}
</script>

<style scoped src="./noticeDetail.css"></style>
<style scoped src="@/components/CSS/board.css"></style>
