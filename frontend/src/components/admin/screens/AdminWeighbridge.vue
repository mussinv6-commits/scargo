<template>
  <div class="wb">
    <AdminPageHeader
      title="검사소 계량"
      description="게이트 OCR을 통과한 차량이 자동으로 대기열에 올라옵니다. 차량을 고르고 축중을 계측하면 서버가 과적을 판정해 과적 검사 기록으로 저장합니다."
    >
      <template #actions>
        <div class="wb-policy">
          <span class="wb-station">{{ policy.stationCode }}</span>
          <span>축하중 {{ kg(policy.axleLimitKg) }}kg 이하</span>
          <span>총중량 {{ kg(policy.grossLimitKg) }}kg 이하</span>
        </div>
      </template>
    </AdminPageHeader>

    <div class="wb-grid">
      <!-- ───────── 좌측: 계량 대기열 ───────── -->
      <aside class="wb-queue" aria-label="계량 대기 차량">
        <header class="wb-queue-head">
          <h2>계량 대기</h2>
          <span class="wb-count">{{ queue.length }}대</span>
          <span class="wb-live" :class="{ 'is-off': queueError }">
            <i class="wb-live-dot"></i>{{ queueError ? '연결 끊김' : '실시간' }}
          </span>
        </header>

        <!-- 26.10.01 추가: 자동 계량 - 켜두면 대기열 차량을 순서대로 계측·판정·저장까지 자동 진행 -->
        <div class="wb-auto" :class="{ 'is-on': autoMode }">
          <label class="wb-switch">
            <input type="checkbox" :checked="autoMode" @change="setAutoMode($event.target.checked)" />
            <span class="wb-switch-track" aria-hidden="true"><span class="wb-switch-thumb"></span></span>
            <span class="wb-switch-label">자동 계량</span>
          </label>
          <p class="wb-auto-status" aria-live="polite">{{ autoStatus }}</p>
        </div>

        <p v-if="queueError" class="wb-queue-note is-error">{{ queueError }}</p>

        <ul v-if="queue.length" class="wb-queue-list">
          <li v-for="item in queue" :key="item.gateLogId">
            <button
              type="button"
              class="wb-queue-item"
              :class="{ 'is-active': selected && selected.gateLogId === item.gateLogId }"
              @click="onUserSelect(item)"
            >
              <span class="wb-plate">{{ item.vehicleNo }}</span>
              <span class="wb-queue-meta">
                {{ item.companyName || '업체 미상' }} / {{ item.truckType || '차종 미등록' }}
              </span>
              <span class="wb-queue-time">
                {{ hhmm(item.passAt) }} {{ item.gateName || item.gateCode || '게이트' }} 통과
                <em>{{ elapsed(item.passAt) }}</em>
              </span>
            </button>
          </li>
        </ul>
        <div v-else-if="!queueLoading" class="wb-queue-empty">
          <p>대기 중인 차량이 없습니다.</p>
          <p>게이트 OCR에서 등록차량으로 확인되면 여기에 자동으로 올라옵니다.</p>
          <RouterLink to="/admin/gate-ocr" class="wb-link">게이트 OCR 화면 열기</RouterLink>
        </div>

        <form class="wb-manual" @submit.prevent="selectManual">
          <label for="wb-manual-plate">번호판 인식이 안 된 차량</label>
          <div class="wb-manual-row">
            <input
              id="wb-manual-plate"
              v-model.trim="manualPlate"
              placeholder="예: 경기90자8118"
              maxlength="20"
              autocomplete="off"
            />
            <button type="submit" :disabled="!manualPlate">직접 계량</button>
          </div>
        </form>
      </aside>

      <!-- ───────── 우측: 계량 콘솔 ───────── -->
      <section class="wb-console" aria-label="계량 콘솔">
        <div v-if="!selected" class="wb-console-empty">
          <svg viewBox="0 0 120 60" aria-hidden="true">
            <rect x="6" y="44" width="108" height="8" rx="2" />
            <rect x="20" y="18" width="62" height="22" rx="3" />
            <rect x="84" y="24" width="20" height="16" rx="3" />
            <circle cx="34" cy="42" r="6" />
            <circle cx="90" cy="42" r="6" />
          </svg>
          <p>왼쪽 대기열에서 계량할 차량을 고르세요.</p>
        </div>

        <template v-else>
          <!-- 차량 정보 -->
          <header class="wb-vehicle">
            <img
              v-if="selected.frontImageUrl"
              :src="selected.frontImageUrl"
              class="wb-photo"
              :alt="`${selected.vehicleNo} 게이트 촬영 사진`"
            />
            <span class="wb-plate wb-plate-lg">{{ selected.vehicleNo }}</span>
            <dl class="wb-vehicle-facts">
              <div>
                <dt>업체</dt>
                <dd>{{ selected.companyName || '-' }}</dd>
              </div>
              <div>
                <dt>차종</dt>
                <dd>{{ selected.truckType || '-' }}{{ selected.semiTrailer ? ' (세미트레일러)' : '' }}</dd>
              </div>
              <div>
                <dt>게이트 통과</dt>
                <dd>{{ selected.passAt ? hhmm(selected.passAt) : '직접 입력' }}</dd>
              </div>
              <div v-if="selected.plateConfidence != null">
                <dt>OCR 신뢰도</dt>
                <dd>{{ selected.plateConfidence }}%</dd>
              </div>
              <div v-if="reweighTarget">
                <dt>재계량</dt>
                <dd>기록 #{{ reweighTarget.checkId }} ({{ (reweighTarget.retryCount ?? 0) + 1 }}회차)</dd>
              </div>
            </dl>
          </header>

          <!-- 검사소 영상(WB-01) + DB 연동 축중 판독 카드 -->
          <div class="wb-stage">
            <div class="wb-frame">
              <video
                ref="videoEl"
                class="wb-video"
                :src="wbVideo"
                muted
                playsinline
                preload="auto"
                aria-label="검사소 WB-01 카메라 영상"
              ></video>
              <div class="wb-card" :class="{ 'is-show': cardShown }" aria-live="polite">
                <div class="wb-card-row1">
                  <span class="wb-card-dot"></span>DB 연동 축중 판독 · {{ policy.stationCode }}
                </div>
                <div class="wb-card-id">
                  {{ selected.vehicleNo
                  }}<span v-if="selected.containerNo" class="wb-card-sub">{{ selected.containerNo }}</span>
                </div>
                <div class="wb-card-axles">
                  <span v-for="(a, i) in axles" :key="i" :class="{ 'is-over': (a.weightKg || 0) > policy.axleLimitKg }">
                    {{ i + 1 }}축 <b>{{ tons(a.weightKg) }}</b
                    >t
                  </span>
                </div>
                <div class="wb-card-total">
                  <span class="wb-card-label">총중량</span>
                  <span class="wb-card-value" :class="{ 'is-over': displayTotal > policy.grossLimitKg }">
                    {{ displayTotal ? tons(displayTotal) : '0.0'
                    }}<small>t / 기준 {{ tons(policy.grossLimitKg) }}t</small>
                  </span>
                  <span v-if="phase === 'reading'" class="wb-card-spin"></span>
                </div>
                <div v-if="badge" class="wb-card-badge" :class="badge.tone">{{ badge.text }}</div>
              </div>
              <div v-if="phase === 'idle' && !result" class="wb-stage-hint">
                차량이 검사소에 올라오면 <b>축중 계측</b>을 누르세요
              </div>
            </div>
          </div>

          <!-- 축별 측정값 -->
          <div class="wb-readout">
            <div class="wb-axles">
              <div class="wb-axles-head">
                <label for="wb-axle-count">축 수</label>
                <select id="wb-axle-count" v-model.number="axleCount" :disabled="busy || !!reweighTarget">
                  <option v-for="n in [2, 3, 4, 5, 6]" :key="n" :value="n">{{ n }}축</option>
                </select>
                <span class="wb-source" :title="'축중기 장비가 연결되면 이 값이 장비 수신값으로 바뀝니다.'">
                  측정값 출처: 계측 시뮬레이터 또는 직접 입력
                </span>
              </div>

              <div class="wb-axle-rows">
                <div v-for="(a, i) in axles" :key="i" class="wb-axle-row" :class="axleState(i)">
                  <label :for="'wb-axle-' + i">{{ i + 1 }}축</label>
                  <div class="wb-axle-input">
                    <input
                      :id="'wb-axle-' + i"
                      v-model.number="a.weightKg"
                      type="number"
                      inputmode="numeric"
                      min="0"
                      max="60000"
                      step="10"
                      :disabled="busy"
                      placeholder="0"
                      @input="userAction(onManualEdit)"
                    />
                    <span>kg</span>
                  </div>
                  <div class="wb-axle-gauge" aria-hidden="true">
                    <span
                      :style="{ width: Math.min(100, ((a.weightKg || 0) / policy.axleLimitKg) * 100) + '%' }"
                    ></span>
                  </div>
                  <span class="wb-axle-pct">{{ axlePct(a.weightKg) }}%</span>
                </div>
              </div>

              <div class="wb-total-row" :class="{ 'is-over': enteredTotal > policy.grossLimitKg }">
                <span>합계</span>
                <strong>{{ kg(enteredTotal) }}kg</strong>
                <span class="wb-total-pct">총중량 기준 {{ kg(policy.grossLimitKg) }}kg 대비 {{ grossPct }}%</span>
              </div>
              <!-- 26.10.02 추가: 총중량에 컨테이너 무게가 포함된 구성 -->
              <p v-if="weightBreakdown" class="wb-breakdown">
                차량 {{ kg(weightBreakdown.truckTare) }}kg
                <template v-if="weightBreakdown.hasContainer">
                  + 컨테이너 자체 {{ kg(weightBreakdown.containerTare) }}kg
                </template>
                + 화물 {{ kg(weightBreakdown.cargo) }}kg
                <span v-if="weightBreakdown.hasContainer" class="wb-breakdown-vgm">
                  (컨테이너 총중량 VGM {{ kg(weightBreakdown.containerTare + weightBreakdown.cargo) }}kg)
                </span>
              </p>

              <!-- 26.10.01 변경: 컨테이너는 입력하지 않고 DB(차량 배정 → 최근 적재기록)에서 자동으로 가져옴 -->
              <div class="wb-container-row" :class="{ 'is-empty': !selected.containerNo }">
                <span class="wb-container-label">컨테이너</span>
                <template v-if="vehicleLoading">
                  <span class="wb-container-none">DB에서 조회 중...</span>
                </template>
                <template v-else-if="selected.containerNo">
                  <strong class="wb-container-no">{{ selected.containerNo }}</strong>
                  <span v-if="selected.containerSizeType || selected.containerType" class="wb-container-meta">
                    {{ [selected.containerSizeType, selected.containerType].filter(Boolean).join(' ') }}
                  </span>
                  <span v-if="selected.containerMaxGrossKg" class="wb-container-meta">
                    최대총중량 {{ kg(selected.containerMaxGrossKg) }}kg
                  </span>
                  <span v-if="selected.containerTareKg" class="wb-container-meta"
                    >자체중량 {{ kg(selected.containerTareKg) }}kg</span
                  >
                  <span class="wb-container-src">{{ containerSourceLabel(selected.containerSource) }}</span>
                </template>
                <span v-else class="wb-container-none">이 차량에 배정되거나 적재된 컨테이너가 없습니다</span>
              </div>
            </div>
          </div>

          <!-- 판정 결과 -->
          <div v-if="result" class="wb-verdict" :class="result.record.isPassed ? 'is-pass' : 'is-fail'" role="status">
            <div class="wb-verdict-main">
              <strong>{{ result.record.isPassed ? '통과' : '과적' }}</strong>
              <span>과적 검사 기록 #{{ result.record.checkId }} 저장됨 · {{ hhmmss(result.record.checkedAt) }}</span>
            </div>
            <ul v-if="result.violations.length" class="wb-verdict-list">
              <li v-for="(v, i) in result.violations" :key="i">
                {{ v.type === 'GROSS' ? '총중량' : v.type === 'PAYLOAD' ? '적재중량' : `${v.axleNo}축 축하중` }}
                {{ kg(v.measuredKg) }}kg (기준 {{ kg(v.limitKg) }}kg, {{ kg(v.measuredKg - v.limitKg) }}kg 초과)
              </li>
            </ul>
            <p v-if="!result.record.isPassed" class="wb-verdict-hint">
              업체에 과적 알림이 발송되었습니다. 감량 후 아래 기록에서 재계량하세요.
            </p>
          </div>
          <p v-else-if="previewViolations.length && phase === 'ready'" class="wb-preview is-fail">
            저장하면 과적으로 판정됩니다: {{ previewViolations.join(', ') }}
          </p>

          <p v-if="consoleError" class="wb-error" role="alert">{{ consoleError }}</p>

          <!-- 조작 버튼 -->
          <div class="wb-actions">
            <button type="button" class="wb-btn" :disabled="busy || !!result" @click="userAction(readFromScale)">
              {{ phase === 'reading' ? '계측 중...' : '축중 계측' }}
            </button>
            <button
              v-if="!result"
              type="button"
              class="wb-btn is-primary"
              :disabled="!canSave"
              @click="userAction(save)"
            >
              {{ phase === 'saving' ? '저장 중...' : reweighTarget ? '재계량 판정 후 저장' : '판정 후 저장' }}
            </button>
            <button v-else type="button" class="wb-btn is-primary" @click="userAction(nextVehicle)">
              {{ queue.length ? '다음 차량' : '계량 마침' }}
            </button>
            <button type="button" class="wb-btn is-ghost" :disabled="busy" @click="userAction(clearSelection)">
              취소
            </button>
          </div>
        </template>
      </section>
    </div>

    <!-- ───────── 오늘 계량 기록 ───────── -->
    <section class="wb-records">
      <header>
        <h2>오늘 계량 기록 · 재계량 대기</h2>
        <span class="wb-records-sum">
          {{ records.length }}건 / 통과 {{ records.filter((r) => r.isPassed).length }} / 과적
          {{ records.filter((r) => !r.isPassed).length }}
        </span>
      </header>
      <div class="wb-table-wrap">
        <table v-if="records.length" class="wb-table">
          <thead>
            <tr>
              <th scope="col" class="num">순서</th>
              <th scope="col">시각</th>
              <th scope="col">차량번호</th>
              <th scope="col">경로</th>
              <th scope="col" class="num">축수</th>
              <th scope="col" class="num">총중량</th>
              <th scope="col">판정</th>
              <th scope="col">사유</th>
              <th scope="col"><span class="sr-only">작업</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, idx) in records" :key="r.checkId">
              <td class="num seq">{{ records.length - idx }}</td>
              <td>{{ hhmm(r.checkedAt) }}</td>
              <td class="plate-cell">{{ r.vehicleNo }}</td>
              <td>{{ r.gateLogId ? '게이트 OCR' : '직접 입력' }}</td>
              <td class="num">{{ r.axleCount ?? '-' }}</td>
              <td class="num">{{ kg(r.totalWeight) }}kg</td>
              <td>
                <span class="wb-tag" :class="r.isPassed ? 'is-pass' : 'is-fail'">
                  {{ r.isPassed ? '통과' : '과적' }}{{ r.retryCount ? ` (재계량 ${r.retryCount})` : '' }}
                </span>
              </td>
              <td class="reason" :title="r.violationReason || ''">{{ r.violationReason || '-' }}</td>
              <td>
                <button v-if="!r.isPassed" type="button" class="wb-mini" :disabled="busy" @click="startReweigh(r)">
                  재계량
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="wb-records-empty">오늘 검사소에서 계량한 차량이 아직 없습니다.</p>
      </div>
    </section>

    <!-- 26.10.02 추가: 이동 경로 미니 플레이어 (유튜브 미니 플레이어처럼 화면 구석에 떠서, 게이트 인부터 이동을 보여줌) -->
    <div
      v-if="selected && miniOpen"
      class="wb-pip"
      :class="{ 'is-big': miniBig, 'is-dragging': dragging }"
      :style="miniPos ? { left: miniPos.x + 'px', top: miniPos.y + 'px', right: 'auto', bottom: 'auto' } : null"
      role="dialog"
      aria-label="차량 이동 경로"
    >
      <header class="wb-pip-bar" @pointerdown="startDrag">
        <span class="wb-pip-live" :class="routeStatus"></span>
        <strong class="wb-pip-plate">{{ selected.vehicleNo }}</strong>
        <span class="wb-pip-state" :class="routeStatus">{{ miniStateText }}</span>
        <span class="wb-pip-tools" @pointerdown.stop>
          <button type="button" title="처음부터 다시 보기" aria-label="처음부터 다시 보기" @click="routeMap?.replay()">
            <i class="bi bi-arrow-counterclockwise"></i>
          </button>
          <button
            type="button"
            :title="miniBig ? '작게 보기' : '크게 보기'"
            :aria-label="miniBig ? '작게 보기' : '크게 보기'"
            @click="miniBig = !miniBig"
          >
            <i :class="miniBig ? 'bi bi-fullscreen-exit' : 'bi bi-arrows-angle-expand'"></i>
          </button>
          <button type="button" title="닫기" aria-label="닫기" @click="miniOpen = false">
            <i class="bi bi-x-lg"></i>
          </button>
        </span>
      </header>
      <div class="wb-pip-body">
        <WeighbridgeRouteMap
          ref="routeMap"
          compact
          :route="routeData"
          :status="routeStatus"
          @phase="(p) => (routePhase = p)"
        />
      </div>
      <footer class="wb-pip-foot">
        <span>{{ routeData?.origin?.name || '진입 게이트' }}</span>
        <i class="bi bi-arrow-right"></i>
        <span class="is-wb">{{ routeData?.weighbridge?.name || '검사소' }}</span>
        <i class="bi bi-arrow-right"></i>
        <span>{{ routeData?.destination?.name || '목적지' }}</span>
      </footer>
    </div>
    <button v-else-if="selected" type="button" class="wb-pip-reopen" @click="miniOpen = true">
      <i class="bi bi-map"></i> 이동 경로 보기
    </button>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import WeighbridgeRouteMap from '@/components/admin/WeighbridgeRouteMap.vue' // 26.10.02 추가: 이동 경로 지도
import wbVideo from '@/assets/weighbridge/wb01.webm' // 검사소 WB-01 카메라 영상 (기존 데모 영상 그대로 사용)
import { adminApi, pickErrorMessage } from '@/utils/adminApi'

// 26.10.01 추가: 검사소 정식 화면
// 흐름: gate_api.py(번호판 OCR) → gate_logs 저장 → 이 화면 대기열 → 축중 계측 → 서버 판정 → overload_checks 저장

const POLL_MS = 3000
const reduceMotion = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

const policy = reactive({ stationCode: 'WB-01', axleLimitKg: 10000, grossLimitKg: 40000 })

// ---- 대기열 ----
const queue = ref([])
const queueLoading = ref(true)
const queueError = ref('')
let pollId = null

async function loadQueue() {
  try {
    const res = await adminApi.get('/api/v1/weighbridge/queue')
    queue.value = Array.isArray(res.data) ? res.data : []
    queueError.value = ''
  } catch (err) {
    const st = err?.response?.status
    // 26.10.01: 백엔드를 재시작하면 로그인 세션이 사라져 401/403 이 난다 → 원인을 바로 알 수 있게 안내
    queueError.value =
      st === 401 || st === 403
        ? '로그인이 만료되었습니다. 로그아웃 후 다시 로그인해 주세요. (백엔드를 재시작하면 로그인이 풀립니다)'
        : pickErrorMessage(err, '대기열을 불러오지 못했습니다.')
  } finally {
    queueLoading.value = false
  }
}

async function loadPolicy() {
  try {
    const res = await adminApi.get('/api/v1/weighbridge/policy')
    Object.assign(policy, res.data)
  } catch {
    /* 기본값(축 10t / 총 40t)으로 계속 진행 */
  }
}

// ---- 오늘 기록 ----
const records = ref([])
async function loadRecords() {
  try {
    const res = await adminApi.get('/api/v1/weighbridge/weighings/today')
    // 26.10.01: 검사소 콘솔에서 실시간으로 계량한 기록만 표시
    // (게이트 OCR로 넘어온 차량 = gateLogId, 콘솔 직접 계량 = stationCode 가 있음.
    //  과적 검사 관리 화면에서 손으로 넣은 테스트 데이터는 둘 다 없어서 제외됨)
    const list = Array.isArray(res.data) ? res.data : []
    // 26.10.06 추가: 오늘 기록만 보여서, 지난날 과적(미통과) 차량은 서버를 다시 켜면 재계량할 방법이 없던 문제
    // → 아직 통과 못 한 과적 기록은 날짜와 상관없이 함께 표시 (재계량 버튼 유지)
    let pending = []
    try {
      const f = await adminApi.get('/api/overload-checks/failed', { params: { page: 0, size: 100, sort: 'checkId,desc' } })
      const raw = Array.isArray(f.data?.content) ? f.data.content : []
      // 같은 차량은 가장 최근 과적 기록 1건만, 오늘 그 뒤에 다시 계량한 기록이 있으면 제외
      const newest = new Map()
      for (const r of list) {
        const k = String(r.vehicleNo || '').replace(/\s/g, '')
        newest.set(k, Math.max(newest.get(k) ?? 0, Number(r.checkId)))
      }
      const seen = new Set()
      pending = raw
        .sort((x, y) => Number(y.checkId) - Number(x.checkId))
        .filter((r) => {
          const k = String(r.vehicleNo || '').replace(/\s/g, '')
          if (seen.has(k)) return false
          seen.add(k)
          return (newest.get(k) ?? 0) <= Number(r.checkId)
        })
    } catch {
      /* 실패해도 오늘 기록은 표시 */
    }
    const byId = new Map()
    for (const r of [...list, ...pending]) {
      if ((r.gateLogId != null || r.stationCode) && !byId.has(r.checkId)) byId.set(r.checkId, r)
    }
    records.value = [...byId.values()].sort((x, y) => Number(y.checkId) - Number(x.checkId))
  } catch {
    /* 기록 표는 보조 정보 - 실패해도 계량은 가능 */
  }
}

// ---- 선택 차량 / 측정값 ----
const selected = ref(null)
const reweighTarget = ref(null)
const manualPlate = ref('')
const vehicleLoading = ref(false)
const axleCount = ref(3)
const axles = ref(makeAxles(3))
const phase = ref('idle') // idle | reading | ready | saving | done
const liveAxle = ref(-1)
const displayTotal = ref(0)
const weightBreakdown = ref(null) // 26.10.02: 차량/컨테이너/화물 무게 구성
const result = ref(null)
const consoleError = ref('')
let animFrame = null

function makeAxles(n, prev = []) {
  return Array.from({ length: n }, (_, i) => prev[i] ?? { weightKg: null, leftKg: null, rightKg: null })
}

watch(axleCount, (n) => {
  axles.value = makeAxles(n, axles.value)
  recomputeTotal()
})

const busy = computed(() => phase.value === 'reading' || phase.value === 'saving')
const enteredTotal = computed(() => axles.value.reduce((s, a) => s + (Number(a.weightKg) || 0), 0))
const allEntered = computed(() =>
  axles.value.every((a) => Number.isFinite(a.weightKg) && a.weightKg >= 0 && a.weightKg <= 60000),
)
const canSave = computed(() => !busy.value && !result.value && allEntered.value && enteredTotal.value > 0)

function axleState(i) {
  if (phase.value === 'reading' && liveAxle.value === i) return 'is-live'
  const w = axles.value[i]?.weightKg
  if (!Number.isFinite(w) || w === 0) return 'is-empty'
  return w > policy.axleLimitKg ? 'is-over' : 'is-ok'
}

const previewViolations = computed(() => {
  const list = []
  axles.value.forEach((a, i) => {
    if ((a.weightKg || 0) > policy.axleLimitKg) list.push(`${i + 1}축 ${kg(a.weightKg)}kg`)
  })
  if (enteredTotal.value > policy.grossLimitKg) list.push(`총중량 ${kg(enteredTotal.value)}kg`)
  return list
})

const grossPct = computed(() => Math.round((enteredTotal.value / policy.grossLimitKg) * 100))

// 영상 위 카드: 계측이 시작되고 차량이 판독 위치에 오는 시점(T_APPEAR)부터 보이고, 이후엔 계속 표시
const cardShown = computed(() => {
  if (phase.value === 'reading') return videoT.value >= T_APPEAR
  return phase.value !== 'idle' || !!result.value
})
const badge = computed(() => {
  if (result.value) {
    const ok = result.value.record.isPassed
    return {
      tone: ok ? 'is-pass' : 'is-fail',
      text: `${ok ? '✔ 정상 통과 PASS' : '⚠ 과적 초과 FAIL'} · 기록 #${result.value.record.checkId}`,
    }
  }
  if (phase.value === 'reading' && videoT.value < T_BADGE) return null
  if (phase.value === 'idle' || !allEntered.value || enteredTotal.value === 0) return null
  return previewViolations.value.length
    ? { tone: 'is-fail', text: '⚠ 과적 초과 예상 (저장 시 확정)' }
    : { tone: 'is-pass', text: '✔ 정상 범위 (저장 시 확정)' }
})

function resetMeasurement(n) {
  weightBreakdown.value = null
  cancelAnimationFrame(animFrame)
  rewindVideo()
  axleCount.value = n
  axles.value = makeAxles(n)
  displayTotal.value = 0
  liveAxle.value = -1
  result.value = null
  consoleError.value = ''
  phase.value = 'idle'
}

function selectQueueItem(item) {
  if (busy.value) return
  reweighTarget.value = null
  selected.value = item
  resetMeasurement(item.suggestedAxleCount || 3)
  loadRoute(item.vehicleNo, item.gateLogId)
}

// ---- 26.10.02 추가: 이동 경로 지도 ----
const routeData = ref(null)
const routeStatus = computed(() => (result.value ? (result.value.record.isPassed ? 'pass' : 'fail') : 'pending'))

// ---- 26.10.02 추가: 미니 플레이어 ----
const routeMap = ref(null)
const routePhase = ref('')
const miniOpen = ref(true)
const miniBig = ref(false)
const miniPos = ref(null) // 드래그로 옮기면 {x,y}, 아니면 오른쪽 아래 기본 위치
const dragging = ref(false)
const miniStateText = computed(() => {
  if (routeStatus.value === 'fail') return '과적 · 검사소 대기'
  if (routeStatus.value === 'pass') return routePhase.value === 'arrived' ? '목적지 도착' : '통과 · 목적지로 이동 중'
  if (routePhase.value === 'arriving') return '게이트 인 · 검사소로 이동 중'
  return '검사소 계량 중'
})
function startDrag(e) {
  if (e.button !== undefined && e.button !== 0) return
  const box = e.currentTarget.parentElement.getBoundingClientRect()
  const dx = e.clientX - box.left
  const dy = e.clientY - box.top
  dragging.value = true
  const move = (ev) => {
    const w = box.width
    const h = box.height
    miniPos.value = {
      x: Math.min(Math.max(8, ev.clientX - dx), window.innerWidth - w - 8),
      y: Math.min(Math.max(8, ev.clientY - dy), window.innerHeight - h - 8),
    }
  }
  const up = () => {
    dragging.value = false
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}
let routeReq = 0
async function loadRoute(vehicleNo, gateLogId) {
  const my = ++routeReq
  routeData.value = null
  routePhase.value = ''
  miniOpen.value = true
  if (!vehicleNo) return
  try {
    const res = await adminApi.get('/api/v1/weighbridge/route', {
      params: { vehicleNo, gateLogId: gateLogId ?? undefined },
    })
    if (my === routeReq) routeData.value = res.data
  } catch {
    if (my === routeReq)
      routeData.value = { origin: null, weighbridge: null, destination: null, waypoints: [], otherGates: [] }
  }
}

// 26.10.01 추가: 차량번호로 등록차량 정보 + 실린 컨테이너를 DB에서 가져와 화면에 채움
async function fillVehicleInfo(base) {
  vehicleLoading.value = true
  try {
    const res = await adminApi.get(`/api/v1/weighbridge/vehicles/${encodeURIComponent(base.vehicleNo)}`)
    if (selected.value && selected.value.vehicleNo === base.vehicleNo) {
      // 게이트 기록/통과 시각 등 이미 가진 값은 유지하고 차량·컨테이너 정보만 채움
      selected.value = { ...res.data, ...base, ...pickVehicleFields(res.data) }
      // 직접 계량이면 등록차량 정보(세미트레일러 여부)에 맞춰 축 수 기본값도 맞춤
      if (!reweighTarget.value && phase.value === 'idle' && res.data?.suggestedAxleCount) {
        axleCount.value = res.data.suggestedAxleCount
      }
    }
  } catch {
    /* 조회 실패해도 계량은 가능 - 저장할 때 서버가 컨테이너를 다시 찾음 */
  } finally {
    vehicleLoading.value = false
  }
}
function pickVehicleFields(d) {
  const keys = [
    'truckType',
    'companyName',
    'semiTrailer',
    'trailerNo',
    'maxLoadWeight',
    'containerNo',
    'containerSizeType',
    'containerType',
    'containerMaxGrossKg',
    'containerTareKg',
    'containerSource',
  ]
  return Object.fromEntries(keys.map((k) => [k, d?.[k] ?? null]))
}
function containerSourceLabel(src) {
  if (src === 'MAPPING') return '차량 배정 정보'
  if (src === 'LOADING_RECORD') return '최근 적재기록'
  if (src === 'PLANNED_ROUTE') return '차량 운행정보'
  return 'DB'
}

function selectManual() {
  if (!manualPlate.value || busy.value) return
  stopAuto('직접 계량을 시작해 자동 계량을 껐습니다.')
  reweighTarget.value = null
  const base = { gateLogId: null, vehicleNo: manualPlate.value.replace(/\s/g, ''), passAt: null }
  selected.value = { ...base }
  manualPlate.value = ''
  resetMeasurement(3)
  fillVehicleInfo(base)
  loadRoute(base.vehicleNo, null)
}

function startReweigh(record) {
  if (busy.value) return
  stopAuto('재계량을 시작해 자동 계량을 껐습니다.')
  reweighTarget.value = record
  const base = { gateLogId: record.gateLogId, vehicleNo: record.vehicleNo, passAt: null }
  selected.value = { ...base, containerNo: record.containerNo || null }
  resetMeasurement(Math.min(6, Math.max(2, record.axleCount || 3)))
  fillVehicleInfo(base)
  loadRoute(base.vehicleNo, base.gateLogId)
  window.scrollTo({ top: 0, behavior: reduceMotion ? 'auto' : 'smooth' })
}

function clearSelection() {
  cancelAnimationFrame(animFrame)
  rewindVideo()
  routeReq++
  routeData.value = null
  selected.value = null
  reweighTarget.value = null
  phase.value = 'idle'
  result.value = null
  consoleError.value = ''
}

function nextVehicle() {
  const next = queue.value.find((q) => q.gateLogId !== selected.value?.gateLogId)
  if (next) selectQueueItem(next)
  else clearSelection()
}

function onManualEdit() {
  weightBreakdown.value = null
  axles.value.forEach((a) => { a.leftKg = null; a.rightKg = null })
  result.value = null
  phase.value = 'ready'
  recomputeTotal()
}
function recomputeTotal() {
  displayTotal.value = enteredTotal.value
}

// ---- 축중 계측 ----
// 축중기 장비가 아직 연결되어 있지 않아, 차량 정보(최대적재량/세미트레일러 여부)를 바탕으로
// 현실적인 범위의 축중을 만들어내는 계측 시뮬레이터를 쓴다. 장비를 붙일 때는 이 함수의
// targets 계산 부분만 장비 수신값으로 바꾸면 나머지(애니메이션/판정/저장)는 그대로 동작한다.
// 26.10.02 변경: 총중량 = 차량 자체중량 + 컨테이너 자체중량 + 화물(컨테이너 안)
function simulateTargets(n, vehicle, retry) {
  const semi = n >= 4
  const truckTare = semi ? 14500 : 9000 // 트랙터+샤시 / 카고트럭 자체중량
  const cTare = Number(vehicle?.containerTareKg) > 0 ? Number(vehicle.containerTareKg) : 0
  const cMax = Number(vehicle?.containerMaxGrossKg) > 0 ? Number(vehicle.containerMaxGrossKg) : 0
  const cNet = cMax > cTare ? cMax - cTare : 0
  // 26.10.02 변경: 적재중량(컨테이너 자중+화물)은 차량 최대적재량(trucks.max_load_weight) 기준으로 생성
  //   첫 계량 75~125%(110% 초과 = 적재중량 위반), 재계량은 감량 후 70~95%
  const rated = Number(vehicle?.maxLoadWeight) > 0 ? Number(vehicle.maxLoadWeight) : semi ? 26000 : 12000
  const loadRatio = retry ? 0.7 + Math.random() * 0.25 : 0.75 + Math.random() * 0.5
  let cargo = Math.max(0, rated * loadRatio - cTare)
  if (cNet > 0) cargo = Math.min(cargo, cNet) // 컨테이너에 실을 수 있는 양을 넘지 않음
  cargo = Math.round(cargo / 10) * 10
  weightBreakdown.value = { truckTare, containerTare: cTare, cargo, hasContainer: cTare > 0 }
  const gross = truckTare + cTare + cargo
  const steer = semi ? 6200 + Math.random() * 900 : 5600 + Math.random() * 800
  const rest = Math.max(0, gross - steer)
  const shares = Array.from({ length: n - 1 }, (_, i) => (semi && i >= 2 ? 1.05 : 1) * (0.9 + Math.random() * 0.2))
  const sum = shares.reduce((s, v) => s + v, 0)
  const weights = [steer, ...shares.map((s) => (rest * s) / sum)]
  const out = weights.map((w) => {
    const weightKg = Math.round(w / 10) * 10
    const leftKg = Math.round((weightKg * (0.47 + Math.random() * 0.06)) / 10) * 10
    return { weightKg, leftKg, rightKg: weightKg - leftKg }
  })
  // 반올림 오차를 마지막 축에 맞춰 합계를 정확히 맞춤
  const diff = gross - out.reduce((s, a) => s + a.weightKg, 0)
  out[out.length - 1].weightKg += diff
  out[out.length - 1].rightKg += diff
  return out
}

// 영상 타임라인(초) - 기존 검사소 데모 영상의 장면에 맞춘 값
// 2.9s 차량이 판독 위치 도착 → 3.3~4.6s 축별 판독 → 4.7~5.3s 총중량 확정 → 5.6s 판정 표시
const T_APPEAR = 2.9
const T_AXLE_START = 3.3
const T_AXLE_END = 4.6
const T_TOTAL = 4.7
const T_TOTAL_SETTLE = 5.3
const T_BADGE = 5.6

const videoEl = ref(null)
const videoT = ref(0)
let readingTargets = []
let clockStart = 0

function rewindVideo() {
  const v = videoEl.value
  videoT.value = 0
  if (!v) return
  v.pause()
  try {
    v.currentTime = 0
  } catch {
    /* 메타데이터 로딩 전 */
  }
}

function showLastFrame() {
  const v = videoEl.value
  if (!v) return
  v.pause()
  if (Number.isFinite(v.duration)) {
    try {
      v.currentTime = Math.max(0, v.duration - 0.05)
    } catch {
      /* noop */
    }
  }
}

function ramp(now, start, settle, target) {
  if (now < start) return null
  if (now >= settle) return target
  const p = (now - start) / (settle - start)
  const jitter = (1 - p) * 500 * Math.sin(now * 40) // 로드셀이 흔들리다 안정되는 느낌
  return Math.max(0, Math.round((target * Math.min(1, p * 1.15) + jitter) / 10) * 10)
}

function readFromScale() {
  if (busy.value || !selected.value) return
  result.value = null
  consoleError.value = ''
  readingTargets = simulateTargets(axleCount.value, selected.value, !!reweighTarget.value)
  axles.value = makeAxles(axleCount.value)
  displayTotal.value = 0

  if (reduceMotion || !videoEl.value) {
    axles.value = readingTargets.map((t) => ({ ...t }))
    recomputeTotal()
    videoT.value = T_BADGE
    showLastFrame()
    phase.value = 'ready'
    return
  }

  phase.value = 'reading'
  const v = videoEl.value
  try {
    v.currentTime = 0
  } catch {
    /* noop */
  }
  clockStart = performance.now()
  v.play().catch(() => {
    /* 재생이 막혀도 아래 시계로 같은 순서대로 진행 */
  })
  animFrame = requestAnimationFrame(tickReading)
}

function tickReading() {
  const v = videoEl.value
  const wall = (performance.now() - clockStart) / 1000
  // 영상이 실제로 재생 중이면 영상 시간에 맞추고, 아니면 경과 시간으로 진행
  const t = v && !v.paused && v.currentTime > 0 ? v.currentTime : wall
  videoT.value = t

  const n = readingTargets.length
  const span = (T_AXLE_END - T_AXLE_START) / n
  readingTargets.forEach((tg, i) => {
    const start = T_AXLE_START + i * span
    const val = ramp(t, start, start + span * 0.9, tg.weightKg)
    axles.value[i] =
      val === null
        ? { weightKg: null, leftKg: null, rightKg: null }
        : val === tg.weightKg
          ? { ...tg }
          : { weightKg: val, leftKg: null, rightKg: null }
  })
  const total = readingTargets.reduce((s, x) => s + x.weightKg, 0)
  const tv = ramp(t, T_TOTAL, T_TOTAL_SETTLE, total)
  displayTotal.value = tv ?? 0

  if (t < T_BADGE) {
    animFrame = requestAnimationFrame(tickReading)
  } else {
    axles.value = readingTargets.map((x) => ({ ...x }))
    recomputeTotal()
    phase.value = 'ready' // 영상은 끝까지 재생되고 마지막 장면(검사소 표시기)에서 멈춤
  }
}

// ---- 저장 (서버 판정) ----
async function save() {
  if (!canSave.value) return
  phase.value = 'saving'
  consoleError.value = ''
  const body = {
    gateLogId: selected.value.gateLogId ?? null,
    vehicleNo: selected.value.vehicleNo,
    containerNo: selected.value.containerNo || null, // 비어 있으면 서버가 DB에서 다시 찾아 채움
    stationCode: policy.stationCode,
    // 26.10.02 추가: 컨테이너 총중량(VGM) - 컨테이너 자체중량 + 화물
    // 적재중량 = 컨테이너 자중 + 화물 (최대적재량과 비교하는 값)
    vgmWeightKg: weightBreakdown.value
      ? weightBreakdown.value.containerTare + weightBreakdown.value.cargo
      : null,
    axles: axles.value.map((a) => ({
      weightKg: Math.round(a.weightKg),
      leftKg: Number.isFinite(a.leftKg) ? a.leftKg : null,
      rightKg: Number.isFinite(a.rightKg) ? a.rightKg : null,
    })),
  }
  try {
    const res = reweighTarget.value
      ? await adminApi.post(`/api/v1/weighbridge/weighings/${reweighTarget.value.checkId}/reweigh`, body)
      : await adminApi.post('/api/v1/weighbridge/weighings', body)
    result.value = res.data
    displayTotal.value = res.data.record.totalWeight ?? enteredTotal.value
    phase.value = 'done'
    reweighTarget.value = null
    await Promise.all([loadQueue(), loadRecords()])
  } catch (err) {
    consoleError.value = pickErrorMessage(err, '계량 결과를 저장하지 못했습니다.')
    phase.value = 'ready'
    if (err?.response?.status === 409) loadQueue()
  }
}

// ---- 표시 도우미 ----
function kg(v) {
  if (v === null || v === undefined || v === '' || Number.isNaN(Number(v))) return '-'
  return Math.round(Number(v)).toLocaleString('ko-KR')
}
function tons(v) {
  if (v === null || v === undefined || v === '' || Number.isNaN(Number(v))) return '--.-'
  return (Number(v) / 1000).toFixed(1)
}
function axlePct(v) {
  return Math.round(((Number(v) || 0) / policy.axleLimitKg) * 100)
}
function hhmm(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  const t = d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
  // 26.10.06: 지난날 재계량 대기 기록은 날짜도 함께 표시
  return d.toDateString() === new Date().toDateString() ? t : `${d.getMonth() + 1}/${d.getDate()} ${t}`
}
function hhmmss(iso) {
  const d = iso ? new Date(iso) : new Date()
  return d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
}
const nowTick = ref(Date.now())
function elapsed(iso) {
  if (!iso) return ''
  const min = Math.max(0, Math.floor((nowTick.value - new Date(iso).getTime()) / 60000))
  if (min < 1) return '방금'
  if (min < 60) return `${min}분 대기`
  return `${Math.floor(min / 60)}시간 ${min % 60}분 대기`
}

// ---- 26.10.01 추가: 자동 계량 ----
// 켜두면: 대기열 첫 차량 선택 → 영상과 함께 축중 계측 → 서버 판정·저장 → 결과 표시(AUTO_NEXT_SEC) → 다음 차량
// 사람이 콘솔을 직접 조작하면(차량 클릭, 계측/저장/취소, 값 수정, 직접 계량, 재계량) 자동 계량은 꺼진다.
const AUTO_KEY = 'scargo.weighbridge.auto'
const AUTO_NEXT_SEC = 4
const autoMode = ref(readAutoPref())
const autoRunning = ref(false) // 지금 선택된 차량을 자동 모드가 진행 중인지
const autoCountdown = ref(0)
const autoNote = ref('')
let autoTimer = null
let autoStartTimer = null

function readAutoPref() {
  try {
    return localStorage.getItem(AUTO_KEY) !== 'off'
  } catch {
    return true
  }
}
function setAutoMode(on) {
  autoMode.value = on
  autoNote.value = ''
  try {
    localStorage.setItem(AUTO_KEY, on ? 'on' : 'off')
  } catch {
    /* 저장 못 해도 동작에는 영향 없음 */
  }
  if (on) autoTick()
  else cancelAutoTimers()
}
function stopAuto(note) {
  if (!autoMode.value && !autoRunning.value) return
  cancelAutoTimers()
  autoRunning.value = false
  if (autoMode.value) {
    autoMode.value = false
    autoNote.value = note || '수동 조작으로 자동 계량을 껐습니다.'
  }
}
function cancelAutoTimers() {
  clearInterval(autoTimer)
  clearTimeout(autoStartTimer)
  autoTimer = null
  autoStartTimer = null
  autoCountdown.value = 0
}
// 사람이 누른 버튼/입력은 자동 계량을 끄고 그대로 실행
function userAction(fn) {
  stopAuto()
  return fn()
}
function onUserSelect(item) {
  if (busy.value) return
  stopAuto('차량을 직접 골라 자동 계량을 껐습니다.')
  selectQueueItem(item)
}

// 다음 차량을 시작할 수 있으면 시작
function autoTick() {
  if (!autoMode.value || busy.value || autoCountdown.value > 0 || autoStartTimer) return
  if (selected.value && !result.value) return // 사람이 보던 차량이 있으면 건드리지 않음
  const next = queue.value[0]
  if (!next) {
    if (result.value) clearSelection()
    return
  }
  autoRunning.value = true
  selectQueueItem(next)
  // 화면이 바뀐 걸 볼 수 있게 잠깐 뒤에 계측 시작
  autoStartTimer = setTimeout(() => {
    autoStartTimer = null
    if (autoMode.value && autoRunning.value && selected.value?.gateLogId === next.gateLogId) readFromScale()
  }, 800)
}

// 계측이 끝나면(ready) 자동 저장, 저장이 끝나면(done) 카운트다운 후 다음 차량
watch(phase, (p) => {
  if (!autoMode.value || !autoRunning.value) return
  if (p === 'ready' && canSave.value) {
    save()
  } else if (p === 'done') {
    autoRunning.value = false
    autoCountdown.value = AUTO_NEXT_SEC
    autoTimer = setInterval(() => {
      autoCountdown.value -= 1
      if (autoCountdown.value <= 0) {
        cancelAutoTimers()
        autoTick()
      }
    }, 1000)
  }
})
// 저장 실패: 이미 계량된 차량(409)이면 건너뛰고, 그 밖의 오류면 자동 계량을 멈춤
watch(consoleError, (msg) => {
  if (!msg || !autoRunning.value) return
  autoRunning.value = false
  if (/이미 계량/.test(msg)) {
    clearSelection()
    setTimeout(autoTick, 500)
  } else {
    stopAuto('저장 중 오류가 나서 자동 계량을 멈췄습니다. 확인 후 다시 켜주세요.')
  }
})

const autoStatus = computed(() => {
  if (!autoMode.value) return autoNote.value || '수동 모드: 차량을 골라 축중 계측을 누르세요.'
  if (phase.value === 'reading') return `${selected.value?.vehicleNo ?? ''} 축중 계측 중`
  if (phase.value === 'saving') return '서버 판정 후 저장 중'
  if (autoCountdown.value > 0) return `저장 완료. ${autoCountdown.value}초 뒤 다음 차량`
  if (autoStartTimer || autoRunning.value) return `${selected.value?.vehicleNo ?? ''} 계량 시작`
  if (queueError.value) return '서버 연결을 기다리는 중'
  return queue.value.length ? '곧 다음 차량을 계량합니다' : '대기 중: 게이트를 통과한 차량이 오면 바로 계량합니다'
})

onMounted(() => {
  loadPolicy()
  loadRecords()
  pollId = setInterval(async () => {
    nowTick.value = Date.now()
    await loadQueue()
    autoTick()
  }, POLL_MS)
  loadQueue().then(autoTick)
})
onBeforeUnmount(() => {
  clearInterval(pollId)
  cancelAutoTimers()
  cancelAnimationFrame(animFrame)
})
</script>

<style scoped>
/* 26.10.01 추가: 검사소 콘솔. 색은 theme.css 토큰(네이비/오렌지)을 따르고,
   지시계(총중량 표시)만 실제 검사소 표시기처럼 어두운 패널로 강조한다. */
.wb {
  --wb-ink: var(--color-text, #0a2540);
  --wb-sub: var(--color-subtext, #64748b);
  --wb-line: #e2e8f0;
  --wb-soft: #f1f4f8;
  --wb-accent: var(--color-accent, #ff6b00);
  --wb-pass: #0f9d6e;
  --wb-fail: #d63a3a;
  --wb-panel: #0b1d33;
  --wb-amber: #ffb547;
  color: var(--wb-ink);
}

.wb-policy {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--wb-sub);
}
.wb-policy span {
  padding: 4px 10px;
  border: 1px solid var(--wb-line);
  border-radius: 6px;
  background: #fff;
}
.wb-policy .wb-station {
  font-family: 'Barlow Condensed', 'Inter', sans-serif;
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  background: var(--wb-ink);
  border-color: var(--wb-ink);
}

.wb-grid {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
@media (max-width: 1080px) {
  .wb-grid {
    grid-template-columns: 1fr;
  }
}

/* ---------- 대기열 ---------- */
.wb-queue {
  background: #fff;
  border: 1px solid var(--wb-line);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.wb-queue-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.wb-queue-head h2 {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
}
.wb-count {
  font-family: 'Barlow Condensed', 'Inter', sans-serif;
  font-size: 18px;
  font-weight: 700;
  color: var(--wb-accent);
}
.wb-live {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--wb-pass);
}
.wb-live.is-off {
  color: var(--wb-fail);
}
.wb-live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}
/* 26.10.02 추가: 이동 경로 미니 플레이어 */
.wb-pip {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1050;
  width: 380px;
  max-width: calc(100vw - 32px);
  height: 300px;
  display: flex;
  flex-direction: column;
  background: #0d1420;
  border-radius: 12px;
  overflow: hidden;
  box-shadow:
    0 18px 40px rgba(10, 37, 64, 0.35),
    0 2px 6px rgba(0, 0, 0, 0.2);
  transition:
    width 0.2s ease,
    height 0.2s ease;
}
.wb-pip.is-big {
  width: 600px;
  height: 440px;
}
.wb-pip.is-dragging {
  transition: none;
  user-select: none;
}
.wb-pip-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 8px 8px 12px;
  color: #e7edf5;
  cursor: grab;
  touch-action: none;
}
.wb-pip.is-dragging .wb-pip-bar {
  cursor: grabbing;
}
.wb-pip-live {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--wb-accent);
  flex: none;
  animation: wbPulse 1.6s ease-out infinite;
}
.wb-pip-live.pass {
  background: #4fd1a5;
}
.wb-pip-live.fail {
  background: #ff5c5c;
}
.wb-pip-plate {
  font-family: 'Barlow Condensed', 'Inter', sans-serif;
  font-size: 17px;
  letter-spacing: 0.3px;
  white-space: nowrap;
}
.wb-pip-state {
  font-size: 12px;
  color: #9db0c7;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}
.wb-pip-state.pass {
  color: #4fd1a5;
}
.wb-pip-state.fail {
  color: #ff8a8a;
}
.wb-pip-tools {
  margin-left: auto;
  display: inline-flex;
  gap: 2px;
}
.wb-pip-tools button {
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #c7d3e2;
  cursor: pointer;
}
.wb-pip-tools button:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}
.wb-pip-tools button:focus-visible {
  outline: 2px solid var(--wb-accent);
}
.wb-pip-body {
  flex: 1;
  min-height: 0;
  background: #e5e7eb;
}
.wb-pip-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  font-size: 12px;
  color: #9db0c7;
  white-space: nowrap;
  overflow: hidden;
}
.wb-pip-foot span {
  overflow: hidden;
  text-overflow: ellipsis;
}
.wb-pip-foot .is-wb {
  color: #ffb547;
}
.wb-pip-foot i {
  font-size: 10px;
  opacity: 0.7;
}
.wb-pip-reopen {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1050;
  padding: 10px 14px;
  border: 0;
  border-radius: 999px;
  background: var(--wb-ink);
  color: #fff;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba(10, 37, 64, 0.3);
}
@media (max-width: 560px) {
  .wb-pip,
  .wb-pip.is-big {
    right: 8px;
    bottom: 8px;
    width: calc(100vw - 16px);
    height: 260px;
  }
}

/* 자동 계량 스위치 */
.wb-auto {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--wb-line);
  border-radius: 8px;
  background: var(--wb-soft);
}
.wb-auto.is-on {
  border-color: rgba(255, 107, 0, 0.45);
  background: rgba(255, 107, 0, 0.06);
}
.wb-switch {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  font-weight: 700;
  font-size: 14px;
  user-select: none;
}
.wb-switch input {
  position: absolute;
  opacity: 0;
  width: 1px;
  height: 1px;
}
.wb-switch-track {
  position: relative;
  width: 40px;
  height: 22px;
  border-radius: 999px;
  background: #cbd5e1;
  transition: background 0.2s ease;
  flex: none;
}
.wb-switch-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
  transition: transform 0.2s ease;
}
.wb-switch input:checked + .wb-switch-track {
  background: var(--wb-accent);
}
.wb-switch input:checked + .wb-switch-track .wb-switch-thumb {
  transform: translateX(18px);
}
.wb-switch input:focus-visible + .wb-switch-track {
  outline: 2px solid var(--wb-accent);
  outline-offset: 2px;
}
.wb-auto-status {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--wb-sub);
}
.wb-auto.is-on .wb-auto-status {
  color: var(--wb-ink);
}

.wb-queue-note {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
}
.wb-queue-note.is-error {
  color: var(--wb-fail);
}

.wb-queue-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 460px;
  overflow-y: auto;
}
.wb-queue-item {
  width: 100%;
  text-align: left;
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--wb-line);
  border-left: 4px solid transparent;
  border-radius: 8px;
  background: #fff;
  color: inherit;
  cursor: pointer;
  font: inherit;
}
.wb-queue-item:hover {
  background: var(--wb-soft);
}
.wb-queue-item.is-active {
  border-left-color: var(--wb-accent);
  background: rgba(255, 107, 0, 0.06);
}
.wb-queue-item:focus-visible,
.wb-btn:focus-visible,
.wb-mini:focus-visible,
.wb-manual button:focus-visible {
  outline: 2px solid var(--wb-accent);
  outline-offset: 2px;
}
.wb-queue-meta {
  font-size: 13px;
  color: var(--wb-sub);
}
.wb-queue-time {
  font-size: 12px;
  color: var(--wb-sub);
}
.wb-queue-time em {
  font-style: normal;
  color: var(--wb-ink);
  font-weight: 600;
  margin-left: 4px;
}
.wb-queue-empty {
  padding: 18px 12px;
  border: 1px dashed var(--wb-line);
  border-radius: 8px;
  font-size: 13px;
  color: var(--wb-sub);
  line-height: 1.6;
}
.wb-queue-empty p {
  margin: 0 0 4px;
}
.wb-queue-empty p:first-child {
  color: var(--wb-ink);
  font-weight: 600;
}
.wb-link {
  display: inline-block;
  margin-top: 6px;
  color: var(--wb-ink);
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* 한국 번호판 느낌의 표기 */
.wb-plate {
  justify-self: start;
  display: inline-block;
  font-family: 'Barlow Condensed', 'Inter', sans-serif;
  font-weight: 700;
  font-size: 19px;
  letter-spacing: 0.5px;
  line-height: 1;
  padding: 6px 10px;
  border: 2px solid var(--wb-ink);
  border-radius: 5px;
  background: #fff;
  white-space: nowrap;
}
.wb-plate-lg {
  font-size: 30px;
  padding: 8px 16px;
  border-width: 3px;
}

.wb-manual {
  border-top: 1px solid var(--wb-line);
  padding-top: 12px;
}
.wb-manual label {
  display: block;
  font-size: 13px;
  color: var(--wb-sub);
  margin-bottom: 6px;
}
.wb-manual-row {
  display: flex;
  gap: 6px;
}
.wb-manual input {
  flex: 1;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid var(--wb-line);
  border-radius: 6px;
  font: inherit;
  font-size: 14px;
}
.wb-manual button {
  padding: 8px 12px;
  border: 1px solid var(--wb-ink);
  border-radius: 6px;
  background: #fff;
  color: var(--wb-ink);
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.wb-manual button:disabled {
  opacity: 0.45;
  cursor: default;
}

/* ---------- 콘솔 ---------- */
.wb-console {
  background: #fff;
  border: 1px solid var(--wb-line);
  border-radius: 12px;
  padding: 20px;
  min-width: 0;
}
.wb-console-empty {
  min-height: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--wb-sub);
  font-size: 14px;
}
.wb-console-empty svg {
  width: 120px;
  fill: #dbe2ea;
}

.wb-vehicle {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--wb-line);
}
.wb-photo {
  width: 120px;
  height: 72px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--wb-line);
}
.wb-vehicle-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
  margin: 0;
}
.wb-vehicle-facts div {
  display: grid;
  gap: 2px;
}
.wb-vehicle-facts dt {
  font-size: 12px;
  color: var(--wb-sub);
  font-weight: 500;
}
.wb-vehicle-facts dd {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

/* 검사소 영상 + 판독 카드 (기존 데모의 카드 위치/구성을 그대로 살림) */
.wb-stage {
  container-type: inline-size;
  margin: 16px 0 4px;
  background: #0d1420;
  border-radius: 12px;
  overflow: hidden;
}
.wb-frame {
  position: relative;
  aspect-ratio: 16 / 9;
  background: #000;
}
.wb-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.wb-stage-hint {
  position: absolute;
  left: 50%;
  bottom: 6%;
  transform: translateX(-50%);
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(10, 20, 35, 0.72);
  color: #e7edf5;
  font-size: 13px;
  white-space: nowrap;
}
.wb-card {
  position: absolute;
  top: 3.5%;
  left: 62%;
  width: 34.5%;
  min-width: 230px;
  background: linear-gradient(180deg, #121b2b, #0d1420);
  border: 1px solid #2c3d57;
  border-radius: 10px;
  padding: 3cqw 3.4cqw;
  color: #e7edf5;
  opacity: 0;
  transform: translateY(-10px) scale(0.97);
  transform-origin: 88% 0%;
  transition:
    opacity 0.3s ease,
    transform 0.3s ease;
  pointer-events: none;
}
.wb-card::after {
  content: '';
  position: absolute;
  bottom: -8px;
  left: 68%;
  width: 16px;
  height: 16px;
  background: #0d1420;
  border-right: 1px solid #2c3d57;
  border-bottom: 1px solid #2c3d57;
  transform: rotate(45deg);
}
.wb-card.is-show {
  opacity: 1;
  transform: translateY(0) scale(1);
}
.wb-card-row1 {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #8a97ab;
  font-size: clamp(10px, 1.2cqw, 13px);
}
.wb-card-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4fd1a5;
  flex: none;
  animation: wbPulse 1.6s ease-out infinite;
}
@keyframes wbPulse {
  0% {
    box-shadow: 0 0 0 0 rgba(79, 209, 165, 0.55);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(79, 209, 165, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(79, 209, 165, 0);
  }
}
.wb-card-id {
  margin-top: 1.6cqw;
  font-size: clamp(14px, 1.9cqw, 20px);
  font-weight: 700;
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}
.wb-card-sub {
  color: #8a97ab;
  font-size: clamp(10px, 1.1cqw, 12px);
  font-weight: 500;
}
.wb-card-axles {
  margin-top: 1.4cqw;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
}
.wb-card-axles span {
  color: #8a97ab;
  font-size: clamp(10px, 1.1cqw, 12px);
}
.wb-card-axles b {
  color: #e7edf5;
  font-size: clamp(12px, 1.45cqw, 15px);
  font-variant-numeric: tabular-nums;
}
.wb-card-axles .is-over b {
  color: #ff5c5c;
}
.wb-card-total {
  margin-top: 1.3cqw;
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}
.wb-card-label {
  color: #8a97ab;
  font-size: clamp(10px, 1.15cqw, 12.5px);
}
.wb-card-value {
  font-size: clamp(18px, 2.6cqw, 28px);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.wb-card-value.is-over {
  color: #ff5c5c;
}
.wb-card-value small {
  font-size: clamp(10px, 1.15cqw, 12.5px);
  color: #8a97ab;
  font-weight: 500;
  margin-left: 3px;
}
.wb-card-spin {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid rgba(138, 151, 171, 0.35);
  border-top-color: #8a97ab;
  animation: wbSpin 0.9s linear infinite;
  align-self: center;
}
@keyframes wbSpin {
  to {
    transform: rotate(360deg);
  }
}
.wb-card-badge {
  margin-top: 1.4cqw;
  display: inline-flex;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: clamp(10px, 1.25cqw, 13px);
  font-weight: 700;
}
.wb-card-badge.is-fail {
  background: rgba(255, 92, 92, 0.14);
  border: 1px solid rgba(255, 92, 92, 0.5);
  color: #ff5c5c;
}
.wb-card-badge.is-pass {
  background: rgba(79, 209, 165, 0.14);
  border: 1px solid rgba(79, 209, 165, 0.5);
  color: #4fd1a5;
}

/* 화면이 좁으면 카드가 영상을 가리지 않도록 영상 아래로 내림 */
@container (max-width: 560px) {
  .wb-frame {
    aspect-ratio: auto;
  }
  .wb-video {
    position: relative;
    height: auto;
    aspect-ratio: 16 / 9;
  }
  .wb-card {
    position: static;
    width: auto;
    min-width: 0;
    border: 0;
    border-radius: 0;
    padding: 12px 14px;
    transform: none;
  }
  .wb-card::after {
    display: none;
  }
  .wb-stage-hint {
    bottom: auto;
    top: 12px;
  }
}

/* 축별 측정값 */
.wb-readout {
  margin-top: 14px;
}
.wb-breakdown {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--wb-sub);
}
.wb-breakdown-vgm {
  color: var(--wb-ink);
  font-weight: 600;
}
.wb-total-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px 12px;
  padding: 10px 0 2px;
  border-top: 1px solid var(--wb-line);
  font-size: 13px;
  color: var(--wb-sub);
}
.wb-total-row strong {
  font-family: 'Barlow Condensed', 'Inter', sans-serif;
  font-size: 24px;
  color: var(--wb-ink);
  font-variant-numeric: tabular-nums;
}
.wb-total-row.is-over strong {
  color: var(--wb-fail);
}
.wb-total-pct {
  margin-left: auto;
}

.wb-axles {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.wb-axles-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.wb-axles-head select {
  padding: 5px 8px;
  border: 1px solid var(--wb-line);
  border-radius: 6px;
  font: inherit;
  font-size: 13px;
  background: #fff;
}
.wb-source {
  margin-left: auto;
  font-size: 12px;
  color: var(--wb-sub);
}

.wb-axle-rows {
  display: grid;
  gap: 6px;
}
.wb-axle-row {
  display: grid;
  grid-template-columns: 38px 132px minmax(0, 1fr) 44px;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.wb-axle-row label {
  font-weight: 600;
  color: var(--wb-sub);
}
.wb-axle-input {
  display: flex;
  align-items: center;
  border: 1px solid var(--wb-line);
  border-radius: 6px;
  padding-right: 8px;
  background: #fff;
}
.wb-axle-input input {
  width: 100%;
  min-width: 0;
  border: 0;
  padding: 7px 8px;
  font: inherit;
  font-family: 'Barlow Condensed', 'Inter', sans-serif;
  font-size: 17px;
  font-weight: 600;
  text-align: right;
  font-variant-numeric: tabular-nums;
  background: transparent;
}
.wb-axle-input input:focus {
  outline: none;
}
.wb-axle-input:focus-within {
  border-color: var(--wb-ink);
}
.wb-axle-input span {
  font-size: 12px;
  color: var(--wb-sub);
}
.wb-axle-gauge {
  height: 8px;
  border-radius: 4px;
  background: var(--wb-soft);
  overflow: hidden;
}
.wb-axle-gauge span {
  display: block;
  height: 100%;
  background: var(--wb-ink);
  border-radius: 4px;
  transition: width 0.15s linear;
}
.wb-axle-row.is-live .wb-axle-gauge span {
  background: var(--wb-accent);
}
.wb-axle-row.is-over .wb-axle-gauge span {
  background: var(--wb-fail);
}
.wb-axle-row.is-over label,
.wb-axle-row.is-over .wb-axle-pct {
  color: var(--wb-fail);
}
.wb-axle-pct {
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--wb-sub);
  font-size: 13px;
}

.wb-container-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--wb-sub);
  margin-top: 4px;
}
.wb-container-row {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--wb-soft);
}
.wb-container-label {
  font-weight: 600;
  color: var(--wb-sub);
  margin-right: 4px;
}
.wb-container-no {
  font-family: 'Barlow Condensed', 'Inter', sans-serif;
  font-size: 18px;
  letter-spacing: 0.5px;
  color: var(--wb-ink);
}
.wb-container-meta {
  color: var(--wb-ink);
}
.wb-container-meta::before {
  content: '';
  display: inline-block;
  width: 1px;
  height: 11px;
  margin-right: 8px;
  background: #cbd5e1;
  vertical-align: -1px;
}
.wb-container-src {
  margin-left: auto;
  font-size: 12px;
  color: var(--wb-sub);
}
.wb-container-none {
  color: var(--wb-sub);
}

/* 판정 */
.wb-verdict {
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid;
}
.wb-verdict.is-pass {
  border-color: rgba(15, 157, 110, 0.35);
  background: rgba(15, 157, 110, 0.07);
}
.wb-verdict.is-fail {
  border-color: rgba(214, 58, 58, 0.35);
  background: rgba(214, 58, 58, 0.06);
}
.wb-verdict-main {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px 14px;
}
.wb-verdict-main strong {
  font-family: 'Barlow Condensed', 'Inter', sans-serif;
  font-size: 30px;
  line-height: 1;
}
.wb-verdict.is-pass strong {
  color: var(--wb-pass);
}
.wb-verdict.is-fail strong {
  color: var(--wb-fail);
}
.wb-verdict-main span {
  font-size: 13px;
  color: var(--wb-sub);
}
.wb-verdict-list {
  margin: 10px 0 0;
  padding-left: 18px;
  font-size: 14px;
  color: var(--wb-ink);
  line-height: 1.7;
}
.wb-verdict-hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--wb-sub);
}
.wb-preview {
  margin: 16px 0 0;
  font-size: 13px;
}
.wb-preview.is-fail {
  color: var(--wb-fail);
}
.wb-error {
  margin: 14px 0 0;
  font-size: 13px;
  color: var(--wb-fail);
  white-space: pre-line;
}

.wb-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}
.wb-btn {
  padding: 11px 18px;
  border-radius: 8px;
  border: 1px solid var(--wb-ink);
  background: #fff;
  color: var(--wb-ink);
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.wb-btn.is-primary {
  background: var(--wb-accent);
  border-color: var(--wb-accent);
  color: #fff;
}
.wb-btn.is-ghost {
  border-color: transparent;
  color: var(--wb-sub);
  margin-left: auto;
}
.wb-btn:disabled {
  opacity: 0.45;
  cursor: default;
}

/* ---------- 오늘 기록 ---------- */
.wb-records {
  margin-top: 24px;
  background: #fff;
  border: 1px solid var(--wb-line);
  border-radius: 12px;
  padding: 16px 18px;
}
.wb-records header {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px 14px;
  margin-bottom: 10px;
}
.wb-records h2 {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
}
.wb-records-sum {
  font-size: 13px;
  color: var(--wb-sub);
}
.wb-table-wrap {
  overflow-x: auto;
}
.wb-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
  min-width: 900px;
  table-layout: fixed;
}
/* 26.10.02: 과적 검사 목록과 같은 모양 - 가운데 정렬, 한 줄 고정 높이 */
.wb-table th:nth-child(1) { width: 60px; }
.wb-table th:nth-child(2) { width: 100px; }
.wb-table th:nth-child(3) { width: 140px; }
.wb-table th:nth-child(4) { width: 110px; }
.wb-table th:nth-child(5) { width: 60px; }
.wb-table th:nth-child(6) { width: 120px; }
.wb-table th:nth-child(7) { width: 130px; }
.wb-table th:nth-child(9) { width: 90px; }
.wb-table th {
  text-align: center;
  font-weight: 600;
  font-size: 12px;
  color: var(--wb-sub);
  padding: 8px 10px;
  border-bottom: 1px solid var(--wb-line);
  white-space: nowrap;
}
.wb-table td {
  height: 60px;
  padding: 0 10px;
  border-bottom: 1px solid #eef2f6;
  vertical-align: middle;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.wb-table .num {
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.wb-table .plate-cell {
  font-weight: 500;
}
.wb-table .reason {
  color: var(--wb-sub);
  text-align: left;
}
.wb-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}
.wb-tag.is-pass {
  color: var(--wb-pass);
  background: rgba(15, 157, 110, 0.1);
}
.wb-tag.is-fail {
  color: var(--wb-fail);
  background: rgba(214, 58, 58, 0.1);
}
.wb-mini {
  padding: 5px 10px;
  border: 1px solid var(--wb-ink);
  border-radius: 6px;
  background: #fff;
  color: var(--wb-ink);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}
.wb-mini:disabled {
  opacity: 0.45;
  cursor: default;
}
.wb-table .seq {
  color: var(--wb-sub);
  font-variant-numeric: tabular-nums;
}
.wb-records-empty {
  margin: 8px 0 4px;
  font-size: 13px;
  color: var(--wb-sub);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .wb-card,
  .wb-axle-gauge span {
    transition: none;
  }
  .wb-card-dot,
  .wb-card-spin {
    animation: none;
  }
}
</style>
