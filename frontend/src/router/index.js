import { createWebHistory, createRouter } from 'vue-router'; // npm i vue-router@next
import { authState } from '@/auth/authState.js';
import { hubAccess } from '@/components/amr/amrAccess.js'; // 26.10.22 통합: 허브 관제 화면 권한

// vite.config.js 에 @를 설정해야 @/ 경로를 사용할 수 있다
import member from '@/router/member/member.js';
import notice from '@/router/notice/notice.js';
import bbs from '@/router/bbs/bbs.js';
import company from '@/router/company/company.js';
import driverApp from '@/router/driver/driverApp.js';
import admin from '@/router/admin/admin.js';
import weather from '@/router/weather/weather.js';
import gate from '@/router/gate/gate.js';
import center from '@/router/center/center.js'; // 26.10.23 지역센터 인수 확정

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
        ...center,
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

    // 26.10.20: S-Bot 관리자는 S그룹 메인(/) · S-Bot(/sbot) · 로그인 화면만 사용 (S Cargo 쪽은 전부 차단, 알림창 없이 S-Bot 으로 이동)
    if (user?.userType === 'ADMIN' && user?.adminScope === 'SBOT') {
        const p = to.path;
        if (p !== '/' && p !== '/login' && !p.startsWith('/sbot')) return next({ name: 'sbot' });
    }

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
        // 26.10.20: S-Bot 관리자는 S Cargo 관리자 화면에 들어갈 수 없다
        if (user.adminScope === 'SBOT') {
            alert('S-Bot 관리자는 S Cargo 관리자 화면에 접근할 수 없습니다.');
            return next({ name: 'sbot' });
        }
    }

    // 26.10.20 추가: S-Bot 화면은 S-Bot 관리자만
    if (to.matched.some((r) => r.meta.requiresSbotAdmin)) {
        if (!user) {
            alert('로그인이 필요합니다.');
            sessionStorage.setItem('location', to.fullPath);
            return next({ name: 'login' });
        }
        if (user.userType !== 'ADMIN') { // 26.10.24: 마스터 관리자(S Cargo 관리자)도 S-Bot 허용
            // 26.10.22 통합: 허브 관제를 쓸 수 있는 관제사(CONTROL · HUB_OP)는 알림 없이 허브 관제로 보낸다
            if (hubAccess(user).canView) return next({ name: 'sbot-hub' });
            alert('S-Bot 관리자 계정만 접근할 수 있습니다.');
            return next({ name: 'group' });
        }
    }

    // 26.10.22 통합: 허브 AMR 관제(/sbot/hub)는 S-Bot 관리자 · CONTROL(조회 전용) · HUB_OP(소속 허브)
    //  어느 허브를 보고 바꿀 수 있는지는 화면과 서버가 다시 확인한다 (amrAccess.js · AmrAccessPolicy)
    if (to.matched.some((r) => r.meta.requiresHubControl)) {
        if (!user) {
            alert('로그인이 필요합니다.');
            sessionStorage.setItem('location', to.fullPath);
            return next({ name: 'login' });
        }
        const access = hubAccess(user);
        if (!access.canView) {
            alert(access.reason);
            return next({ name: 'group' });
        }
    }

    // 26.10.23 지역센터 화면(/center/**): 소속 센터가 있는 지역센터 관제사(CENTER_OP)만. ADMIN 은 /admin/center-receipts 사용
    if (to.matched.some((r) => r.meta.requiresCenterOp)) {
        if (!user) {
            alert('로그인이 필요합니다.');
            sessionStorage.setItem('location', to.fullPath);
            return next({ name: 'login' });
        }
        if (user.userType !== 'CENTER_OP') {
            alert('지역센터 관제사 계정만 접근할 수 있습니다.');
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
