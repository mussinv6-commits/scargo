// 26.10.08 추가: 기사 앱 전용 라우터
// - 웹의 기사 화면 경로(/driver/app/...)를 그대로 써서, 화면 안의 링크(MY/차량으로 가기 등)가 수정 없이 동작한다.
// - 레이아웃만 PC용 사이드바(DriverAppLayout) 대신 하단 탭바(AppShell)로 바꾼다.
// - 앱은 파일로 실행되므로 해시 방식(#/driver/app/status) 주소를 쓴다.
import { createRouter, createWebHashHistory } from 'vue-router'
import { authState } from '@/auth/authState.js'
import driverApp from '@/router/driver/driverApp.js'
import AppShell from './AppShell.vue'
import AppLogin from './AppLogin.vue'

const HOME = '/driver/app/status'
const driverRoute = { ...driverApp[0], component: AppShell }

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: HOME },
    { path: '/login', name: 'login', component: AppLogin },
    { path: '/driver', redirect: HOME },
    driverRoute,
    { path: '/:pathMatch(.*)*', redirect: HOME },
  ],
  scrollBehavior(to, from, saved) {
    if (saved) return saved
    if (to.hash) return { el: to.hash, top: 80, behavior: 'smooth' }
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  const user = authState.user
  if (to.matched.some((r) => r.meta.requiresDriverAuth) && !user) {
    return { name: 'login' }
  }
  if (to.name === 'login' && user) return HOME
  return true
})

export default router
