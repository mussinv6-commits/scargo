// 26.11.03 경보(화재) 변화를 화면들이 바로 알아채게 하는 신호.
//  예전: 각 화면이 3~8초마다 따로 서버에 물어봐서 공지 · 알림이 늦게 떴다.
//  지금: 화재 시작 · 종료를 서버에 알린 직후 pokeSafety() 를 부르면, 같은 탭은 이벤트로, 다른 탭은 storage 이벤트로 즉시 다시 조회한다.
const KEY = 'sbot_safety_poke'
export function pokeSafety() {
  try { window.dispatchEvent(new Event('sbot-safety-poke')) } catch { /* 무시 */ }
  try { localStorage.setItem(KEY, String(Date.now())) } catch { /* 무시 */ }
}
// fn 을 신호가 올 때마다 부른다. 반환값을 부르면 해제
export function onSafetyPoke(fn) {
  const h = () => { try { fn() } catch { /* 무시 */ } }
  const s = (e) => { if (!e.key || e.key === KEY || e.key === 'sbot_fire_v1') h() }
  window.addEventListener('sbot-safety-poke', h)
  window.addEventListener('storage', s)
  return () => { window.removeEventListener('sbot-safety-poke', h); window.removeEventListener('storage', s) }
}
