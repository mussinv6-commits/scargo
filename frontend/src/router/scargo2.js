// 26.10.21 v8: web.html(기획 목업) W-01~W-16 을 실제 웹 화면으로. 화면 ID 1개 = 라우트 1개
import { T } from '@/scargo2/roles.js'
import { authState } from '@/auth/authState.js'
const C = import.meta.glob('@/scargo2/screens/W*.vue')
const child = (id) => ({ path: id, name: id, component: C['/src/scargo2/screens/' + id.replace('-', '') + '.vue'], meta: { sid: id, wScreen: true, title: T[id] }, ...(GUARD[id] ? { beforeEnter: GUARD[id] } : {}) })
// 26.10.28 총관리자는 지역센터 관제(W-05)를 '지역 물류센터 관제 > 지역별' 탭에서 본다 (관제사 · 지역센터 관제사는 그대로 W-05)
const GUARD = { 'W-05': () => (authState.user?.userType === 'ADMIN' ? { path: '/admin/center', query: { tab: 'region' } } : true) }
export default [
  { path: '/w', component: () => import('@/scargo2/ScLayout.vue'), children: [{ path: '', redirect: '/w/W-01' }, ...Object.keys(T).map(child),
    // 26.10.29 관제사 계정 내 정보 (총관리자는 /admin/profile). 사이드바가 있는 같은 화면 안에서 연다
    { path: 'receipts', name: 'w-receipts', component: () => import('@/components/common/CenterReceiptPanel.vue'), meta: { title: '센터 인수 확정', requiresCenterOp: true } }, // 26.10.29 지역센터 관제사 사이드바 메뉴
    // 26.10.29 인수 내역 (통합 관제사 · 허브 관제사). 총관리자는 /admin/handovers
    { path: 'handovers', name: 'w-handovers', component: () => import('@/components/admin/screens/AdminHandovers.vue'), meta: { title: '인수 내역', requiresAuth: true }, beforeEnter: () => { const t = authState.user?.userType; if (t === 'ADMIN') return '/admin/handovers'; return ['CONTROL', 'HUB_OP'].includes(t) ? true : '/w/W-01' } },
    { path: 'my', name: 'w-my', component: () => import('@/components/admin/screens/AdminProfile.vue'), meta: { title: '내 정보', requiresAuth: true }, beforeEnter: () => (authState.user ? (authState.user.userType === 'ADMIN' ? '/admin/profile' : true) : '/w/W-01') }] },
  { path: '/pending', name: 'pending', component: () => import('@/scargo2/PendingApproval.vue'), meta: { pendingOnly: true } }, // v13: 운송사 승인 대기
  { path: '/tower', redirect: '/w/W-02' }, // v7 컨트롤타워 → v8 W 화면
  { path: '/plan', redirect: '/w/W-01' },  // v7 iframe 방식 폐기
]
