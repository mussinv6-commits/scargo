<template>
  <div class="topbar">
    <div>
      <div class="brand">MY/차량</div>
      <div class="sub">내 정보와 차량을 관리하세요</div>
    </div>
  </div>

  <main class="page">
    <div class="layout-2col">
    <div class="col-main">
    <!-- 프로필 (실제 로그인 계정 정보) -->
    <div class="profile-card">
      <div class="avatar">{{ initial }}</div>
      <div class="profile-body">
        <div class="profile-name">{{ user?.userName || '-' }}님</div>
        <div class="profile-phone">{{ user?.phoneNum || '연락처 미등록' }}</div>
        <div class="profile-badges">
          <span class="mini-badge">{{ userTypeLabel }}</span>
          <span class="mini-badge">ID {{ user?.userId }}</span>
        </div>
        <div class="info-row"><span>가입일</span><b>{{ memberSince }}</b></div>
      </div>
      <button class="edit-btn" @click="editProfile">수정</button>
    </div>

    <!-- 소속 업체 (companyId가 있는 계정만) -->
    <template v-if="user?.companyId">
      <div class="section-title">소속 업체</div>
      <div class="card company-card">
        <template v-if="company">
          <div class="info-row"><span>업체명</span><b>{{ company.companyName }}</b></div>
          <div class="info-row"><span>주소</span><b>{{ company.address }}</b></div>
        </template>
        <p v-else class="hint-text">업체 정보를 불러오는 중이거나 찾을 수 없습니다. (companyId: {{ user.companyId }})</p>
      </div>
    </template>

    <!-- 차량 정보 (26.09.21 수정: 고정형/지입차 모델로 변경 - 직접 등록/조회 대신 관리자가 배정한 차량만 표시) -->
    <div class="section-title" style="margin-top: 22px;">내 차량 정보</div>

    <div v-if="myTruck" class="card truck-card">
      <div class="info-row"><span>차량번호</span><b>{{ myTruck.vehicleNo }}</b></div>
      <div class="info-row"><span>차종</span><b>{{ myTruck.truckType || '-' }}</b></div>
      <div class="info-row"><span>세미트레일러</span><b>{{ myTruck.semiTrailer ? '예' : '아니오' }}</b></div>
      <div class="info-row" v-if="myTruck.semiTrailer">
        <span>트레일러 번호</span><b>{{ myTruck.trailerNo || '-' }}</b>
      </div>
      <div class="info-row"><span>최대 적재 중량</span><b>{{ myTruck.maxLoadWeight ?? '-' }} kg</b></div>
      <!-- 26.09.22 추가: 진입 허가 상태 - 불허 상태로 야드에 헛걸음하지 않도록 기사도 바로 확인 가능하게 -->
      <div class="info-row">
        <span>진입 허가</span>
        <b :class="entryApprovalClass">{{ entryApprovalLabel }}</b>
      </div>
      <p v-if="myTruck.entryApproval === 'REJECTED'" class="error-text">
        진입이 반려된 차량입니다. 소속 업체 또는 관리자에게 문의해주세요.
      </p>
      <p v-else-if="myTruck.entryApproval === 'PENDING'" class="hint-text">
        관리자 진입 허가 심사 대기 중입니다.
      </p>
    </div>

    <!-- 26.09.30 추가: 기사 화면에 차량 등록 기능이 없던 문제 → 배정 차량이 없으면 등록 신청 폼 표시 -->
    <div v-else id="register" class="card register-card">
      <template v-if="pendingVehicleNo">
        <p class="hint-text" style="margin-top:0;">
          <b>{{ pendingVehicleNo }}</b> 차량 등록을 신청했습니다.
          소속 업체에서 기사 배정을 완료하고 관리자 진입 허가가 나면 이 화면에 차량 정보가 표시됩니다.
        </p>
        <button type="button" class="btn-outline" @click="pendingVehicleNo = ''; savePending('')">다른 차량 등록하기</button>
      </template>
      <form v-else class="register-form" novalidate @submit.prevent="registerTruck">
        <p class="hint-text" style="margin-top:0;">
          아직 배정된 차량이 없습니다. 운행할 차량을 등록하면 소속 업체와 관리자에게 알림이 전달됩니다.
        </p>
        <label class="field">
          <span>차량 번호 <em>*</em></span>
          <input class="input" v-model.trim="reg.vehicleNo" placeholder="예: 12가3456" maxlength="20" />
        </label>
        <label class="field">
          <span>차종</span>
          <input class="input" v-model.trim="reg.truckType" placeholder="예: 카고 / 윙바디 / 트랙터" maxlength="30" />
        </label>
        <label class="field">
          <span>최대 적재 중량 (kg)</span>
          <input class="input" v-model="reg.maxLoadWeight" type="number" min="0" step="1" placeholder="예: 25000" @keydown="blockNegativeWeight" @input="clampWeight" />
        </label>
        <label class="check-row">
          <input type="checkbox" v-model="reg.isSemiTrailer" /> 세미트레일러(트랙터+트레일러) 차량입니다
        </label>
        <label v-if="reg.isSemiTrailer" class="field">
          <span>트레일러 번호 <em>*</em></span>
          <input class="input" v-model.trim="reg.trailerNo" placeholder="트레일러 번호판" maxlength="20" />
        </label>
        <p v-if="regMsg" class="error-text">{{ regMsg }}</p>
        <button type="submit" class="btn-fill" :disabled="registering">{{ registering ? '등록 중...' : '차량 등록 신청' }}</button>
      </form>
    </div>

    <!-- 나의 운행 이력 -->
    <template v-if="myTruck">
      <div class="section-title" style="margin-top: 22px;">나의 운행 이력</div>
      <div class="card">
        <table v-if="records.length" class="record-table">
          <thead>
            <tr><th>컨테이너</th><th>장소 ID</th><th>일시</th></tr>
          </thead>
          <tbody>
            <tr v-for="r in records" :key="r.recordId">
              <td>{{ r.containerNo }}</td>
              <td>{{ r.locationId }}</td>
              <td>{{ formatDate(r.loadedAt) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="hint-text">아직 운행 이력이 없습니다.</p>
      </div>
    </template>

    </div>

    <aside class="side-panel">
    <!-- 푸시 알림 설정 (※ 현재는 이 기기에만 저장되는 프런트 전용 설정입니다) -->
    <div class="section-title">푸시 알림 설정</div>
    <div class="card toggle-card">
      <div class="toggle-row">
        <div>
          <div class="toggle-label">배차 알림</div>
          <div class="toggle-desc">새 배차가 등록되면 즉시 알려드려요</div>
        </div>
        <label class="switch"><input type="checkbox" v-model="notif.dispatch"><span class="slider"></span></label>
      </div>
      <div class="toggle-row">
        <div>
          <div class="toggle-label">운행 상태 알림</div>
          <div class="toggle-desc">상/하차 시간 임박, 지연 등 운행 관련 안내</div>
        </div>
        <label class="switch"><input type="checkbox" v-model="notif.trip"><span class="slider"></span></label>
      </div>
      <div class="toggle-row">
        <div>
          <div class="toggle-label">정산·세금계산서 알림</div>
          <div class="toggle-desc">정산 완료, 세금계산서 발행 상태 안내</div>
        </div>
        <label class="switch"><input type="checkbox" v-model="notif.settlement"><span class="slider"></span></label>
      </div>
      <div class="toggle-row">
        <div>
          <div class="toggle-label">공지사항 알림</div>
          <div class="toggle-desc">서비스 점검, 정책 변경 등 주요 공지</div>
        </div>
        <label class="switch"><input type="checkbox" v-model="notif.notice"><span class="slider"></span></label>
      </div>
    </div>

    <button class="logout-btn" @click="logout">로그아웃</button>
    </aside>
    </div>
  </main>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { authState, clearLogin } from '@/auth/authState.js'
import { API_BASE } from '@/utils/apiBase.js'
import { fetchMyTruck as fetchAssignedTruck } from '@/utils/driverTruck.js'
import { friendlyError, withBoolAliases } from '@/utils/apiHelpers.js'
import { VEHICLE_NO_PATTERN, VEHICLE_NO_MESSAGE, normalizeVehicleNo } from '@/utils/validators.js'

const router = useRouter()

// ---- 로그인 사용자 (authState는 로그인/로그아웃 시 전역으로 갱신됨) ----
const user = computed(() => authState.user)
const initial = computed(() => user.value?.userName?.[0] || '?')
const memberSince = computed(() => {
  const d = user.value?.createdAt
  return d ? new Date(d).toLocaleDateString('ko-KR') : '-'
})
const userTypeLabel = computed(() => {
  const map = {
    GENERAL: '일반 회원',
    CORPORATE_PENDING: '기업 회원 (승인대기)',
    CORPORATE_APPROVED: '기업 회원 (승인완료)',
    ADMIN: '관리자',
  }
  return map[user.value?.userType] || user.value?.userType || '-'
})

// ---- 소속 업체 ----
// 주의: 백엔드에 companyId로 단건 조회하는 API가 없고(있는 건 /api/companies/{businessNo}뿐),
// /api/companies/options 로 전체 목록을 받아 companyId로 찾는 방식으로 대체합니다.
const company = ref(null)
async function fetchCompany() {
  if (!user.value?.companyId) return
  try {
    const resp = await axios.get(`${API_BASE}/api/companies/options`, { withCredentials: true })
    company.value = resp.data.find(c => c.companyId === user.value.companyId) || null
  } catch (err) {
    console.error(err)
  }
}

// ---- 26.09.21 수정: 고정형(지입차) 모델로 전환 ----
// 기사가 직접 차량번호를 검색/등록하지 않고, 관리자가 "기사 배정"한 차량을 그대로 보여준다.
// (구 GET /api/trucks/{vehicleNo} + POST /api/trucks 방식 → GET /api/trucks/my 로 단순화)
const myTruck = ref(null)

async function fetchMyTruck() {
  // 204 No Content(배정된 차량 없음) / 에러는 null
  myTruck.value = await fetchAssignedTruck()
  if (myTruck.value) {
    savePending('')
    await fetchRecords()
  }
}

// ---- 26.09.30 추가: 차량 등록 신청 (POST /api/trucks) ----
// 등록 후 기사 배정은 사업자(기사 관리 메뉴), 진입 허가는 관리자가 처리한다.
const reg = reactive({ vehicleNo: '', truckType: '', maxLoadWeight: '', isSemiTrailer: false, trailerNo: '' })
function blockNegativeWeight(event) {
  if (event.key === '-' || event.key === 'Subtract') {
    event.preventDefault()
    return
  }
  if (event.key !== 'ArrowDown') return
  const current = event.target.value === '' ? 0 : Number(event.target.value)
  if (Number.isNaN(current) || current <= 0) event.preventDefault()
}
function clampWeight() {
  if (reg.maxLoadWeight !== '' && Number(reg.maxLoadWeight) < 0) reg.maxLoadWeight = '0'
}
const regMsg = ref('')
const registering = ref(false)
const pendingKey = () => `scargo_pendingTruck_${user.value?.userId}`
const pendingVehicleNo = ref(localStorage.getItem(pendingKey()) || '')
function savePending(v) {
  try {
    if (v) localStorage.setItem(pendingKey(), v)
    else localStorage.removeItem(pendingKey())
  } catch (e) { /* 저장 실패는 무시 */ }
}

async function registerTruck() {
  regMsg.value = ''
  const vehicleNo = normalizeVehicleNo(reg.vehicleNo)
  if (!vehicleNo) return (regMsg.value = '차량 번호를 입력해주세요.')
  if (!VEHICLE_NO_PATTERN.test(vehicleNo)) return (regMsg.value = VEHICLE_NO_MESSAGE)
  if (reg.isSemiTrailer && !reg.trailerNo) return (regMsg.value = '트레일러 번호를 입력해주세요.')
  if (reg.maxLoadWeight !== '' && Number(reg.maxLoadWeight) <= 0) return (regMsg.value = '최대 적재 중량은 0보다 커야 합니다.')
  if (!user.value?.companyId) return (regMsg.value = '소속 업체 정보가 없어 등록할 수 없습니다. 관리자에게 문의해주세요.')

  registering.value = true
  try {
    await axios.post(
      `${API_BASE}/api/trucks`,
      withBoolAliases(
        {
          vehicleNo,
          companyId: user.value.companyId,
          isSemiTrailer: reg.isSemiTrailer,
          trailerNo: reg.isSemiTrailer ? reg.trailerNo : null,
          truckType: reg.truckType || null,
          maxLoadWeight: reg.maxLoadWeight === '' ? null : Number(reg.maxLoadWeight),
        },
        ['isSemiTrailer']
      ),
      { withCredentials: true }
    )
    pendingVehicleNo.value = vehicleNo
    savePending(vehicleNo)
    Object.assign(reg, { vehicleNo: '', truckType: '', maxLoadWeight: '', isSemiTrailer: false, trailerNo: '' })
    await fetchMyTruck()
  } catch (err) {
    regMsg.value = `차량 등록에 실패했습니다. ${friendlyError(err)}`
  } finally {
    registering.value = false
  }
}

// 26.09.22 추가: 진입 허가 상태 표시용
const entryApprovalLabel = computed(() => {
  return { PENDING: '심사대기', APPROVED: '허가', REJECTED: '반려' }[myTruck.value?.entryApproval] || '심사대기'
})
const entryApprovalClass = computed(() => {
  if (myTruck.value?.entryApproval === 'APPROVED') return 'text-approved'
  if (myTruck.value?.entryApproval === 'REJECTED') return 'text-rejected'
  return 'text-pending'
})

// ---- 운행 이력 (매핑/게이트 통과 기록) ----
const records = ref([])

async function fetchRecords() {
  if (!myTruck.value) return
  try {
    // 26.09.30 수정: 실제 백엔드 경로는 /api/loading-records/truck/{vehicleNo} (Page 응답)
    const resp = await axios.get(`${API_BASE}/api/loading-records/truck/${encodeURIComponent(myTruck.value.vehicleNo)}`, {
      params: { page: 0, size: 20 },
      withCredentials: true,
    })
    records.value = resp.data?.content || []
  } catch (err) {
    records.value = []
  }
}

function formatDate(d) {
  return d ? new Date(d).toLocaleString() : '-'
}

// ---- 푸시 알림 설정 (프런트 전용, localStorage에만 저장 - 백엔드 API 없음) ----
const notif = reactive({
  dispatch: true,
  trip: true,
  settlement: true,
  notice: false,
})

watch(
  notif,
  (v) => {
    if (user.value?.userId) {
      localStorage.setItem(`scargo_notif_${user.value.userId}`, JSON.stringify(v))
    }
  },
  { deep: true }
)

function editProfile() {
  alert('회원정보 수정 API는 아직 백엔드에 없습니다. (준비 중)')
}
function logout() {
  if (!confirm('로그아웃 하시겠습니까?')) return
  axios
    .post(`${API_BASE}/api/accounts/logout`, {}, { withCredentials: true })
    .catch(() => {}) // 백엔드에 logout API가 없어도 클라이언트 쪽은 로그아웃 처리
    .finally(() => {
      clearLogin()
      router.push('/login')
    })
}

// ---- 초기 로딩 ----
onMounted(async () => {
  if (!user.value) {
    alert('로그인이 필요합니다.')
    router.push('/login')
    return
  }

  const savedNotif = localStorage.getItem(`scargo_notif_${user.value.userId}`)
  if (savedNotif) Object.assign(notif, JSON.parse(savedNotif))

  await fetchCompany()

  // 26.09.21 수정: localStorage에 저장해둔 차량번호로 재조회하던 방식 →
  // 관리자가 배정한 차량을 세션 기준으로 바로 조회하는 방식으로 변경
  await fetchMyTruck()
})
</script>

<style scoped>
.page { padding: 28px 32px; }

/* 프로필 */
.profile-card {
  display: flex; align-items: center; gap: 14px;
  background: var(--surface); border: 1px solid var(--border);
  border-radius: var(--radius); padding: 16px; margin-bottom: 18px;
}
.avatar {
  width: 52px; height: 52px; border-radius: 50%;
  background: var(--amber-soft); color: var(--amber);
  display: flex; align-items: center; justify-content: center;
  font-family: 'Barlow Condensed', sans-serif; font-size: 22px; font-weight: 700; flex-shrink: 0;
}
.profile-body { flex: 1; min-width: 0; }
.profile-name { font-size: 16px; font-weight: 700; }
.profile-phone { font-size: 13px; color: var(--text-muted); margin-top: 2px; }
.profile-badges { display: flex; gap: 6px; margin-top: 8px; flex-wrap: wrap; }
.mini-badge { font-size: 11.5px; color: var(--text-muted); background: var(--surface-alt); padding: 3px 8px; border-radius: 999px; }
.edit-btn {
  padding: 8px 14px; border-radius: 8px; border: 1px solid var(--border);
  background: transparent; color: var(--text); font-size: 13px; font-weight: 600; cursor: pointer; flex-shrink: 0;
}

.company-card, .truck-card, .lookup-card { margin-bottom: 4px; }

.info-row { display: flex; justify-content: space-between; padding: 8px 0; font-size: 14px; border-bottom: 1px solid var(--border); }
.info-row:last-of-type { border-bottom: none; }
.info-row span { color: var(--text-muted); }
/* 26.09.21 추가: 값(<b>)에 색이 명시돼 있지 않아 특정 환경에서 배경색과 거의 안 구분되던 문제 수정 */
.info-row b { color: var(--text); font-weight: 600; }

.hint-text { color: var(--text-muted); font-size: 13px; }
.error-text { color: var(--red); font-size: 13px; margin-top: 4px; }
.register-form { display: flex; flex-direction: column; gap: 10px; }
.register-form .field { display: flex; flex-direction: column; gap: 4px; font-size: 12.5px; color: var(--text-muted); font-weight: 600; }
.register-form .field em { color: var(--amber); font-style: normal; }
.register-form .check-row { display: flex; align-items: center; gap: 8px; font-size: 13.5px; color: var(--text); cursor: pointer; }
/* 26.09.22 추가: 진입 허가 상태 색상 */
.text-approved { color: var(--green) !important; }
.text-rejected { color: var(--red) !important; }
.text-pending { color: var(--amber) !important; }

.lookup-row { display: flex; gap: 8px; margin: 8px 0; }
.input {
  padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px;
  font-size: 14px; flex: 1; background: var(--surface-alt); color: var(--text);
}
.input::placeholder { color: var(--text-muted); }

.truck-form { display: flex; flex-direction: column; gap: 10px; margin-top: 8px; }
.checkbox-row { display: flex; align-items: center; gap: 8px; font-size: 14px; }

.btn-outline, .btn-fill {
  padding: 10px 14px; border-radius: 8px; font-size: 13.5px; font-weight: 600; cursor: pointer; margin-top: 8px;
}
.btn-outline { background: transparent; border: 1px solid var(--amber); color: var(--amber); }
.btn-fill { border: none; background: var(--amber); color: #fff; width: 100%; }
.btn-outline:active, .btn-fill:active { transform: scale(0.98); }

.record-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.record-table th, .record-table td { padding: 8px; border-bottom: 1px solid var(--border); text-align: center; color: var(--text); }
.record-table th { color: var(--text-muted); font-weight: 600; }

/* 알림 토글 */
.toggle-card { display: flex; flex-direction: column; }
.toggle-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 13px 0; }
.toggle-row + .toggle-row { border-top: 1px solid var(--border); }
.toggle-label { font-size: 14.5px; font-weight: 600; }
.toggle-desc { font-size: 12px; color: var(--text-muted); margin-top: 3px; line-height: 1.4; }
.switch { position: relative; width: 44px; height: 26px; flex-shrink: 0; }
.switch input { opacity: 0; width: 0; height: 0; }
.slider { position: absolute; cursor: pointer; inset: 0; background: var(--surface-alt); border: 1px solid var(--border); border-radius: 999px; transition: background .2s ease; }
.slider::before { content: ""; position: absolute; width: 18px; height: 18px; left: 3px; top: 3px; background: var(--text-muted); border-radius: 50%; transition: transform .2s ease, background .2s ease; }
.switch input:checked + .slider { background: var(--amber-soft); border-color: var(--amber); }
.switch input:checked + .slider::before { transform: translateX(18px); background: var(--amber); }

.logout-btn { width: 100%; margin-top: 22px; padding: 14px; border-radius: var(--radius); border: 1px solid var(--border); background: transparent; color: var(--red); font-size: 14.5px; font-weight: 600; cursor: pointer; }
</style>