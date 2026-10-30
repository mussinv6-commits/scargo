// 26.10.24 일일 처리량 보고서 API (ADMIN · CONTROL 전체, HUB_OP 소속 허브)
// 26.10.29 엑셀은 서버 파일 대신 화면 조회 결과로 공통 보고서 양식에 맞춰 만든다
import axios from 'axios'
import { API_BASE } from './apiBase'
import { downloadBlob } from './xlsxLite.js'
import { buildDailyReportXlsx } from './dailyReportXlsx.js'

const http = axios.create({ baseURL: API_BASE, withCredentials: true })

// 26.10.30 to 를 주면 date ~ to(포함) 기간 합계 (주간 · 월간 · 연간). period 는 엑셀 제목용: day | week | month | year
export const dailyReport = (date, to) =>
  http.get('/api/reports/daily', { params: { date: date || undefined, to: to && to !== date ? to : undefined } }).then((r) => r.data)

const PNAME = { day: '일일', week: '주간', month: '월간', year: '연간' }

export async function downloadDailyReport(date, to, period = 'day') {
  const report = await dailyReport(date, to)
  const day = date || new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10)
  const span = to && to !== day ? `${day}~${to}` : day
  downloadBlob(buildDailyReportXlsx(report, day, to, period), `${PNAME[period] || '일일'}처리량_${span}.xlsx`)
}
