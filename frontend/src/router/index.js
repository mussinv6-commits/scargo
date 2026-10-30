import { createWebHistory, createRouter } from 'vue-router'; // npm i vue-router@next
import { authState } from '@/auth/authState.js';
import { hubAccess, isMaster } from '@/components/amr/amrAccess.js'; // 26.10.22 (share): 허브 관제 화면 권한

// vite.config.js 에 @를 설정해야 @/ 경로를 사용할 수 있다
import member from '@/router/member/member.js';
import notice from '@/router/notice/notice.js';
import bbs from '@/router/bbs/bbs.js';
import company from '@/router/company/company.js';
import driverApp from '@/router/driver/driverApp.js';
import scargo2 from '@/router/scargo2.js'; // 26.10.21 v8: 기획 web.html W-01~W-16
import { webRole, canSee, HOME, homeOf, DRIVER_TYPES } from '@/scargo2/roles.js';
import group from '@/router/group.js';
import admin from '@/router/admin/admin.js';
import weather from '@/router/weather/weather.js';
import gate from '@/router/gate/gate.js';
import center from '@/router/center/center.js'; // 26.10.23 (share): 지역센터 인수 확정

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
        ...scargo2, // 26.10.21 v8: /w/W-01 ~ /w/W-16 (v7 /tower·/plan 은 여기로 이동)
        ...group, // 26.10.21 병합: scargo_web 그룹 랜딩(/group)
        ...center, // 26.10.23 (share)
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
    if (user?.userType === 'ADMIN' && user?.adminScope === 'SBOT' && !isMaster(user)) { // 26.10.29 마스터(admin01)는 모든 화면 입장 가능
        const p = to.path;
        if (p !== '/' && p !== '/login' && p !== '/w/W-01' && !p.startsWith('/sbot')) return next({ name: 'sbot' });
    }


    // v13: 로그인한 상태에서 로그인 화면으로 오면 권한별 첫 화면으로
    if (user && to.name === 'login') return next(homeOf(user));
    // v13: 승인 대기 운송사는 승인 대기 화면 + 홈·공지만
    if (to.meta.pendingOnly && user?.userType !== 'CORPORATE_PENDING') return next(homeOf(user));
    if (user?.userType === 'CORPORATE_PENDING' && (to.meta.wScreen || to.path.startsWith('/driver') || to.path.startsWith('/company') || to.path.startsWith('/admin'))) return next('/pending');
    // v13: 기사 화면은 기사 계정만 (관제사·운송사·관리자가 기사 화면으로 가던 문제)
    if (to.path.startsWith('/driver') && user && !DRIVER_TYPES.includes(user.userType)) return next(homeOf(user));
    // 26.10.29 지선 운송기사: 웹 기사 화면은 컨테이너 기사용이라 데이터가 없음(서버 403) → 앱 안내 화면
    if (to.path.startsWith('/driver') && user?.userType === 'BRANCH_DRIVER') return next('/app-only');

    // 26.10.21 v9: W 화면은 로그인 계정 권한으로만 (비로그인 시연 모드·권한 전환 삭제). 비로그인은 로그인 화면으로
    if (to.meta.wScreen && !user && to.meta.sid !== 'W-01') { sessionStorage.setItem('location', to.fullPath); return next('/w/W-01'); }
    if (to.meta.wScreen && user) {
        if (user.userType === 'CONTAINER_DRIVER' || user.userType === 'BRANCH_DRIVER') { alert('기사 계정은 앱을 이용해 주세요.'); return next({ name: 'home' }); }
        const r = webRole(user);
        if (!r) { alert('웹 관제 권한이 없는 계정입니다.'); return next({ name: 'home' }); }
        if (to.meta.sid === 'W-01') return next('/w/' + HOME[r]);
        if (!canSee(r, to.meta.sid)) { alert('이 권한으로는 볼 수 없는 화면입니다.'); return next('/w/' + HOME[r]); }
    }
    // 26.10.26 총관리자 첫 화면은 카르고 대시보드(/admin) 그대로 (통합 관제는 사이드바 "관제 현황" 메뉴로)

    if (user?.userType === 'ADMIN') {
        if (to.name === 'notice') return next({ name: 'admin-notices' });
        if (to.name === 'notifications') return next({ name: 'admin-notifications' }); // 26.10.28 알림 내역은 관리자 화면 안에서
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
        if (user.userType !== 'ADMIN') { // 26.10.23: 총관리자(S Cargo 관리자)도 S-Bot 사용 가능
            // 26.10.22 (share): 허브 관제를 쓸 수 있는 관제사(CONTROL · HUB_OP)는 알림 없이 허브 관제로 보낸다
            if (hubAccess(user).canView) return next({ name: 'sbot-hub' });
            alert('관리자 계정만 접근할 수 있습니다.');
            return next({ name: 'group' });
        }
    }

    // 26.10.22 (share): 허브 AMR 관제(/sbot/hub)는 S-Bot 관리자 · CONTROL(조회 전용) · HUB_OP(소속 허브)
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

    // 26.10.23 (share): 지역센터 화면(/center/**): 지역센터 관제사(CENTER_OP)만. ADMIN 은 /admin/center-receipts 사용
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

    // 26.10.20 컨트롤타워: 로그인한 계정은 허용 권한만 (비로그인은 목업 시연 모드로 진입)
    const towerRoles = to.matched.find((r) => r.meta.towerRoles)?.meta.towerRoles
    if (towerRoles && user && !towerRoles.includes(user.userType)) {
        alert('컨트롤타워 권한이 없는 계정입니다.');
        return next({ name: 'home' });
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
