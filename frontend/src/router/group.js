// S GROUP 랜딩 (계열 서비스 진입점). '/'는 기존 S CARGO 홈 유지.
export default [{ path: '/group', name: 'group-landing', component: () => import('@/views/group/GroupLanding.vue') }]
