import { createWebHistory, createRouter } from 'vue-router'; // npm i vue-router@next
import { authState } from '@/auth/authState.js';

// vite.config.js 에 @를 설정해야 @/ 경로를 사용할 수 있다
import member from '@/router/member/member.js';
import notice from '@/router/notice/notice.js';
import bbs from '@/router/bbs/bbs.js';
import company from '@/router/company/company.js';
import driverApp from '@/router/driver/driverApp.js';
import admin from '@/router/admin/admin.js';
import weather from '@/router/weather/weather.js';
import gate from '@/router/gate/gate.js';

const router = createRouter({
    history: createWebHistory(),
    routes: [
        ...member,
        ...notice,
        ...bbs,
        ...company,
        ...driverApp,
        ...admin,
        ...weather,
        ...gate,
    ],
    scrollBehavior(to, from, savedPosition) {
        if (savedPosition) {
            return savedPosition; // 뒤로가기/앞으로가기 시 이전 스크롤 위치 복원
        }
        return { top: 0 }; // 새로운 라우트로 이동 시 항상 맨 위로
    },
})

// 라우트 진입 전 권한 체크 (관리자/메인메뉴: 관리자·사업자·화물차 기사 3분류 공용 가드)
router.beforeEach((to, from, next) => {
    const user = authState.user; // reactive 객체에서 바로 꺼내 씀

    if (user?.userType === 'ADMIN') {
        if (to.name === 'notice') return next({ name: 'admin-notices' });
        if (to.name === 'noticeWrite') return next({ name: 'admin-notice-write' });
        if (to.name === 'noticeUpdate') return next({ name: 'admin-notice-update', params: to.params });
        if (to.name === 'noticeDetail') return next({ name: 'admin-notice-detail', params: to.params });
    }

    // 관리자 화면: ADMIN 계정만
    if (to.matched.some((r) => r.meta.requiresAdmin)) {
        if (!user) {
            alert('로그인이 필요합니다.');
            sessionStorage.setItem('location', to.fullPath);
            return next({ name: 'login' });
        }
        if (user.userType !== 'ADMIN') {
            alert('관리자 계정만 접근할 수 있습니다.');
            return next({ name: 'home' });
        }
    }

    // 사업자(회사) 화면: 승인된 회사 계정만
    if (to.matched.some((r) => r.meta.requiresCorporate)) {
        if (!user) {
            alert('로그인이 필요합니다.');
            sessionStorage.setItem('location', to.fullPath); // 로그인 후 원래 가려던 곳으로
            return next({ name: 'login' });
        }
        if (user.userType !== 'CORPORATE_APPROVED') {
            alert('승인된 회사 계정만 접근할 수 있습니다.');
            return next({ name: 'home' });
        }
    }

    // 로그인만 필요한 화면
    if (to.matched.some((r) => r.meta.requiresAuth) && !user) {
        alert('로그인이 필요합니다.');
        sessionStorage.setItem('location', to.fullPath);
        return next({ name: 'login' });
    }

    // 기사 화면: 로그인만 되어 있으면 접근 가능
    // (운송현황/배차목록/정산매출/MY차량 4개 화면, meta.requiresDriverAuth로 표시됨)
    if (to.matched.some((r) => r.meta.requiresDriverAuth)) {
        if (!user) {
            alert('로그인이 필요합니다.');
            sessionStorage.setItem('location', to.fullPath);
            return next({ name: 'login' });
        }
    }

    next();
})

export default router;
