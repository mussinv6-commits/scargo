// 음성 안내 (APK: 안드로이드 TTS 엔진 / 브라우저: Web Speech API)
// fix_app_1026c: 실제 폰에서 소리가 안 나던 문제
//  - AndroidManifest 에 TTS_SERVICE <queries> 추가 (Android 11+ 는 이게 없으면 TTS 엔진을 못 찾음)
//  - 시작할 때 엔진·한국어 지원 확인 → voice.status ('checking' | 'ok' | 'no-engine' | 'no-korean')
//  - 네이티브 TTS 가 실패하면 Web Speech API 로 한 번 더 시도, 둘 다 안 되면 내비에 안내 문구
import { Capacitor } from '@capacitor/core'
import { TextToSpeech } from '@capacitor-community/text-to-speech'
import { reactive } from 'vue'

const KEY = 'scargo.voice'
const native = Capacitor.isNativePlatform()
const web = typeof window !== 'undefined' && 'speechSynthesis' in window && typeof SpeechSynthesisUtterance !== 'undefined'

export const voice = reactive({
  enabled: (() => { try { return localStorage.getItem(KEY) !== 'off' } catch { return true } })(),
  last: '',
  status: 'checking',
  engine: native ? 'native' : web ? 'web' : 'none',
  lastError: '',
  talking: false // 26.10.29 지금 읽는 중 (화재 사이렌이 음성과 겹치지 않게 쉼)
})

function webKorean() {
  try { return speechSynthesis.getVoices().some(v => (v.lang || '').toLowerCase().startsWith('ko')) } catch { return false }
}

export async function initVoice() {
  if (native) {
    try {
      const { supported } = await TextToSpeech.isLanguageSupported({ lang: 'ko-KR' })
      if (supported) { voice.engine = 'native'; voice.status = 'ok'; return voice.status }
      voice.status = 'no-korean'
    } catch (e) {
      voice.lastError = e?.message || String(e)
      voice.status = 'no-engine'
    }
    if (web) { voice.engine = 'web'; voice.status = 'ok' } // 웹뷰 음성으로 대체
    return voice.status
  }
  if (!web) { voice.engine = 'none'; voice.status = 'no-engine'; return voice.status }
  voice.engine = 'web'
  // 목소리 목록은 늦게 채워지는 경우가 있어 잠깐 기다림. 한국어 목소리가 없어도 기본 목소리로 읽음
  await new Promise(r => { if (speechSynthesis.getVoices().length) return r(); speechSynthesis.onvoiceschanged = () => r(); setTimeout(r, 1500) })
  voice.status = speechSynthesis.getVoices().length === 0 ? 'no-engine' : webKorean() ? 'ok' : 'no-korean'
  return voice.status
}
initVoice()
if (import.meta.env.DEV && typeof window !== 'undefined') window.__scargoVoice = voice // 개발(브라우저) 테스트용

let voiceRate = 1, voiceVolume = 1
try { voiceRate = Number(localStorage.getItem('scargo.voiceRate') || 1) || 1; voiceVolume = Number(localStorage.getItem('scargo.voiceVolume') || 1) || 1 } catch {}
export function setVoiceRateVolume(rate, volume) {
  if (rate != null && !Number.isNaN(Number(rate))) { voiceRate = Math.min(2, Math.max(0.5, Number(rate))); try { localStorage.setItem('scargo.voiceRate', String(voiceRate)) } catch {} }
  if (volume != null && !Number.isNaN(Number(volume))) { voiceVolume = Math.min(1, Math.max(0, Number(volume))); try { localStorage.setItem('scargo.voiceVolume', String(voiceVolume)) } catch {} }
}
export function setVoice(on) {
  voice.enabled = on
  try { localStorage.setItem(KEY, on ? 'on' : 'off') } catch { /* 무시 */ }
  if (!on) stopAll()
}

function stopAll() {
  if (native) TextToSpeech.stop().catch(() => {})
  if (web) try { speechSynthesis.cancel() } catch { /* 무시 */ }
}

// 26.10.29 브라우저(크롬 등)는 화면을 한 번도 누르지 않은 상태에서 음성을 막는다(not-allowed).
//  막힌 안내는 기억해 두었다가 사용자가 화면을 처음 누르는 순간 바로 읽는다 (화재 경보처럼 놓치면 안 되는 안내)
let pendingText = ''
if (web && typeof window !== 'undefined') {
  const replay = () => { if (pendingText) { const t = pendingText; pendingText = ''; speakWeb(t) } }
  ;['pointerdown', 'touchstart', 'keydown'].forEach((e) => window.addEventListener(e, replay, { passive: true }))
}
function speakWeb(text) {
  return new Promise((resolve) => {
    if (!web) return resolve()
    try {
      // 한 번도 누르지 않은 화면이면 지금은 못 읽으므로 기억해 두었다가 첫 터치 때 읽음
      if (navigator.userActivation && !navigator.userActivation.hasBeenActive) { pendingText = text; return resolve() }
      const u = new SpeechSynthesisUtterance(text)
      u.lang = 'ko-KR'; u.rate = voiceRate; u.volume = voiceVolume
      const ko = speechSynthesis.getVoices().find(v => (v.lang || '').toLowerCase().startsWith('ko'))
      try { if (ko) u.voice = ko } catch { /* 목소리 지정 실패해도 기본 목소리로 읽음 */ }
      u.onend = () => resolve()
      u.onerror = (e) => { if (e && e.error === 'not-allowed') pendingText = text; resolve() }
      speechSynthesis.speak(u)
      setTimeout(resolve, 15000)
    } catch { resolve() }
  })
}

async function speakOne(text) {
  voice.talking = true
  try { await speakOne0(text) } finally { voice.talking = false }
}
async function speakOne0(text) {
  if (voice.engine === 'native') {
    try {
      await TextToSpeech.speak({ text, lang: 'ko-KR', rate: voiceRate, pitch: 1.0, volume: 1.0, category: 'playback', queueStrategy: 0 })
      return
    } catch (e) {
      voice.lastError = e?.message || String(e)
      if (!web) { voice.status = 'no-engine'; return }
      voice.engine = 'web'
    }
  }
  if (voice.engine === 'web') return speakWeb(text)
}

let speaking = Promise.resolve()

/** urgent=true 면 지금 말하던 것을 끊고 바로 말함 (높이제한 등)
 *  force=true 면 음성 안내를 꺼 두어도 말함 (26.10.29 화재 경보처럼 안전에 관한 안내) */
export function speak(text, { urgent = false, force = false } = {}) {
  if ((!voice.enabled && !force) || !text) return
  voice.last = text
  // 26.10.29 폰(네이티브 TTS): stop() 을 따로 부르면 stop 이 speak 보다 늦게 처리돼 방금 시작한 안내까지 끊기는 경우가 있음
  //  → 네이티브는 queueStrategy 0(이전 안내 끊고 바로 읽기)만으로 처리, 브라우저 음성만 cancel
  if (urgent) { if (voice.engine !== 'native' && web) try { speechSynthesis.cancel() } catch { /* 무시 */ }; speaking = Promise.resolve().then(() => speakOne(text)) }
  else speaking = speaking.then(() => speakOne(text))
}

// 거리 읽기: 1,234m → "1.2킬로미터", 300m → "300미터"
export function sayDistance(m) {
  if (m >= 950) return `${(Math.round(m / 100) / 10).toString().replace(/\.0$/, '')}킬로미터`
  return `${Math.max(10, Math.round(m / 50) * 50)}미터`
}
