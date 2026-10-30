<template>
  <!-- 26.10.26 S-Bot 허브 관제에서 이동: 게이트 OCR 로 찍힌 차량번호를 누르면 인식 사진 + 씰 대조 -->
  <div class="gs">
    <div class="gs-head">
      <h2>허브 게이트 OCR · 씰 확인</h2>
      <span class="gs-sub">입출차 번호판 인식 기록 · 차량번호를 누르면 인식 사진과 씰 대조 칸이 나옵니다</span>
      <button type="button" class="gs-btn gs-ref" @click="load">새로고침</button>
    </div>
    <p v-if="msg" class="gs-msg">{{ msg }}</p>
    <div class="gs-body">
      <table class="gs-tbl">
        <thead><tr><th>시각</th><th>구분</th><th>차량번호(OCR)</th><th>종류</th><th>씰 상태</th></tr></thead>
        <tbody>
          <tr v-if="!rows.length"><td colspan="5" class="gs-empty">입출차 기록이 아직 없습니다</td></tr>
          <tr v-for="v in rows" :key="v.id" :class="{ pick: pick && pick.id === v.id }" @click="select(v)">
            <td>{{ tm(v) }}</td>
            <td>{{ v.direction === 'EXIT' ? '출차' : '입차' }}</td>
            <td><b>{{ fmt(v.plate) || '판독불가' }}</b></td>
            <td>{{ KIND[v.kind] || '' }}</td>
            <td><span class="gs-chip" :class="chip(v).c">{{ chip(v).t }}</span></td>
          </tr>
        </tbody>
      </table>
      <aside class="gs-det" v-if="pick">
        <b class="gs-t">{{ fmt(pick.plate) || '판독불가' }}<template v-if="seal"> · {{ seal.a }}</template></b>
        <p class="gs-s" v-if="seal">{{ sealMsg || seal.b }}</p>
        <!-- 26.10.29 QA: 등록 씰 번호는 대조 전에 보여 주지 않는다(현장에서 실제 씰을 보고 입력). 미등록이면 입력칸 대신 대기 안내, 일치하면 입력칸을 닫는다 -->
        <div v-if="seal && pick.sealInput && pick.sealStatus !== 'MISSING'" class="gs-res" :class="pick.sealStatus === 'OK' ? 'ok' : 'bad'">
          <template v-if="pick.sealStatus === 'OK'">현장 씰 <b>{{ pick.sealInput }}</b> · 기사 앱 등록 씰과 일치</template>
          <template v-else>현장 씰 <b>{{ pick.sealInput }}</b> · 기사 앱 등록 씰과 다름 · 관리자 확인 필요</template>
        </div>
        <div class="gs-in" v-if="seal && canManage && pick.sealStatus !== 'MISSING' && pick.sealStatus !== 'OK'">
          <input v-model="sealNo" placeholder="현장 씰 번호" autocomplete="off" @keyup.enter="check" />
          <button type="button" class="gs-btn" :disabled="!sealNo.trim()" @click="check">대조</button>
        </div>
        <p class="gs-s" v-else-if="seal && !canManage && pick.sealStatus !== 'MISSING' && pick.sealStatus !== 'OK'">조회 전용 계정은 씰 대조를 할 수 없습니다</p>
        <div v-if="pick.imageUrl" class="gs-ph">
          <img :src="photo" alt="인식 사진" @error="onImgErr" />
          <p>사진 {{ fmt(pick.photoPlate) || '-' }} → 인식 {{ fmt(pick.plate) || '판독 불가' }}</p>
        </div>
        <p v-else class="gs-s">이 기록에는 인식 사진이 없습니다</p>
      </aside>
      <aside class="gs-det gs-none" v-else>왼쪽 목록에서 차량번호를 눌러 주세요</aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import { amrApi } from "@/components/amr/amrApi.js";
import { authState } from "@/auth/authState.js";
import { hubAccess } from "@/components/amr/amrAccess.js";

const KIND = { CARGO: "화물차", COURIER: "택배차", UNKNOWN: "미등록" };
const rows = ref([]); const pick = ref(null); const msg = ref(""); const sealNo = ref(""); const sealMsg = ref(""); const photo = ref("");
let timer = null;
const canManage = computed(() => { try { return !!hubAccess(authState.user).canManage; } catch { return true; } });
const fmt = (p) => (p ? String(p).replace(/\s/g, "").replace(/^(\D*)(\d{2,3})(\D)(\d{4})$/, (m, a, b, c, d) => (a ? a + " " : "") + b + c + " " + d) : "");
const tm = (v) => { const s = v.createdAt || v.at || v.scannedAt || v.time || v.loggedAt; if (!s) return "-"; const d = new Date(typeof s === "number" && s < 1e12 ? s * 1000 : s); return isNaN(d) ? "-" : d.toLocaleTimeString("ko-KR", { hour12: false }); };
const M = { PENDING: ["씰 대조 대기", "컨테이너에 붙은 씰 번호를 직접 보고 입력하세요"], OK: ["씰 대조 일치 · 하차 가능", "등록 번호와 일치"], MISMATCH: ["씰 불일치 · 하차 보류", "관리자 확인 필요"], MISSING: ["씰 미등록 · 게이트 대기", "기사 앱 씰 등록 대기 · 기사가 앱에서 씰을 등록하면 대조할 수 있습니다"] };
const seal = computed(() => {
  const v = pick.value;
  if (!v || !(v.direction === "ENTRY" && v.kind === "CARGO" && v.plate && v.sealStatus)) return null;
  const [a, b] = M[v.sealStatus] || M.PENDING;
  return { a, b };
});
function chip(v) {
  if (!(v.direction === "ENTRY" && v.kind === "CARGO" && v.sealStatus)) return { t: "-", c: "" };
  return { OK: { t: "씰 일치", c: "ok" }, PENDING: { t: "대조 대기", c: "wait" }, MISMATCH: { t: "불일치", c: "bad" }, MISSING: { t: "미등록", c: "bad" } }[v.sealStatus] || { t: "-", c: "" };
}
async function load() {
  const r = await amrApi.vehicles(30);
  if (!r) { msg.value = "서버에 연결하지 못했거나 권한이 없습니다"; return; }
  msg.value = ""; rows.value = Array.isArray(r) ? r : r.items || [];
  if (pick.value) { const n = rows.value.find((x) => x.id === pick.value.id); if (n) pick.value = n; }
}
function select(v) {
  pick.value = pick.value && pick.value.id === v.id ? null : v; sealNo.value = ""; sealMsg.value = "";
  if (pick.value && v.imageUrl) photo.value = amrApi.photoUrl(v.id);
}
function onImgErr() { if (pick.value && pick.value.imageUrl && photo.value !== pick.value.imageUrl) photo.value = pick.value.imageUrl; }
async function check() {
  const v = pick.value, val = sealNo.value.trim(); if (!v || !val) return;
  const r = await amrApi.sealCheck(v.id, val);
  if (!r) { sealMsg.value = "씰 확인 실패 · 서버 연결을 확인하세요"; return; }
  const i = rows.value.findIndex((x) => x.id === v.id); if (i >= 0) rows.value[i] = r;
  pick.value = r; sealNo.value = ""; sealMsg.value = "";
}
onMounted(() => { amrApi.useCookieAuth(); load(); timer = setInterval(load, 5000); });
onBeforeUnmount(() => clearInterval(timer));
</script>

<style scoped>
.gs { padding: 4px 2px; color: #102644; }
.gs-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.gs-head h2 { margin: 0; font-size: 20px; }
.gs-sub { color: #6b7280; font-size: 13px; }
.gs-ref { margin-left: auto; }
.gs-msg { color: #c62828; margin: 0 0 8px; }
.gs-body { display: grid; grid-template-columns: 1.3fr 1fr; gap: 16px; align-items: start; }
.gs-tbl { width: 100%; border-collapse: collapse; background: #fff; border-radius: 10px; overflow: hidden; box-shadow: 0 1px 3px #0001; font-size: 14px; }
.gs-tbl th, .gs-tbl td { padding: 9px 10px; border-bottom: 1px solid #e8ecf1; text-align: left; }
.gs-tbl th { background: #f4f6f9; color: #102644; }
.gs-tbl tbody tr { cursor: pointer; }
.gs-tbl tbody tr:hover { background: #f8fafc; }
.gs-tbl tbody tr.pick { background: #fff7e0; }
.gs-empty { text-align: center; color: #8a94a6; padding: 24px; cursor: default; }
.gs-chip { display: inline-block; padding: 1px 9px; border-radius: 10px; font-size: 12px; background: #eef1f5; color: #667; }
.gs-chip.ok { background: #dff3e6; color: #1b7f3b; font-weight: 700; } .gs-chip.wait { background: #ffe9b8; color: #8a5a00; } .gs-chip.bad { background: #fde1e1; color: #c62828; font-weight: 700; }
.gs-det { background: #fff; border: 1px solid #dde3ea; border-radius: 10px; padding: 14px; }
.gs-none { color: #8a94a6; text-align: center; padding: 40px 14px; }
.gs-t { font-size: 16px; display: block; }
.gs-s { margin: 4px 0 8px; color: #6b7280; font-size: 13px; }
.gs-res { margin: 8px 0; padding: 9px 12px; border-radius: 8px; font-size: 13.5px; }
.gs-res.ok { background: #e3f6ea; color: #1b7a3d; } .gs-res.bad { background: #fde4e4; color: #c62828; }
.gs-in { display: flex; gap: 6px; margin: 8px 0; }
.gs-in input { flex: 1; min-width: 0; padding: 8px; border: 1px solid #c9d1dc; border-radius: 6px; text-transform: uppercase; }
.gs-btn { padding: 8px 16px; border: 0; border-radius: 6px; background: #102644; color: #fff; font-weight: 700; cursor: pointer; }
.gs-btn:disabled { opacity: .5; cursor: default; }
.gs-ph img { width: 100%; border-radius: 8px; display: block; background: #eef1f5; min-height: 80px; }
.gs-ph p { margin: 6px 0 0; font-size: 12px; color: #6b7280; }
@media (max-width: 900px) { .gs-body { grid-template-columns: 1fr; } }
</style>
