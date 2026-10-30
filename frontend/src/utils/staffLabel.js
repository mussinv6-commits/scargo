// 26.10.29 QA: 담당자를 '관제 담당' 같은 임의 문구 대신 실제 권한명으로 표시
//  ADMIN → 총관리자, CONTROL → 통합 관제사, HUB_OP → '<허브명> 관제사', CENTER_OP → '<센터명> 관제사'
//  이름이 이미 권한명이면(예: '곤지암허브 관제사') 이름만, 아니면 '권한명 이름'
import axios from 'axios'
import { API_BASE } from './apiBase'

const http = axios.create({ baseURL: API_BASE, withCredentials: true })

export function roleTitle(u) {
  if (!u) return ''
  if (u.role === 'ADMIN') return '총관리자'
  if (u.role === 'CONTROL') return '통합 관제사'
  if (u.role === 'HUB_OP') return (u.hubName ? u.hubName + ' ' : '허브 ') + '관제사'
  if (u.role === 'CENTER_OP') return (u.centerName ? u.centerName + ' ' : '물류센터 ') + '관제사'
  return ''
}

export function staffLabel(u) {
  if (!u) return ''
  const t = roleTitle(u), n = (u.userName || '').trim()
  if (!t) return n
  if (!n || n === t || /(관제사|관리자)$/.test(n)) return n || t
  return t + ' ' + n
}

let cache = null
export async function loadAssignees() {
  if (cache) return cache
  const r = await http.get('/api/staff/assignees')
  cache = (Array.isArray(r.data) ? r.data : []).map((u) => ({ ...u, label: staffLabel(u) }))
  return cache
}

// 예전에 저장해 둔 '관제 담당' · '허브 담당' 은 권한명이 아니다
const LEGACY_ASSIGNEE = {
  '관제 담당': '통합 관제사',
  '허브 담당': '허브 관제사',
  '센터 담당': '물류센터 관제사',
  '지역 담당': '물류센터 관제사',
  '지역센터 담당': '물류센터 관제사',
}

// 담당 칸: 직원 계정이면 '곤지암허브 관제사 홍길동', 예전 문구면 권한명으로 바꾼다
export function displayAssignee(row, staff = []) {
  const raw = typeof row === 'string' ? row : (row?.assignee || '')
  const s = String(raw || '').trim()
  if (!s) return '-'
  const hit = staff.find((u) => u.label === s || u.userName === s || u.userId === s)
  if (hit?.label) return hit.label
  const role = typeof row === 'object' ? (row?.assigneeRole || row?.role) : ''
  if (role) {
    const name = LEGACY_ASSIGNEE[s] ? '' : s
    const label = staffLabel({
      role,
      userName: name,
      hubName: row.assigneeHub || row.hubName,
      centerName: row.assigneeCenter || row.centerName,
    })
    if (label) return label
  }
  return LEGACY_ASSIGNEE[s] || s
}
