// 2차 기획 v2: 컨트롤타워 (권한별 W-xx 화면). 실제 권한 검사는 서버 @PreAuthorize 와 같이 맞춘다.
// 26.10.20: 운송사 담당자(승인 운송사 = 로그인 응답 userType CORPORATE_APPROVED)도 W-02·W-09 (자기 회사 것만) 접근
export const TOWER_ROLES = ['ADMIN', 'CONTROL', 'HUB_OP', 'CENTER_OP', 'CORPORATE_APPROVED']
export default [{ path: '/tower', name: 'tower', component: () => import('@/tower/TowerPage.vue'), meta: { towerRoles: TOWER_ROLES } }]
// 26.10.21 v7: 기획 HTML 전체 화면(W-01~W-16) 그대로 보기 — /plan (W-01 로그인은 실제 서버 로그인)
export const planRoutes = [{ path: '/plan', name: 'plan', component: () => import('@/plan/PlanFrame.vue') }]
