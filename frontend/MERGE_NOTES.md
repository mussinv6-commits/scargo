# S카고(scargo) 프론트엔드 통합본 (merged)

`project-sample-vue-final.zip` 을 기준으로 `project-sample-vue_hs_.zip` 의
배차 매핑 기능을 통합하고, 관리자/사업자 화면을 새로 만들어 얹은 버전입니다.

## 1. 메인 메뉴 구조

로그인한 계정의 `userType` 값에 따라 상단 내비게이션에 아래 3개 메인 메뉴 중
해당하는 것만 노출됩니다. (App.vue 참고)

### 관리자 (`userType === 'ADMIN'`) → `/admin/*`
- 대시보드
- 회원 관리 (전체 회원 조회 + 기업회원 승인)
- 업체 관리 (등록/수정/삭제)
- 차량 관리 (등록/수정/삭제/상태)
- 컨테이너 관리 (등록/수정/삭제)
- 야드 관리 (등록/수정/삭제)
- 적재 위치 관리 (야드별 섹터 등록/수정/삭제)
- 적재 기록 조회 (페이지네이션 + 삭제)
- 과적 검사 관리 (등록/수정/삭제)
- 게시판 관리 → 기존 게시판 화면으로 연결

### 사업자 (`userType === 'CORPORATE_APPROVED'`) → `/company/*`
- 업체 정보 (업체 정보 + 소속 차량 목록)
- 차량-컨테이너 매핑 (구 hs 프로젝트의 MappingView를 이식/재스타일링)

  ※ 승인 대기 중(`CORPORATE_PENDING`)인 계정은 메뉴 대신
    "사업자(승인대기)" 안내만 표시됩니다.

### 화물차 기사 (`userType === 'GENERAL'`) → `/driver/app/*`
- 운송현황 / 배차목록 / 정산·매출 / MY차량
- 기존 vue-final 의 4개 화면을 그대로 유지했습니다 (수정 없음).

### 공통 메뉴
- 홈, 게시판(게시판/게시판2), 야드 날씨(WeatherCalendar)

## 2. 두 프론트엔드 병합 내역

| 항목 | 처리 |
|---|---|
| 기준 프로젝트 | `project-sample-vue-final` (더 최신, 기사앱/업체화면/디자인 시스템 보유) |
| hs 프로젝트에서 가져온 것 | `MappingView.vue` → `src/components/company/CompanyMapping.vue` 로 이식, Tailwind 클래스(미설치 상태라 실제로는 적용 안 되고 있었음) → 프로젝트 자체 CSS로 재작성 |
| 라우터 | `router/company/company.js` 에 `/company`(정보) · `/company/mapping`(매핑) 두 자식 라우트로 재구성 |
| 신규 | 관리자 화면 전체(`src/components/admin/**`), 관리자 라우트(`router/admin/admin.js`) |
| 접근 제어 | `router/index.js` 의 `beforeEach` 가드 하나로 관리자(`requiresAdmin`)/사업자(`requiresCorporate`)/기사(`requiresDriverAuth`) 3가지를 통합 처리 |

## 3. CSS 통일 작업

- `components/CSS/theme.css` (네이비 `#0a2540` + 오렌지 `#ff6b00`) 를 유일한
  브랜드 팔레트로 삼고, 나머지 화면을 여기에 맞췄습니다.
- `components/CSS/admin.css` : 관리자 전용 사이드바/테이블/모달 디자인
  시스템을 신규 작성 (다크 테마인 `tokens.css`(기사앱)와 동일한 패턴으로
  `.admin-shell` 스코프 안에서만 변수를 재정의해 다른 화면과 충돌하지 않게 함).
- `components/CSS/company.css` : 사업자 탭/카드/매핑 화면 스타일 신규 작성.
- `components/CSS/unify.css` (신규) : 부트스트랩 기본 파랑 버튼/포커스/페이지네이션
  색상을 브랜드 컬러로 오버라이드 → 게시판 등 기존 부트스트랩 화면도 톤이 통일됩니다.
- `src/style.css` : Vite 스캐폴딩이 만든 보라색 다크모드 변수 세트를 걷어내고
  최소 리셋만 남겼습니다 (테마 색상과 변수명이 겹쳐 혼선을 줄 수 있었음).

## 4. 진행하며 고친 기존 버그

- `company.vue`(구) 가 존재하지 않는 API(`/api/companies/id/{id}`,
  `/api/trucks/company/{id}`)를 호출하고 있어 실제 백엔드 경로
  (`/api/companies/{companyId}`, `/api/trucks/options?companyId=`)로 수정했습니다.
  단, options API는 필드가 적어(차량번호/차종/세미트레일러 여부) 트레일러 번호·
  최대적재중량 등은 표시하지 못합니다 (관리자 화면에서는 전체 조회 가능).
- `router/weather/weather.js` 가 `component: weather` (정의되지 않은 값)로
  되어 있던 오타를 `WeatherCalendar` 컴포넌트로 수정하고 라우터에 연결했습니다.

## 5. 백엔드 관련 주의사항 (프론트 범위 밖이라 코드는 수정하지 않았습니다)

1. **세션 기반 권한 문제**: `AccountService.login()` 이 `HttpSession` 에
   `accountId` 만 저장할 뿐 Spring Security의 `SecurityContext` 를 채우지
   않습니다. 그 상태에서 여러 컨트롤러가 `@PreAuthorize("hasRole('ADMIN')")` 등을
   쓰고 있어서, 실제로는 인증 주체가 없어 관리자 전용 API가 항상 401/403을
   반환할 가능성이 높습니다. 관리자 화면이 정상 동작하려면 로그인 성공 시
   `SecurityContextHolder` 에 권한을 심어주는 처리가 백엔드에 추가로 필요합니다.
2. **기사 화면(운송현황/배차목록/정산·매출)이 참조하는 API 중 일부
   (`/api/dispatches/**`, `/api/settlements/**`, `/api/loading-records/vehicle/**`)는
   현재 제공된 백엔드(scargo)에 대응하는 컨트롤러가 없습니다.** 프론트가
   백엔드보다 앞서 만들어진 화면으로 보이며, 이번 병합 범위에서는 그대로
   유지만 하고 손대지 않았습니다.
3. `CompanyResponse`(목록 조회 DTO)에 `companyId` 필드가 빠져 있어서, 관리자
   업체관리 화면은 `/api/companies/options` 로 ID를 먼저 얻은 뒤 상세를 N+1로
   조회하는 방식으로 우회했습니다. 백엔드에서 `companyId` 필드를 추가해주시면
   더 단순해집니다.

## 6. 실행 방법

```bash
npm install
npm run dev
```

백엔드(scargo, Spring Boot)는 `http://localhost:8080` 에서 별도로 실행되어야 하며,
`SecurityConfig` 의 CORS 허용 origin(`http://localhost:5173`)과 맞아야 합니다.

## 7. 공지사항(notice) 기능 추가 병합

별도로 전달된 공지사항 게시판(목록/상세/작성/수정 + 홈 상단 흐르는 띠)을
이 프로젝트에 이식했습니다.

| 항목 | 처리 |
|---|---|
| 컴포넌트 | `src/components/notice/` (noticeList/noticeDetail/noticeUpdate/noticeWrite/noticeSlide) |
| 라우터 | `src/router/notice/notice.js` 신규 작성, `router/index.js`에 등록 (`/notice`, `/notice/:id`, `/notice/update/:id`, `/notice/write`) |
| CSS | 원본에서 분리되어 있던 `noticeList.css`/`noticeWrite.css`/`noticeSlide.css`를 각 컴포넌트에 `<style scoped src="...">`로 연결 (원본 .vue 파일에는 연결이 안 되어 있었음) |
| 버그 수정 | `noticeUpdate.vue`, `noticeSlide.vue`에서 라우트 이름이 `noticedetail`(소문자)로 잘못 참조되던 것을 실제 라우트명인 `noticeDetail`로 수정, `noticeSlide.vue`의 파라미터 키도 `seq` → `id`로 수정 |
| 노출 위치 | `App.vue` 공통 메뉴에 "공지사항" 링크 추가, `home.vue` 상단에 `<NoticeSlide />` 배치 |

**주의**: 공지사항 컴포넌트들은 기존 scargo 백엔드(`:8080`)가 아니라
`http://localhost:3000/api/v1/posts...` 를 호출하도록 하드코딩되어 있습니다.
이 부분은 프론트 구조 병합 범위 밖이라 그대로 두었으니, 실제 사용하시려면
백엔드 주소를 프로젝트의 `API_BASE`(`src/utils/apiBase.js`)에 맞게 수정하거나
해당 API를 제공하는 백엔드를 `:3000`에 별도로 띄워주셔야 합니다.
