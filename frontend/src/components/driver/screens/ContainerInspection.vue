<template>
  <!-- 26.10.23 기사 컨테이너 점검표 (7-point). 본인 기록만 보이고, 본인 작성 중 기록만 수정 · 확정할 수 있다
       위쪽: 본인에게 배정된 운송 오더 (차량 · 업체 · 도착 허브 · 씰 등록 여부) + 점검 작성 · 운송 출발 -->
  <div class="topbar">
    <div>
      <div class="brand">컨테이너 점검표</div>
      <div class="sub">출발 전 컨테이너 7개 부위를 점검하고 확정하세요</div>
    </div>
  </div>

  <main class="page">
    <section class="ord-wrap">
      <h3 class="ord-h">내 운송 오더</h3>
      <p v-if="orderMsg" class="ord-msg" :class="{ err: orderErr }">{{ orderMsg }}</p>
      <p v-if="!orders.length && !loadingOrders" class="ord-empty">배정된 운송 오더가 없습니다.</p>
      <div v-for="o in orders" :key="o.orderId" class="ord-card">
        <div class="ord-top">
          <strong>{{ o.orderNo }}</strong>
          <span class="ord-badge">{{ ORDER_STATUS_LABEL[o.status] || o.status }}</span>
        </div>
        <div class="ord-meta">
          <span>컨테이너 {{ o.containerNo }}</span>
          <span>차량 {{ o.vehicleNo }}<template v-if="!o.myVehicle"> (내 배정 차량 아님)</template></span>
          <span>{{ o.companyName }}</span>
          <span>도착 {{ o.destHubName }}</span>
          <span>씰 {{ o.sealRegistered ? '등록됨' : '미등록' }}</span>
          <span>점검 {{ inspectionLabel(o.latestInspection) }}</span>
        </div>
        <div class="ord-btns">
          <button v-if="ACTIVE.includes(o.status) && o.myVehicle" class="ord-btn" @click="startInspection(o)">점검 작성</button>
          <button v-if="DEPARTABLE.includes(o.status) && o.myVehicle" class="ord-btn primary" :disabled="busy" @click="depart(o)">운송 출발</button>
        </div>
      </div>
    </section>

    <ContainerInspectionPanel ref="panel" />
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import ContainerInspectionPanel from '@/components/common/ContainerInspectionPanel.vue'
import { ORDER_STATUS_LABEL, myTransportOrders, departTransportOrder } from '@/utils/hubArrivalApi.js'
import { OVERALL_LABEL, RECORD_LABEL } from '@/utils/containerInspectionApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'

const ACTIVE = ['ASSIGNED', 'LOADED', 'GATE_OUT', 'IN_TRANSIT', 'ARRIVED']
const DEPARTABLE = ['ASSIGNED', 'LOADED', 'GATE_OUT']

const panel = ref(null)
const orders = ref([])
const loadingOrders = ref(false)
const busy = ref(false)
const orderMsg = ref('')
const orderErr = ref(false)

function inspectionLabel(v) {
  if (!v) return '없음'
  const [overall, record] = String(v).split('/')
  return `${OVERALL_LABEL[overall] || overall} · ${RECORD_LABEL[record] || record}`
}

async function loadOrders() {
  loadingOrders.value = true
  try {
    orders.value = await myTransportOrders()
  } catch (err) {
    orderMsg.value = friendlyError(err, '운송 오더를 불러오지 못했습니다.')
    orderErr.value = true
  } finally {
    loadingOrders.value = false
  }
}

function startInspection(o) {
  panel.value?.startFor({ containerNo: o.containerNo, orderId: o.orderId })
}

async function depart(o) {
  if (!confirm(`${o.orderNo} 운송을 출발 처리할까요?`)) return
  busy.value = true
  try {
    await departTransportOrder(o.orderId)
    orderMsg.value = `${o.orderNo} 운송 출발을 기록했습니다.`
    orderErr.value = false
    await loadOrders()
  } catch (err) {
    orderMsg.value = friendlyError(err, '출발 처리하지 못했습니다.')
    orderErr.value = true
  } finally {
    busy.value = false
  }
}

onMounted(loadOrders)
</script>

<style scoped>
.ord-wrap { background: #fff; border: 1px solid #e3e8ef; border-radius: 12px; padding: 14px 16px; margin-bottom: 14px; }
.ord-h { margin: 0 0 10px; font-size: 15.5px; font-weight: 700; }
.ord-empty { margin: 0; color: #6b7280; font-size: 13px; }
.ord-msg { margin: 0 0 8px; font-size: 13px; color: #166534; }
.ord-msg.err { color: #c81e2c; }
.ord-card { border: 1px solid #eef1f5; border-radius: 10px; padding: 10px 12px; margin-bottom: 8px; }
.ord-top { display: flex; justify-content: space-between; align-items: center; }
.ord-badge { font-size: 12px; background: #e0e7ff; color: #3730a3; border-radius: 999px; padding: 1px 9px; }
.ord-meta { display: flex; flex-wrap: wrap; gap: 4px 14px; margin: 6px 0; font-size: 13px; color: #4b5563; }
.ord-btns { display: flex; gap: 6px; }
.ord-btn { border: 1px solid #cfd6df; background: #f8fafc; border-radius: 6px; padding: 6px 12px; font-size: 13px; cursor: pointer; }
.ord-btn.primary { background: #1d4ed8; border-color: #1d4ed8; color: #fff; }
.ord-btn:disabled { opacity: .6; }
</style>
