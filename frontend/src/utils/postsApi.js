// 26.09.30 추가: 게시판/공지사항 공용 API 헬퍼 (백엔드 PostController: /api/v1/posts)
// - PostCategory enum: NOTICE(공지) / FREE(자유게시판) / QNA / FAQ
// - 등록/수정은 multipart/form-data, 작성자 accountId 와 권한(currentAccountId, isAdmin)은 요청 파라미터로 전달
// - PostResponse 에 작성자 이름이 없어서 accountId → 이름을 /api/accounts/{id} 로 조회해 캐싱
import axios from 'axios'
import { API_BASE } from './apiBase'
import { authState } from '@/auth/authState.js'

export const postsApi = axios.create({ baseURL: `${API_BASE}/api/v1/posts`, withCredentials: true })
export const commentsApi = axios.create({ baseURL: `${API_BASE}/api/comments`, withCredentials: true })

export const NOTICE_CATEGORY = 'NOTICE'

/** 공지 구분: GENERAL | IMPORTANT | URGENT */
export function parseNoticeLevel(post) {
  const lv = String(post?.noticeLevel || '').toUpperCase()
  if (lv === 'URGENT') return 'URGENT'
  if (lv === 'IMPORTANT') return 'IMPORTANT'
  const title = String(post?.title || '')
  if (/^\s*\[긴급\]/.test(title)) return 'URGENT'
  if (/^\s*\[중요\]/.test(title)) return 'IMPORTANT'
  if (post?.isPinned) return 'IMPORTANT'
  return 'GENERAL'
}

export function noticeTypeLabel(levelOrPost) {
  const level = typeof levelOrPost === 'string' ? levelOrPost : parseNoticeLevel(levelOrPost)
  if (level === 'URGENT') return '긴급'
  if (level === 'IMPORTANT') return '중요'
  return '일반'
}

export function noticeTypeClass(levelOrPost) {
  const level = typeof levelOrPost === 'string' ? levelOrPost : parseNoticeLevel(levelOrPost)
  if (level === 'URGENT') return 'badge-urgent'
  if (level === 'IMPORTANT') return 'badge-must-read'
  return 'badge-default'
}

export function isAlertNotice(post) {
  const level = parseNoticeLevel(post)
  return level === 'URGENT' || level === 'IMPORTANT'
}

export function currentUser() {
  return authState.user
}
export function isAdminUser() {
  return authState.user?.userType === 'ADMIN'
}
export function canManagePost(post) {
  const u = authState.user
  if (!u || !post) return false
  return u.userType === 'ADMIN' || Number(post.accountId) === Number(u.accountId)
}

const nameCache = new Map()
export async function authorName(accountId) {
  if (!accountId) return '-'
  if (authState.user && Number(authState.user.accountId) === Number(accountId)) return authState.user.userName
  if (nameCache.has(accountId)) return nameCache.get(accountId)
  const p = axios
    .get(`${API_BASE}/api/accounts/${accountId}`, { withCredentials: true })
    .then((r) => r.data?.userName || r.data?.userId || `회원 #${accountId}`)
    .catch(() => `회원 #${accountId}`)
  nameCache.set(accountId, p)
  return p
}

/** 목록에 작성자 이름(authorName) 채워넣기 */
export async function withAuthors(list) {
  const names = await Promise.all(list.map((p) => authorName(p.accountId)))
  return list.map((p, i) => ({ ...p, authorName: names[i] }))
}

export function fmtDate(d, withTime = false) {
  if (!d) return ''
  const s = String(d).replace('T', ' ')
  return withTime ? s.substring(0, 16) : s.substring(0, 10)
}

/** 첨부파일 다운로드 URL */
export function attachmentUrl(file) {
  return `${API_BASE}/api/v1/posts/attachments/${file.attachmentId}`
}
