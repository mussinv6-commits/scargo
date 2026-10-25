// 26.10.24 일일 처리량 보고서 API (ADMIN · CONTROL 전체, HUB_OP 소속 허브)
import axios from 'axios'
import { API_BASE } from './apiBase'
import { downloadBlob } from './exportExcel.js'

const http = axios.create({ baseURL: API_BASE, withCredentials: true })

export const dailyReport = (date) =>
  http.get('/api/reports/daily', { params: { date: date || undefined } }).then((r) => r.data)

export async function downloadDailyReport(date) {
  const res = await http.get('/api/reports/daily/xlsx', { params: { date: date || undefined }, responseType: 'blob' })
  downloadBlob(res.data, `일일처리량_${date || new Date().toISOString().slice(0, 10)}.xlsx`)
}
