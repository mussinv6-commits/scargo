// 26.10.21 v8: web.html(기획 목업) W-01~W-16 을 실제 웹 화면으로. 화면 ID 1개 = 라우트 1개
import { T } from '@/scargo2/roles.js'
const C = import.meta.glob('@/scargo2/screens/W*.vue')
const child = (id) => ({ path: id, name: id, component: C['/src/scargo2/screens/' + id.replace('-', '') + '.vue'], meta: { sid: id, wScreen: true, title: T[id] } })
export default [
  { path: '/w', component: () => import('@/scargo2/ScLayout.vue'), children: [{ path: '', redirect: '/w/W-01' }, ...Object.keys(T).map(child)] },
  { path: '/pending', name: 'pending', component: () => import('@/scargo2/PendingApproval.vue'), meta: { pendingOnly: true } }, // v13: 운송사 승인 대기
  { path: '/tower', redirect: '/w/W-02' }, // v7 컨트롤타워 → v8 W 화면
  { path: '/plan', redirect: '/w/W-01' },  // v7 iframe 방식 폐기
]
