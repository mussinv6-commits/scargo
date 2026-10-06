<template>
  <div class="an-page">
    <div class="an-head">
      <div>
        <h1>공지사항</h1>
        <p>항만 운영과 관련된 안내를 확인하세요.</p>
      </div>
    </div>

    <section class="an-card">
      <div class="an-toolbar">
        <div class="an-search">
          <select v-model="searchType" class="an-select" aria-label="검색 기준">
            <option value="title">제목</option>
            <option value="content">내용</option>
          </select>
          <input
            v-model="keyword"
            type="search"
            placeholder="공지사항을 검색하세요."
            @keyup.enter="search"
          />
          <button type="button" class="an-search-btn" @click="search">
            <i class="bi bi-search"></i> 검색
          </button>
        </div>
        <button type="button" class="an-write" @click="goWrite">
          <i class="bi bi-pencil-square"></i> 글쓰기
        </button>
      </div>

      <div class="an-chips" role="tablist">
        <button
          v-for="chip in chips"
          :key="chip.key || 'all'"
          type="button"
          role="tab"
          :class="{ on: level === chip.key }"
          :aria-selected="level === chip.key"
          @click="setLevel(chip.key)"
        >
          {{ chip.label }} <b>{{ chip.count }}</b>
        </button>
      </div>

      <table class="an-table">
        <thead>
          <tr>
            <th class="col-no">번호</th>
            <th class="col-type">구분</th>
            <th>제목</th>
            <th class="col-author">작성자</th>
            <th class="col-date">등록일</th>
            <th class="col-date">수정일</th>
            <th class="col-view">조회수</th>
            <th class="col-manage">관리</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(notice, index) in pageRows"
            :key="notice.postId"
            :class="{ 'is-important': levelOf(notice) === 'IMPORTANT' }"
            @click="goDetail(notice.postId)"
          >
            <td>{{ displayNo(index) }}</td>
            <td><span class="an-badge" :class="'is-' + levelOf(notice).toLowerCase()">{{ levelLabel(notice) }}</span></td>
            <td class="an-title">
              <i v-if="levelOf(notice) !== 'GENERAL'" class="bi bi-pin-angle-fill"></i>
              <span>{{ notice.title }}</span>
            </td>
            <td>{{ notice.authorName || '관리자' }}</td>
            <td>{{ formatDate(notice.createdAt) }}</td>
            <td>{{ formatDate(notice.updatedAt) }}</td>
            <td>{{ notice.viewCount ?? 0 }}</td>
            <td class="an-manage" @click.stop>
              <button type="button" aria-label="수정" @click="goEdit(notice.postId)"><i class="bi bi-pencil"></i></button>
              <button type="button" class="is-delete" aria-label="삭제" @click="remove(notice)"><i class="bi bi-trash"></i></button>
            </td>
          </tr>
          <tr v-if="pageRows.length === 0">
            <td colspan="8" class="an-empty">등록된 공지사항이 없습니다.</td>
          </tr>
        </tbody>
      </table>

      <nav class="an-pager" aria-label="페이지 이동">
        <button type="button" :disabled="page <= 1" @click="goPage(page - 1)">‹</button>
        <button
          v-for="p in pageButtons"
          :key="p"
          type="button"
          :class="{ on: p === page }"
          @click="goPage(p)"
        >{{ p }}</button>
        <button type="button" :disabled="page >= totalPages" @click="goPage(page + 1)">›</button>
      </nav>
    </section>
  </div>
</template>

<script>
import { postsApi, NOTICE_CATEGORY, parseNoticeLevel, withAuthors, fmtDate, currentUser } from '@/utils/postsApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'

const PAGE_SIZE = 5

export default {
  data() {
    return {
      all: [],
      pool: [],
      pageRows: [],
      keyword: '',
      appliedKeyword: '',
      searchType: 'title',
      level: '',
      page: 1,
    }
  },
  computed: {
    chips() {
      const count = (key) => this.all.filter((p) => !key || parseNoticeLevel(p) === key).length
      return [
        { key: '', label: '전체', count: this.all.length },
        { key: 'IMPORTANT', label: '중요', count: count('IMPORTANT') },
        { key: 'GENERAL', label: '일반', count: count('GENERAL') },
        { key: 'URGENT', label: '필독', count: count('URGENT') },
      ]
    },
    totalPages() {
      return Math.max(1, Math.ceil(this.pool.length / PAGE_SIZE) || 1)
    },
    pageButtons() {
      const last = this.totalPages
      const start = Math.max(1, Math.min(this.page - 2, last - 4))
      const end = Math.min(last, start + 4)
      const pages = []
      for (let i = start; i <= end; i++) pages.push(i)
      return pages.length ? pages : [1]
    },
  },
  mounted() {
    this.load()
  },
  methods: {
    levelOf: parseNoticeLevel,
    levelLabel(post) {
      const level = parseNoticeLevel(post)
      if (level === 'URGENT') return '필독'
      if (level === 'IMPORTANT') return '중요'
      return '일반'
    },
    formatDate(date) {
      return fmtDate(date)
    },
    displayNo(index) {
      return this.pool.length - ((this.page - 1) * PAGE_SIZE) - index
    },
    async load() {
      try {
        const { data } = await postsApi.get(`/category/${NOTICE_CATEGORY}`, { params: { page: 0, size: 500 } })
        this.all = data.content || []
        await this.applyFilter()
      } catch (err) {
        console.error('공지사항 목록 조회 실패:', err)
      }
    },
    async applyFilter() {
      let rows = this.all
      const q = this.appliedKeyword.trim()
      if (q) {
        try {
          const { data } = await postsApi.get('/search', {
            params: { searchType: this.searchType, keyword: q, category: NOTICE_CATEGORY, page: 0, size: 200 },
          })
          const found = new Set((data.content || []).filter((p) => p.category === NOTICE_CATEGORY).map((p) => p.postId))
          rows = this.all.filter((p) => found.has(p.postId))
        } catch (err) {
          console.error('공지사항 검색 실패:', err)
        }
      }
      if (this.level) rows = rows.filter((p) => parseNoticeLevel(p) === this.level)
      this.pool = rows
      if (this.page > this.totalPages) this.page = this.totalPages
      const start = (this.page - 1) * PAGE_SIZE
      this.pageRows = await withAuthors(rows.slice(start, start + PAGE_SIZE))
    },
    search() {
      this.appliedKeyword = this.keyword
      this.page = 1
      this.applyFilter()
    },
    setLevel(key) {
      this.level = key
      this.page = 1
      this.applyFilter()
    },
    goPage(p) {
      if (p < 1 || p > this.totalPages) return
      this.page = p
      this.applyFilter()
    },
    goWrite() {
      this.$router.push({ name: 'admin-notice-write' })
    },
    goDetail(id) {
      this.$router.push({ name: 'admin-notice-detail', params: { id } })
    },
    goEdit(id) {
      this.$router.push({ name: 'admin-notice-update', params: { id } })
    },
    async remove(notice) {
      if (!confirm(`「${notice.title}」 공지를 삭제할까요?`)) return
      const user = currentUser()
      if (!user?.accountId) {
        alert('로그인이 필요합니다.')
        return
      }
      try {
        await postsApi.delete(`/${notice.postId}`, {
          params: { currentAccountId: user.accountId, isAdmin: true },
        })
        await this.load()
      } catch (err) {
        alert(`공지사항 삭제에 실패했습니다.\n${friendlyError(err)}`)
      }
    },
  },
}
</script>

<style scoped>
.an-page { padding-top: 8px; }
.an-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.an-head h1 { margin: 0; font-size: 26px; font-weight: 800; color: #0a2540; }
.an-head p { margin: 4px 0 0; font-size: 13.5px; color: #6b7c90; }
.an-card {
  background: #fff;
  border: 1px solid #e8edf4;
  border-radius: 18px;
  box-shadow: 0 10px 30px rgba(10, 37, 64, 0.05);
  padding: 22px 22px 18px;
}
.an-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.an-search { display: flex; align-items: center; gap: 8px; }
.an-select, .an-search input {
  height: 40px;
  border: 1px solid #e4e9f0;
  border-radius: 10px;
  background: #fff;
  padding: 0 12px;
  font-size: 13.5px;
  color: #0a2540;
}
.an-select { width: 92px; }
.an-search input { width: 240px; }
.an-search-btn, .an-write {
  height: 40px;
  border: 0;
  border-radius: 10px;
  font-weight: 800;
  cursor: pointer;
}
.an-search-btn { padding: 0 16px; background: #0a2540; color: #fff; }
.an-write { padding: 0 16px; background: #ff6b00; color: #fff; }
.an-chips { display: flex; gap: 8px; margin: 16px 0 14px; }
.an-chips button {
  border: 0;
  background: #eef2f6;
  color: #5d7086;
  border-radius: 999px;
  padding: 7px 14px;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}
.an-chips button b { font-weight: 800; margin-left: 4px; }
.an-chips button.on { background: #0a2540; color: #fff; }
.an-chips button.on:nth-child(2) { background: #ff6b00; }
.an-chips button.on:nth-child(4) { background: #e11d48; }
.an-table { width: 100%; border-collapse: collapse; }
.an-table th {
  text-align: center;
  font-size: 12.5px;
  color: #7b8da3;
  font-weight: 700;
  padding: 12px 8px;
  border-bottom: 1px solid #eef2f6;
}
.an-table td {
  text-align: center;
  padding: 14px 8px;
  border-bottom: 1px solid #f3f6fa;
  font-size: 13.5px;
  color: #334e68;
}
.an-table tbody tr { cursor: pointer; }
.an-table tbody tr.is-important { background: #fff6f6; }
.an-table tbody tr:hover td { background: #f8fbff; }
.an-table tbody tr.is-important:hover td { background: #fff1f1; }
.col-no { width: 64px; }
.col-type { width: 80px; }
.col-author { width: 110px; }
.col-date { width: 110px; }
.col-view { width: 72px; }
.col-manage { width: 88px; }
.an-title { text-align: left !important; font-weight: 700; color: #0a2540; }
.an-title i { color: #e11d48; margin-right: 4px; }
.an-badge {
  display: inline-block;
  min-width: 46px;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}
.an-badge.is-general { background: #eef2f6; color: #5d7086; }
.an-badge.is-important { background: #ffe4e6; color: #e11d48; }
.an-badge.is-urgent { background: #ffe4e6; color: #be123c; }
.an-manage { display: flex; justify-content: center; gap: 6px; }
.an-manage button {
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: #eef6ff;
  color: #2563eb;
  cursor: pointer;
}
.an-manage .is-delete { background: #fff1f2; color: #e11d48; }
.an-empty { padding: 48px 0 !important; color: #8aa0b8; }
.an-pager { display: flex; justify-content: center; gap: 8px; margin-top: 18px; }
.an-pager button {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 999px;
  background: #fff;
  color: #5d7086;
  font-weight: 800;
  cursor: pointer;
}
.an-pager button.on { background: #0a2540; color: #fff; }
.an-pager button:disabled { opacity: 0.35; cursor: default; }
</style>
