import { createRouter, createWebHashHistory } from 'vue-router'
import { isLoggedIn, session } from './lib/session'
import { APP_HOME, ROLE_OF_SCREEN } from './mockup/store.js'
import './mockup/ap.css'

// 26.10.21 v8: app.html(기획 목업) 화면 ID 1개 = 라우트 1개 (/m/A-01 …). v7 의 iframe(/plan) 방식은 폐기
const M = import.meta.glob('./mockup/screens/*.vue')
const IDS = ['A-01', 'A-02-1', 'A-02-2', 'A-02-3', 'A-02-4', 'A-03', 'T-01', 'T-02', 'T-03', 'P-01', 'P-03', 'P-04', 'H-01', 'F-01', 'D-00', 'C-00', 'C-01', 'C-02'] // fix_1434: F-01 고속도로 요금표 (기사 공통)
const PUBLIC = ['A-01', 'A-02-1', 'A-02-2', 'A-02-3', 'A-02-4']
const mockRoutes = IDS.map(id => ({ path: '/m/' + id, name: id, component: M['./mockup/screens/' + id.replace(/-/g, '') + '.vue'], meta: { sid: id, public: PUBLIC.includes(id) } }))

// fix_1300: v7 옛 화면(/load 적재 등록 · /precheck · /explore · /login-v7)과 /ops 데모 라우트 삭제.
//  /load 는 T-02 인수 확인(씰 대조·보류)을 건너뛰고 오더를 LOADED 로 바꿀 수 있었음. 남은 v7 화면: 내비·도착·이력·알림·내 정보
const routes = [
  ...mockRoutes,
  { path: '/login', redirect: '/m/A-01' },                                                            // 01 → A-01
  { path: '/', redirect: '/m/D-00' }, // fix_1300: v7 홈 삭제 (권한별 홈은 아래 가드가 결정)
  { path: '/navi/:id?', component: () => import('./views/Navi.vue'), meta: { tab: true } },         // 05
  { path: '/arrive/:id', component: () => import('./views/Arrive.vue') },                            // 06
  { path: '/history', component: () => import('./views/History.vue'), meta: { tab: true } },        // 07
  { path: '/alerts', component: () => import('./views/Alerts.vue') },                                // 08
  { path: '/alerts/:id', component: () => import('./views/AlertDetail.vue') }, // 26.10.29 알림 상세
  { path: '/plan', redirect: '/m/A-01' }, // v7 iframe → v8 실제 화면
  { path: '/me', component: () => import('./views/Me.vue'), meta: { tab: true } },
  { path: '/incident', component: () => import('./views/IncidentReport.vue') },
  { path: '/support', component: () => import('./views/Support.vue') },
  { path: '/schedule', component: () => import('./views/Schedule.vue') },
  { path: '/settlements', component: () => import('./views/Settlements.vue') },
  { path: '/documents', component: () => import('./views/Documents.vue') },
  { path: '/logbook', component: () => import('./views/Logbook.vue') },
  { path: '/nearby', component: () => import('./views/Nearby.vue') },
  { path: '/inspection-history', component: () => import('./views/InspectionHistory.vue') },
  { path: '/settings', component: () => import('./views/AppSettings.vue') },                  // 내정보
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

// APK 안에서는 해시 라우팅이 가장 안전 (새로고침해도 404 없음)
const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to) => {
  if (!to.meta.public && !isLoggedIn()) return '/m/A-01'
  const t = session.me?.userType
  // v9: 승인 전 기사는 A-03 만 (서버도 ROLE_PENDING_DRIVER 로 다른 API 차단)
  const st = session.me?.approvalStatus
  if (isLoggedIn() && st && st !== 'APPROVED' && !to.meta.public && to.path !== '/m/A-03' && !to.path.startsWith('/alerts')) return '/m/A-03' // 26.10.28: 승인 결과 알림은 볼 수 있게
  if (to.path === '/m/A-01' && isLoggedIn() && APP_HOME[t]) return APP_HOME[t]
  // 홈(/)은 권한별 홈으로 (컨테이너 기사 T-01 · 지선기사 P-01 · 허브 관제사 H-01)
  if (to.path === '/' && APP_HOME[t]) return APP_HOME[t]
  // 권한 밖 화면 차단 (T-xx 컨테이너 기사, P-xx 지선기사, H-xx 허브 관제사). 승인 전 계정은 A-03
  const need = to.meta.sid && ROLE_OF_SCREEN(to.meta.sid)
  if (need && isLoggedIn() && !need.includes(t)) return APP_HOME[t] || '/m/A-03'
})

export default router
