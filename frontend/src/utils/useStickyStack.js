// 26.10.28 상단 고정: 메뉴명(상단 바) 아래의 설명 글자와 탭도 메뉴명과 함께 고정한다.
//  스크롤 영역(.admin-main) 안에서 첫 번째 '설명 줄'과 첫 번째 '탭 줄'을 찾아, 화면에 나오는 순서대로
//  상단 바 바로 밑에 차례로 붙인다 (높이는 화면마다 달라서 실제 높이를 재서 top 을 정함).
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'

// 설명 줄: 공통 헤더(AdminPageHeader)와 직접 제목을 그리는 몇몇 화면의 설명 영역
// .sticky-head = 설명 · 안내 줄 · 탭을 한 덩어리로 고정하고 싶은 화면이 직접 감싸는 상자 (운영 센터: 설명 + 자동 백업 + 경보 + 탭)
const DESC = '.sticky-head, .admin-page-header, .an-head, .gs-head, .sbi-head, .mg-d'
// 탭 줄: 화면마다 이름이 달라서 알려진 탭 클래스를 모두 적는다
const TABS = '.sa-tabs, .ch-tabs, .oc-tabs, .sbi-tabs, .w-tabs, .crp-tabs, .tabs-sticky'

export function useStickyStack(mainRef) {
  const route = useRoute()
  let mo = null, ro = null, raf = 0, marked = []

  function clear() {
    for (const el of marked) { el.style.position = ''; el.style.top = ''; el.style.zIndex = ''; el.style.background = ''; el.classList.remove('is-stuck') }
    marked = []
  }
  function apply() {
    raf = 0
    const main = mainRef.value
    if (!main) return
    clear()
    const bar = main.querySelector('.admin-topbar')
    let top = bar ? bar.offsetHeight : 0
    const desc = main.querySelector(DESC)
    let tabs = main.querySelector(TABS)
    if (tabs && desc && desc.contains(tabs)) tabs = null // 탭이 이미 고정 상자 안에 있으면 따로 고정하지 않음
    const picks = [desc, tabs].filter(Boolean)
    // 화면에 나오는 순서대로 (탭이 설명보다 위에 있는 화면도 있음: 컨테이너 관리 · 가입 승인)
    picks.sort((a, b) => (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1))
    let z = 9
    for (const el of picks) {
      el.style.position = 'sticky'
      el.style.top = top + 'px'
      el.style.zIndex = String(z--)
      el.classList.add('is-stuck')
      el.style.background = bgOf(el) // 고정된 줄 밑으로 내용이 비쳐 보이지 않게
      marked.push(el)
      top += el.offsetHeight
    }
  }
  function bgOf(el) {
    for (let n = el; n && n !== document.body; n = n.parentElement) {
      const c = getComputedStyle(n).backgroundColor
      if (c && c !== 'transparent' && !/rgba\(0, 0, 0, 0\)/.test(c)) return c
    }
    return '#e8eef6'
  }
  const schedule = () => { if (!raf) raf = requestAnimationFrame(apply) }

  // 26.10.28 탭을 바꾸면 내용 맨 위로 (스크롤을 내린 채로 다른 탭을 눌러도 아래에 멈춰 있지 않게). 모든 화면의 탭 공통
  const TAB_BTN = TABS.split(',').map((c) => c.trim() + ' button').join(', ') + ', .kp-tabs button, .chips button'
  function onTabClick(e) {
    if (!e.target.closest(TAB_BTN)) return
    const main = mainRef.value
    if (main) requestAnimationFrame(() => main.scrollTo({ top: 0 }))
  }

  onMounted(() => {
    schedule()
    const main = mainRef.value
    if (!main) return
    main.addEventListener('click', onTabClick)
    // 탭을 바꾸거나 데이터가 들어와 화면이 다시 그려지면 다시 잰다
    mo = new MutationObserver(schedule)
    mo.observe(main, { childList: true, subtree: true })
    if (window.ResizeObserver) { ro = new ResizeObserver(schedule); ro.observe(main) }
    window.addEventListener('resize', schedule)
  })
  watch(() => route.fullPath, schedule)
  onBeforeUnmount(() => { mainRef.value?.removeEventListener('click', onTabClick); mo?.disconnect(); ro?.disconnect(); window.removeEventListener('resize', schedule); if (raf) cancelAnimationFrame(raf); clear() })
}
