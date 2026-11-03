// 26.11.01 서버/로컬 기사 설정 동기화 (TTS·검문소 필터·큰 글씨)
import { setVoice, setVoiceRateVolume } from './voice'
import { setCheckpointAllow } from './checkpoints'

const KEY = 'scargo.driverSettings'

export function loadLocalSettings() {
  try { return JSON.parse(localStorage.getItem(KEY) || 'null') } catch { return null }
}
export function saveLocalSettings(s) {
  try { localStorage.setItem(KEY, JSON.stringify(s || {})) } catch {}
}

export function applyDriverSettings(s) {
  if (!s) return
  saveLocalSettings(s)
  if (typeof s.ttsEnabled === 'boolean') setVoice(!!s.ttsEnabled)
  if (s.ttsRate != null || s.ttsVolume != null) setVoiceRateVolume(Number(s.ttsRate ?? 1), Number(s.ttsVolume ?? 1))
  if ('checkpointAllow' in s) setCheckpointAllow(s.checkpointAllow == null ? '' : String(s.checkpointAllow))
  try {
    document.documentElement.classList.toggle('large-text', !!s.largeText)
    document.documentElement.style.fontSize = s.largeText ? '18px' : ''
  } catch {}
}

export function bootDriverSettings() {
  const local = loadLocalSettings()
  if (local) applyDriverSettings(local)
}
