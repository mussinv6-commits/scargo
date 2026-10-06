<template>
  <div class="ad-page">
    <div class="ad-head">
      <div class="ad-head-title">
        <i class="bi bi-megaphone"></i>
        <div>
          <h1>공지사항 상세</h1>
          <p>공지사항 <span>›</span> 상세</p>
        </div>
      </div>
      <button type="button" class="ad-list" @click="goList">
        <i class="bi bi-list-ul"></i> 목록으로
      </button>
    </div>

    <article class="ad-card">
      <span class="ad-badge" :class="'is-' + level.toLowerCase()">{{ levelLabel }}</span>
      <h2>{{ notice.title }}</h2>
      <div class="ad-meta">
        <span><i class="bi bi-person"></i> {{ author }}</span>
        <span><i class="bi bi-calendar3"></i> {{ formatDate(notice.createdAt) }}</span>
        <span v-if="notice.updatedAt"><i class="bi bi-pencil"></i> 수정일 {{ formatDate(notice.updatedAt) }}</span>
        <span><i class="bi bi-eye"></i> 조회수 {{ notice.viewCount ?? 0 }}</span>
      </div>

      <div v-if="lead && !isHtml" class="ad-lead">
        <i class="bi bi-megaphone"></i>
        <p>{{ lead }}</p>
      </div>

      <div v-if="isHtml" class="ad-body ad-html" v-html="safeHtml"></div>
      <div v-else class="ad-body">{{ body }}</div>

      <section v-if="attachments.length" class="ad-files">
        <h3>첨부파일 <b>{{ attachments.length }}</b></h3>
        <div v-for="file in attachments" :key="file.attachmentId" class="ad-file">
          <i :class="fileIcon(file)"></i>
          <div>
            <strong>{{ file.originalName }}</strong>
            <span>{{ formatSize(file.fileSize) }}</span>
          </div>
          <button type="button" aria-label="다운로드" @click="downloadAttachment(file)">
            <i class="bi bi-download"></i>
          </button>
        </div>
      </section>
    </article>
  </div>
</template>

<script>
import axios from 'axios'
import { postsApi, parseNoticeLevel, authorName, fmtDate } from '@/utils/postsApi.js'

function sanitizeNoticeHtml(html) {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  doc.querySelectorAll('script,style,iframe,object').forEach((node) => node.remove())
  doc.querySelectorAll('*').forEach((el) => {
    [...el.attributes].forEach((attr) => {
      if (attr.name.startsWith('on') || attr.name === 'srcdoc') el.removeAttribute(attr.name)
      if (attr.name === 'href' && /^\s*javascript:/i.test(attr.value)) el.removeAttribute(attr.name)
      if (attr.name === 'src' && !/^(https?:|data:image\/(png|jpe?g|gif|webp);base64,)/i.test(attr.value.trim())) el.removeAttribute(attr.name)
    })
  })
  return doc.body.innerHTML
}
import { API_BASE } from '@/utils/apiBase.js'

export default {
  data() {
    return {
      postId: this.$route.params.id,
      notice: {},
      attachments: [],
      author: '관리자',
    }
  },
  computed: {
    level() { return parseNoticeLevel(this.notice) },
    isHtml() { return /<\/?[a-z][\s\S]*>/i.test(this.notice.contentText || '') },
    safeHtml() { return sanitizeNoticeHtml(this.notice.contentText || '') },
    levelLabel() {
      if (this.level === 'URGENT') return '필독'
      if (this.level === 'IMPORTANT') return '중요'
      return '일반'
    },
    paragraphs() {
      return String(this.notice.contentText || '').split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean)
    },
    lead() {
      if (this.level === 'GENERAL' || this.paragraphs.length < 2) return ''
      return this.paragraphs[0]
    },
    body() {
      if (!this.lead) return this.notice.contentText || ''
      return this.paragraphs.slice(1).join('\n\n')
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
    formatDate(d) { return fmtDate(d, true) },
    async load() {
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
    fileIcon(file) {
      const name = String(file.originalName || '').toLowerCase()
      if (name.endsWith('.pdf')) return 'bi bi-file-earmark-pdf'
      if (/\.(png|jpe?g|gif|webp)$/.test(name)) return 'bi bi-file-earmark-image'
      return 'bi bi-file-earmark'
    },
    formatSize(size) {
      const n = Number(size)
      if (!Number.isFinite(n) || n < 0) return ''
      if (n < 1024) return `${n}B`
      if (n < 1024 * 1024) return `${Math.max(1, Math.round(n / 1024))}KB`
      const mb = n / (1024 * 1024)
      return `${mb >= 10 ? Math.round(mb) : mb.toFixed(1)}MB`
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
      this.$router.push({ name: 'admin-notices' })
    },
  },
}
</script>

<style scoped>
.ad-page { padding-top: 8px; }
.ad-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.ad-head-title { display: flex; align-items: center; gap: 12px; }
.ad-head-title > i {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: #fff3ea;
  color: #ff6b00;
  font-size: 20px;
}
.ad-head h1 { margin: 0; font-size: 22px; font-weight: 800; color: #0a2540; }
.ad-head p { margin: 2px 0 0; font-size: 13px; color: #8aa0b8; }
.ad-head p span { margin: 0 4px; }
.ad-list {
  height: 40px;
  padding: 0 14px;
  border: 1px solid #e4e9f0;
  border-radius: 10px;
  background: #fff;
  color: #334e68;
  font-weight: 800;
  cursor: pointer;
}
.ad-card {
  background: #fff;
  border: 1px solid #e8edf4;
  border-radius: 18px;
  box-shadow: 0 10px 30px rgba(10, 37, 64, 0.05);
  padding: 28px 32px 32px;
}
.ad-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}
.ad-badge.is-general { background: #eef2f6; color: #5d7086; }
.ad-badge.is-important, .ad-badge.is-urgent { background: #ffe4e6; color: #e11d48; }
.ad-card h2 { margin: 14px 0 12px; font-size: 26px; font-weight: 800; color: #0a2540; }
.ad-meta { display: flex; flex-wrap: wrap; gap: 8px 18px; color: #7b8da3; font-size: 13.5px; }
.ad-meta i { margin-right: 4px; }
.ad-lead {
  display: flex;
  gap: 12px;
  margin-top: 22px;
  padding: 16px 18px;
  border-radius: 14px;
  background: #fff5f5;
  color: #9f1239;
}
.ad-lead i { font-size: 20px; margin-top: 2px; }
.ad-lead p { margin: 0; font-weight: 700; line-height: 1.6; white-space: pre-wrap; }
.ad-body {
  margin-top: 22px;
  color: #243b53;
  font-size: 15px;
  line-height: 1.75;
  white-space: pre-wrap;
}
.ad-html { white-space: normal; }
.ad-html :deep(p) { margin: 0 0 10px; }
.ad-html :deep(ul), .ad-html :deep(ol) { margin: 0 0 10px; padding-left: 1.2em; }
.ad-html :deep(img) { max-width: 100%; height: auto; display: block; margin: 12px 0; border-radius: 8px; }
.ad-html :deep(table) { width: 100%; border-collapse: collapse; margin: 12px 0; }
.ad-html :deep(th), .ad-html :deep(td) { border: 1px solid #d5deea; padding: 8px 10px; vertical-align: top; }
.ad-html :deep(th) { background: #f4f7fb; }
.ad-files { margin-top: 28px; padding-top: 18px; border-top: 1px solid #eef2f6; }
.ad-files h3 { margin: 0 0 12px; font-size: 15px; font-weight: 800; color: #0a2540; }
.ad-files h3 b {
  display: inline-grid;
  place-items: center;
  min-width: 20px;
  height: 20px;
  margin-left: 4px;
  padding: 0 6px;
  border-radius: 999px;
  background: #eef2f6;
  font-size: 12px;
}
.ad-file {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #e8edf4;
  border-radius: 12px;
  margin-bottom: 8px;
}
.ad-file > i { font-size: 22px; color: #ef4444; }
.ad-file div { flex: 1; min-width: 0; }
.ad-file strong { display: block; color: #0a2540; font-size: 14px; }
.ad-file span { color: #8aa0b8; font-size: 12.5px; }
.ad-file button {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 10px;
  background: #f4f7fb;
  color: #334e68;
  cursor: pointer;
}
</style>
