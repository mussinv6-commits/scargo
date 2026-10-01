<template>
  <div id="notice-list">
    <div class="notice-list-head">
      <h2 class="notice-list-title">공지사항</h2>
      <p class="notice-list-sub">항만 운영과 관련된 안내를 확인하세요.</p>
    </div>

    <div class="notice-toolbar">
      <select v-model="searchType" class="form-select form-select-sm" style="width:110px;">
        <option value="title">제목</option>
        <option value="content">내용</option>
        <option value="category">구분</option>
      </select>
      <input
        v-if="searchType !== 'category'"
        v-model="keyword"
        class="form-control form-control-sm notice-search-input"
        placeholder="공지사항 검색"
        @keyup.enter="searchBtn"
      />
      <select v-else v-model="categoryKeyword" class="form-select form-select-sm notice-search-input">
        <option value="">전체</option>
        <option value="GENERAL">일반</option>
        <option value="IMPORTANT">중요</option>
        <option value="URGENT">긴급</option>
      </select>
      <button type="button" class="btn btn-primary btn-sm px-3" @click="searchBtn">검색</button>
    </div>

    <div v-if="isAdmin" class="notice-write-row">
      <router-link to="/notice/write" class="btn btn-outline-primary btn-sm notice-write-btn">글쓰기</router-link>
    </div>

    <table class="table notice-table">
      <thead>
        <tr>
          <th style="width:70px;">번호</th>
          <th style="width:90px;">구분</th>
          <th>제목</th>
          <th style="width:110px;">작성자</th>
          <th style="width:110px;">등록일</th>
          <th style="width:110px;">수정일</th>
          <th style="width:80px;">조회수</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(notice, index) in noticeList" :key="notice.postId" @click="noticeDetail(notice.postId)">
          <td class="text-muted">{{ displayNo(index) }}</td>
          <td>
            <span class="badge-type" :class="noticeTypeClass(notice)">{{ noticeTypeLabel(notice) }}</span>
          </td>
          <td>
            <div class="title-text">
              {{ notice.title }}
              <span v-if="hasAttachment(notice)" class="attach-icon" title="첨부파일 있음">📎</span>
            </div>
          </td>
          <td class="text-muted small">{{ notice.authorName || '관리자' }}</td>
          <td class="text-muted small">{{ formatDate(notice.createdAt) }}</td>
          <td class="text-muted small">{{ formatDate(notice.updatedAt) }}</td>
          <td class="text-muted small">{{ notice.viewCount }}</td>
        </tr>
        <tr v-if="noticeList.length === 0">
          <td colspan="7" class="py-5 text-muted">등록된 공지사항이 없습니다.</td>
        </tr>
      </tbody>
    </table>

    <nav class="notice-pager" aria-label="페이지 이동">
      <button type="button" class="pager-btn" :disabled="pageNumber <= 1" @click="goPage(pageNumber - 1)">이전</button>
      <button
        v-for="p in pageButtons"
        :key="p"
        type="button"
        class="pager-btn"
        :class="{ active: p === pageNumber }"
        @click="goPage(p)"
      >{{ p }}</button>
      <button type="button" class="pager-btn" :disabled="pageNumber >= totalPages" @click="goPage(pageNumber + 1)">다음</button>
    </nav>
  </div>
</template>

<script>
import { postsApi, NOTICE_CATEGORY, noticeTypeLabel, noticeTypeClass, isAdminUser, withAuthors, fmtDate, parseNoticeLevel } from '@/utils/postsApi.js'

const PAGE_SIZE = 5

export default {
  data() {
    return {
      noticeList: [],
      searchType: 'title',
      keyword: '',
      categoryKeyword: '',
      pageNumber: 1,
      cnt: 0,
    }
  },
  computed: {
    isAdmin() { return isAdminUser() },
    totalPages() { return Math.max(1, Math.ceil(this.cnt / PAGE_SIZE) || 1) },
    pageButtons() {
      const last = this.totalPages
      const cur = this.pageNumber
      const start = Math.max(1, cur - 2)
      const end = Math.min(last, start + 4)
      const from = Math.max(1, end - 4)
      const pages = []
      for (let i = from; i <= end; i++) pages.push(i)
      return pages.length ? pages : [1]
    },
  },
  mounted() {
    this.getNoticeList()
  },
  methods: {
    noticeTypeLabel,
    noticeTypeClass,
    displayNo(index) {
      return this.cnt - ((this.pageNumber - 1) * PAGE_SIZE) - index
    },
    hasAttachment(notice) {
      if (notice.attachments?.length) return true
      if (notice.hasAttachment === true) return true
      return Number(notice.attachmentCount) > 0
    },
    async getNoticeList() {
      try {
        if (this.searchType === 'category' && this.categoryKeyword) {
          const { data } = await postsApi.get(`/category/${NOTICE_CATEGORY}`, { params: { page: 0, size: 200 } })
          const all = this.filterByLevel(data.content || [])
          this.cnt = all.length
          const start = (this.pageNumber - 1) * PAGE_SIZE
          this.noticeList = await withAuthors(all.slice(start, start + PAGE_SIZE))
          return
        }
        const { data } = await postsApi.get(`/category/${NOTICE_CATEGORY}`, {
          params: { page: this.pageNumber - 1, size: PAGE_SIZE },
        })
        this.noticeList = await withAuthors(data.content || [])
        this.cnt = data.totalElements || 0
      } catch (err) {
        console.error('공지사항 목록 조회 실패:', err)
      }
    },
    filterByLevel(rows) {
      if (this.searchType !== 'category' || !this.categoryKeyword) return rows
      return rows.filter((p) => parseNoticeLevel(p) === this.categoryKeyword)
    },
    goPage(p) {
      if (p < 1 || p > this.totalPages) return
      this.pageNumber = p
      if (this.keyword.trim() && this.searchType !== 'category') this.searchBtn(true)
      else this.getNoticeList()
    },
    async searchBtn(keepPage = false) {
      if (!keepPage) this.pageNumber = 1
      if (this.searchType === 'category') {
        await this.getNoticeList()
        return
      }
      if (!this.keyword.trim()) {
        await this.getNoticeList()
        return
      }
      try {
        const { data } = await postsApi.get('/search', {
          params: {
            searchType: this.searchType,
            keyword: this.keyword,
            category: NOTICE_CATEGORY,
            page: this.pageNumber - 1,
            size: PAGE_SIZE,
          },
        })
        const rows = (data.content || []).filter((p) => p.category === NOTICE_CATEGORY)
        this.noticeList = await withAuthors(rows)
        this.cnt = data.totalElements || rows.length
      } catch (err) {
        console.error('공지사항 검색 실패:', err)
      }
    },
    noticeDetail(postId) {
      this.$router.push({ name: 'noticeDetail', params: { id: postId } })
    },
    formatDate(date) {
      return fmtDate(date)
    },
  },
}
</script>

<style scoped src="./noticeList.css"></style>
<style scoped src="@/components/CSS/board.css"></style>
