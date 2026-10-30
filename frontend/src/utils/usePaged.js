// 26.10.28 QA 공통 규칙: 표 페이지 나누기 (기본 10건, 운영센터 카드형 목록·도착 점검 현황판은 5건)
//  usePaged(목록 ref 또는 함수, 페이지 크기) → { page, pageCount, rows, no(i) }
//  화면에는 <AdminPager v-model="pg.page" :page-count="pg.pageCount" /> 와 함께 쓴다 (번호 3개 + ‹ ›)
import { computed, reactive, ref, unref, watch } from 'vue'

export function usePaged(src, size = 10) {
  const list = computed(() => {
    const v = typeof src === 'function' ? src() : unref(src)
    return Array.isArray(v) ? v : []
  })
  const page = ref(1)
  const pageCount = computed(() => Math.max(1, Math.ceil(list.value.length / size)))
  watch(pageCount, (n) => { if (page.value > n) page.value = n })
  const rows = computed(() => list.value.slice((page.value - 1) * size, page.value * size))
  // 번호: 화면 기준 1부터, 페이지를 넘겨도 이어지게
  const no = (i) => (page.value - 1) * size + i + 1
  return reactive({ page, pageCount, rows, no, size, total: computed(() => list.value.length) })
}
