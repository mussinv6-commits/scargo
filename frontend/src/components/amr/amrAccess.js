// ─────────────────────────────────────────────────────────────
// 26.10.22 허브 AMR 관제 화면 권한 (라우터 · S그룹 메인 · 관제 화면이 같이 쓴다)
//  백엔드 AmrAccessPolicy 와 같은 규칙:
//   ADMIN     전체 허브 조회 · 관리
//   CONTROL   전체 허브 조회만 (시뮬레이터 실행 · 저장 · 변경 불가)
//   HUB_OP    소속 허브(hubId)만 조회 · 관리
//   그 외     접근 불가 (CENTER_OP 포함)
//  26.10.22 통합: S Cargo 관리자와 S-Bot 관리자 화면 분리는 유지 → ADMIN 은 S-Bot 관리자(adminScope SBOT)만 허브 관제 화면 사용
//  ※ 화면 표시용 판단일 뿐이고, 실제 차단은 서버가 한다 (403)
// ─────────────────────────────────────────────────────────────

// 26.10.26 마스터 = S그룹 CEO 통합 마스터 계정(admin01). 아이디 필드명이 달라도 값으로 판별한다
export function isMaster(user) {
  return !!user && user.userType === 'ADMIN' && Object.values(user).some((v) => typeof v === 'string' && v.trim().toLowerCase() === 'admin01')
}

export function hubAccess(user) {
  if (!user) return { canView: false, canManage: false, reason: '로그인 후 이용할 수 있습니다.' }
  switch (user.userType) {
    case 'ADMIN':
      // 26.10.24: 마스터 관리자(admin01 등 S Cargo 관리자)도 S-Bot 을 사용할 수 있다 (서버도 ADMIN 전체 허용)
      // 26.10.26 마스터 계정은 '마스터님 · 전체'로 표시 (관리자님 · 관리자 · 전체 허브처럼 겹쳐 보이던 문제). 어느 화면에서든 같은 값이라 '허브'는 뺌
      return { canView: true, canManage: true, allHubs: true, hubId: null, label: '전체' }
    case 'CONTROL':
      return { canView: true, canManage: false, allHubs: true, hubId: null, label: '통합 관제사 · 전체 허브 조회 전용' }
    case 'HUB_OP':
      if (user.hubId == null) return { canView: false, canManage: false, reason: '소속 허브가 지정되지 않은 허브 관제사 계정입니다. 관리자에게 문의하세요.' }
      return { canView: true, canManage: true, allHubs: false, hubId: user.hubId, label: '허브 관제사 · 소속 허브' }
    default:
      return { canView: false, canManage: false, reason: '허브 AMR 관제는 S-Bot 관리자 · 통합 관제사 · 허브 관제사만 이용할 수 있습니다.' }
  }
}

// S-Bot 관리자 = 대시보드 · 택배 이동 · 물류센터까지 모두 사용 (관제사는 허브 관제만)
export function isSbotAdmin(user) {
  return !!user && user.userType === 'ADMIN'
}
