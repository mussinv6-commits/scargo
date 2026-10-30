// 26.10.28 QA C-3 · #7 · #8 · #15: iframe(3D 관제 · 구간 관제) 높이를 화면에 딱 맞춘다 → 바깥 스크롤바가 생기지 않게
//  가장 가까운 스크롤 영역(관리자 화면이면 .admin-main)의 아래 끝까지 채운다. 화면 축소(zoom)도 반영
import { onBeforeUnmount, onMounted, ref } from 'vue'

function scrollParent(el) {
  for (let n = el?.parentElement; n; n = n.parentElement) {
    const oy = getComputedStyle(n).overflowY
    if (oy === 'auto' || oy === 'scroll' || oy === 'hidden') return n
  }
  return null
}

export function useFitHeight(elRef, { min = 420, gap = 8 } = {}) {
  const height = ref(null)
  let ro = null
  function fit() {
    const el = elRef.value
    if (!el) return
    const box = scrollParent(el)
    const r = el.getBoundingClientRect()
    const scale = el.offsetHeight ? r.height / el.offsetHeight : 1 // 화면 좌표 ↔ CSS px 비율 (zoom)
    const bottom = box ? box.getBoundingClientRect().bottom : window.innerHeight
    // 스크롤 영역까지 올라가며 아래쪽 안쪽 여백 · 테두리를 모두 뺀다 (안에 낀 래퍼의 여백 때문에 20px 넘치던 문제)
    let padB = 0
    for (let n = el.parentElement; n; n = n.parentElement) {
      const cs = getComputedStyle(n)
      padB += (parseFloat(cs.paddingBottom) || 0) + (parseFloat(cs.borderBottomWidth) || 0)
      if (n === box) break
    }
    const top = r.top + (box ? box.scrollTop * scale : 0)
    const h = Math.floor((bottom - top) / (scale || 1) - padB - gap)
    height.value = Math.max(min, h)
  }
  onMounted(() => {
    fit()
    requestAnimationFrame(fit)
    window.addEventListener('resize', fit)
    const box = scrollParent(elRef.value)
    if (box && window.ResizeObserver) { ro = new ResizeObserver(fit); ro.observe(box) }
  })
  onBeforeUnmount(() => { window.removeEventListener('resize', fit); ro?.disconnect() })
  return height
}
