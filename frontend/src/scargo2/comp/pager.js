// fix_1434: 표 페이지 나누기 (재사용). usePage(목록 ref 또는 함수, 페이지 크기) → { rows, page, pages, offset, reset }
import { computed, ref, watch, unref, reactive } from "vue"
export function usePage(src, size = 10) { return reactive(usePageRaw(src, size)) }
function usePageRaw(src, size) {
  const list = computed(() => { const v = typeof src === 'function' ? src() : unref(src); return Array.isArray(v) ? v : [] })
  const page = ref(1)
  const pages = computed(() => Math.max(1, Math.ceil(list.value.length / size)))
  watch(pages, (n) => { if (page.value > n) page.value = n })
  const offset = computed(() => (page.value - 1) * size)
  const rows = computed(() => list.value.slice(offset.value, offset.value + size))
  // 번호 칸(CSS 카운터)이 페이지를 넘어 이어지도록 시작 값
  const reset = computed(() => ({ counterReset: 'rn ' + offset.value }))
  return { rows, page, pages, offset, reset, size, total: computed(() => list.value.length) }
}
