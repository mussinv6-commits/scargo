<template>
  <div class="screen">
    <header class="topbar" style="padding: calc(var(--safe-t) + 16px) 20px 12px"><h1 style="font-size: 22px">내정보</h1></header>
    <main class="content">
      <section class="card">
        <div class="between">
          <div class="stack" style="gap: 2px">
            <b style="font-size: 18px">{{ me?.userName || '-' }}{{ isDriver ? ' 기사님' : '님' }}</b>
            <span class="small muted">{{ me?.userId }} · {{ me?.companyName || '소속 없음' }}</span>
            <span class="small muted">{{ ({ CONTAINER_DRIVER: '컨테이너 운송기사', BRANCH_DRIVER: '지선 운송기사', HUB_OP: '허브 관제사' })[me?.userType] || '' }}{{ me?.phoneNum ? ' · ' + me.phoneNum : '' }}</span>
          </div>
          <Icon name="user" size="30" />
        </div>
      </section>

      <section v-if="isDriver" class="card"><!-- 26.10.29 허브 관제사는 차량 · 차량 아이콘 항목 없음 -->
        <div class="small muted" style="font-weight: 600">배정 차량</div>
        <template v-if="truck">
          <div class="between" style="align-items: baseline">
            <span style="font-size: 22px; font-weight: 700">{{ truck.vehicleNo }}</span>
            <span class="small muted">{{ truck.semiTrailer ? '세미트레일러' : truck.truckType || '' }}</span>
          </div>
          <div class="grid3 small muted">
            <div class="stack" style="gap: 2px">축수<b style="font-size: 15px; color: var(--ink)">{{ spec?.axleCount ?? '-' }}축</b></div>
            <div class="stack" style="gap: 2px">최대적재<b style="font-size: 15px; color: var(--ink)">{{ kg(spec?.maxPayloadKg) }}</b></div>
            <div class="stack" style="gap: 2px">공차중량<b style="font-size: 15px; color: var(--ink)">{{ kg(spec?.emptyWeightKg) }}</b></div>
          </div>
          <div class="small faint">{{ spec ? '제원 출처: 번호판 제원 조회 데이터' : '차량 제원이 등록되지 않았습니다' }}{{ truck.trailerNo ? ' · 트레일러 ' + truck.trailerNo : '' }}</div>
        </template>
        <div v-else class="small muted">배정된 차량이 없습니다.</div>
      </section>

      <section v-if="isDriver" class="card" style="gap: 10px">
        <div class="stack" style="gap: 2px"><b style="font-size: 14px">지도 위 내 차량 아이콘</b><span class="small muted">내비 화면에서 내 위치를 이 모양으로 보여줍니다</span></div>
        <VehicleIconPicker />
      </section>

      <!-- 26.10.24: 화면 모드 (자동 = 해 지면 다크) -->
      <section class="card" style="gap: 10px">
        <div class="stack" style="gap: 2px"><b style="font-size: 14px">화면 모드</b>
          <span class="small muted">지금 {{ theme.active === 'dark' ? '다크' : '라이트' }} 모드<template v-if="theme.mode === 'auto'"> · 오늘 일몰 {{ hhmm(theme.sunset) }} → 다크, 일출 {{ hhmm(theme.sunrise) }} → 라이트</template></span>
        </div>
        <div class="modes" role="radiogroup" aria-label="화면 모드">
          <button v-for="m in THEME_MODES" :key="m.id" type="button" class="mode" :class="{ on: theme.mode === m.id }" role="radio" :aria-checked="theme.mode === m.id" @click="setThemeMode(m.id)">
            <b>{{ m.name }}</b><span>{{ m.desc }}</span>
          </button>
        </div>
      </section>

      <!-- 26.10.24: 내비 시연 주행(배속 선택 → 경로 따라 차량 이동) 버튼 표시 -->
      <section class="card">
        <label class="between" style="cursor: pointer">
          <span class="stack" style="gap: 2px"><b style="font-size: 14px">시연 주행 버튼</b><span class="small muted">내비에서 배속(1~100배)을 골라 경로를 따라 차량을 움직입니다. 발표·테스트용</span></span>
          <input type="checkbox" class="switch" :checked="session.simButton" @change="setSimButton($event.target.checked)" />
        </label>
      </section>

      <!-- fix_app_1026: 서버 주소 설정 (ServerSheet) -->
      <section class="card" style="gap: 8px">
        <div class="between">
          <div class="stack" style="gap: 2px; min-width: 0"><b style="font-size: 14px">서버 주소</b><span class="small muted" style="word-break: break-all">{{ base || '개발 프록시' }}</span></div>
          <button class="btn sm" type="button" @click="session.serverSheet = true">변경</button>
        </div>
      </section>

      <section class="card" style="gap: 8px">
        <button class="btn" type="button" @click="$router.push('/history')">운행 이력 보기</button>
        <button class="btn" type="button" @click="$router.push('/settings')">앱 설정</button>
        <button class="btn" type="button" @click="$router.push('/inspection-history')">점검 이력</button>
      </section>

      <section class="card" style="gap: 10px">
        <div class="stack" style="gap: 2px"><b style="font-size: 14px">비밀번호 변경</b><span class="small muted">현재 비밀번호 확인 후 새 비밀번호로 바꿉니다</span></div>
        <input v-model="pw.cur" class="input" type="password" placeholder="현재 비밀번호" autocomplete="current-password" />
        <input v-model="pw.next" class="input" type="password" placeholder="새 비밀번호 (4자 이상)" autocomplete="new-password" />
        <input v-model="pw.next2" class="input" type="password" placeholder="새 비밀번호 확인" autocomplete="new-password" />
        <p v-if="pw.msg" class="small" :style="pw.ok ? 'color:#166534' : 'color:#C62828'">{{ pw.msg }}</p>
        <button class="btn" type="button" :disabled="pw.busy" @click="changePw">{{ pw.busy ? '변경 중…' : '비밀번호 변경' }}</button>
      </section>

      <button class="btn" type="button" style="color:#C62828;font-weight:700" @click="doLogout">로그아웃</button>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import VehicleIconPicker from '../components/VehicleIconPicker.vue'
import { getApiBase, isNative, Api, errText } from '../lib/api'
import { kg } from '../lib/format'
import { logout, refreshMe, session, setSimButton } from '../lib/session'
import { hhmm, setThemeMode, theme, THEME_MODES } from '../lib/theme'

const router = useRouter()
const me = computed(() => session.me)
const isDriver = computed(() => ['CONTAINER_DRIVER', 'BRANCH_DRIVER'].includes(session.me?.userType)) // 26.10.29 허브 관제사는 '님' · 차량 항목 숨김
const truck = computed(() => session.me?.truck)
const spec = computed(() => session.me?.truck?.spec)
const sheet = ref(false)
const base = computed(() => (session.serverSheet, getApiBase())) // fix_app_1026
const version = '0.2.0'

onMounted(() => refreshMe().catch(() => {}))

const pw = reactive({ cur: '', next: '', next2: '', busy: false, msg: '', ok: false })
async function changePw() {
  pw.msg = ''; pw.ok = false
  if (!pw.cur || !pw.next) { pw.msg = '현재·새 비밀번호를 입력하세요.'; return }
  if (pw.next.length < 4) { pw.msg = '새 비밀번호는 4자 이상이어야 합니다.'; return }
  if (pw.next !== pw.next2) { pw.msg = '새 비밀번호 확인이 일치하지 않습니다.'; return }
  pw.busy = true
  try {
    await Api.changePassword(pw.cur, pw.next)
    pw.ok = true; pw.msg = '비밀번호를 변경했습니다.'; pw.cur = ''; pw.next = ''; pw.next2 = ''
  } catch (e) { pw.msg = errText(e) } finally { pw.busy = false }
}

async function doLogout() {
  await logout()
  router.replace('/login')
}
</script>

<style scoped>
/* 26.10.24: 켜기/끄기 스위치 */
.switch { appearance: none; -webkit-appearance: none; flex-shrink: 0; width: 48px; height: 28px; border-radius: 14px; background: var(--line-strong); position: relative; cursor: pointer; transition: background .15s; margin: 0; }
.switch::after { content: ''; position: absolute; top: 3px; left: 3px; width: 22px; height: 22px; border-radius: 11px; background: #fff; box-shadow: 0 1px 2px rgba(0,0,0,.3); transition: left .15s; }
.switch:checked { background: var(--primary); }
.switch:checked::after { left: 23px; }
.modes { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
.mode { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; padding: 10px 12px; border: 1.5px solid var(--line-strong); border-radius: 12px; background: var(--surface); color: var(--ink); cursor: pointer; text-align: left; }
.mode b { font-size: 14px; }
.mode span { font-size: 12px; color: var(--muted); }
.mode.on { border-color: var(--primary); background: var(--primary-soft); }
.switch:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
</style>


