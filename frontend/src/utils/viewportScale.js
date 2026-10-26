// v13: 화면 비율 축소. 디자인 기준 폭(1280px)보다 창이 좁으면 html 전체에 CSS zoom 을 걸어
// 줄바꿈·접힘 없이 전체 화면 그대로 축소해서 보여준다. 기준 폭 이상이면 원래 크기(zoom 1).
// Chrome/Edge 128+ 표준 zoom 은 클릭 좌표·드롭다운(Popper)·모달 위치를 자동으로 맞춘다.
export const DESIGN_WIDTH = 1280
export function installViewportScale() {
  const root = document.documentElement
  const apply = () => {
    const z = Math.min(1, window.innerWidth / DESIGN_WIDTH)
    root.style.zoom = z === 1 ? '' : String(z)
    root.style.setProperty('--sc-zoom', String(z)) // 100vh 보정용 (calc(100vh / var(--sc-zoom)))
  }
  apply()
  window.addEventListener('resize', apply)
}
