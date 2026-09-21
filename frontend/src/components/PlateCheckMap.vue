<template>
  <div class="plate-map-wrap">
    <div class="status">
      현재 단계: <span class="stage">{{ stage }}</span>
      <div class="plate">{{ plateText }}</div>
    </div>

    <div class="panel">
      <h3>① 게이트 통과 - 번호판 검사</h3>
      <input type="file" ref="fileInputEl" accept="image/*" />
      <button :disabled="inspecting" @click="onInspect">사진 업로드해서 검사</button>
      <div class="result">{{ inspectResult }}</div>

      <h3 style="margin-top:14px">② 검사소 이동</h3>
      <button :disabled="!gateDone" @click="onOverload">과적 체크 진행</button>
    </div>

    <div ref="mapEl" class="map"></div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";

// FastAPI 서버 주소 - uvicorn app.main:app --port 8000 로 띄운 상태여야 함
const FASTAPI_URL = "http://localhost:8000";
const KAKAO_APP_KEY = import.meta.env.VITE_KAKAO_APP_KEY;

const mapEl = ref(null);
const fileInputEl = ref(null);

const stage = ref("적재장 대기 중");
const plateText = ref("");
const inspectResult = ref("");
const inspecting = ref(false);
const gateDone = ref(false);

// Vue 반응형으로 관리 안 해도 되는 지도 관련 객체들
let kakaoRef = null;
let truckOverlay = null;
let path = [];

function loadKakaoSdk() {
  return new Promise((resolve, reject) => {
    if (window.kakao && window.kakao.maps) {
      resolve(window.kakao);
      return;
    }
    const script = document.createElement("script");
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_APP_KEY}&autoload=false`;
    script.onload = () => window.kakao.maps.load(() => resolve(window.kakao));
    script.onerror = reject;
    document.head.appendChild(script);
  });
}

function interpolate(kakao, a, b, t) {
  return new kakao.maps.LatLng(
    a.getLat() + (b.getLat() - a.getLat()) * t,
    a.getLng() + (b.getLng() - a.getLng()) * t
  );
}

function animateSegment(fromIdx, toIdx) {
  const STEPS = 60;
  return new Promise((resolve) => {
    let step = 0;
    const timer = setInterval(() => {
      const t = step / STEPS;
      truckOverlay.setPosition(interpolate(kakaoRef, path[fromIdx], path[toIdx], t));
      step++;
      if (step > STEPS) {
        clearInterval(timer);
        resolve();
      }
    }, 40);
  });
}

onMounted(async () => {
  const kakao = await loadKakaoSdk();
  kakaoRef = kakao;

  const CENTER = new kakao.maps.LatLng(37.34, 126.545);
  const map = new kakao.maps.Map(mapEl.value, { center: CENTER, level: 4 });

  // 체크포인트 3곳: 적재장 -> 게이트(번호판인식) -> 검사소(과적체크)
  const checkpoints = [
    { name: "적재장", lat: 37.341, lng: 126.543 },
    { name: "게이트", lat: 37.3402, lng: 126.545 },
    { name: "검사소", lat: 37.3393, lng: 126.5468 },
  ];

  checkpoints.forEach((cp) => {
    new kakao.maps.Marker({
      position: new kakao.maps.LatLng(cp.lat, cp.lng),
      map,
    });
    new kakao.maps.CustomOverlay({
      position: new kakao.maps.LatLng(cp.lat, cp.lng),
      content: `<div style="background:#fff;padding:4px 8px;border-radius:6px;font-size:12px;border:1px solid #E4E5E8;transform:translateY(-32px)">${cp.name}</div>`,
      map,
    });
  });

  path = checkpoints.map((cp) => new kakao.maps.LatLng(cp.lat, cp.lng));
  new kakao.maps.Polyline({
    path,
    strokeWeight: 4,
    strokeColor: "#E2A30A",
    strokeOpacity: 0.9,
    strokeStyle: "solid",
    map,
  });

  const truckEl = document.createElement("div");
  truckEl.innerText = "🚛";
  truckEl.style.fontSize = "26px";
  truckEl.style.transform = "translate(-50%, -50%)";

  truckOverlay = new kakao.maps.CustomOverlay({
    position: path[0],
    content: truckEl,
    map,
  });
});

// ---- ① 번호판 검사: FastAPI(/api/plates/inspect)에 사진 업로드 ----
async function onInspect() {
  const file = fileInputEl.value.files[0];
  if (!file) {
    inspectResult.value = "사진을 먼저 선택하세요.";
    return;
  }

  inspecting.value = true;
  inspectResult.value = "검사 중...";

  try {
    const formData = new FormData();
    formData.append("file", file);

    const res = await fetch(`${FASTAPI_URL}/api/plates/inspect`, {
      method: "POST",
      body: formData,
    });
    if (!res.ok) throw new Error(`서버 응답 오류 (${res.status})`);

    const data = await res.json();
    const plate = data.plate_candidates && data.plate_candidates[0];

    if (plate) {
      inspectResult.value = `인식됨: ${plate}`;
      plateText.value = `번호판: ${plate}`;
    } else {
      inspectResult.value = "번호판 후보를 찾지 못함 (판독불가)";
      plateText.value = "";
    }

    // 인식 결과와 상관없이 게이트까지는 이동 (실패 처리 로직은 추후 추가)
    stage.value = "게이트 통과 중";
    await animateSegment(0, 1);
    stage.value = "번호판 인식 완료";
    gateDone.value = true;
  } catch (err) {
    inspectResult.value = `연결 실패: ${err.message} (FastAPI 서버가 켜져 있는지 확인)`;
  } finally {
    inspecting.value = false;
  }
}

// ---- ② 과적 체크: 지금은 FastAPI에 해당 로직이 없어 이동만 시뮬레이션 ----
async function onOverload() {
  gateDone.value = false;
  stage.value = "검사소로 이동 중";
  await animateSegment(1, 2);
  stage.value = "과적 체크 중 (임시 시뮬레이션)";
}
</script>

<style scoped>
.plate-map-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 500px;
  font-family: "맑은 고딕", sans-serif;
}

.map {
  width: 100%;
  height: 100%;
  min-height: 500px;
}

.status {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 10;
  background: #25262a;
  color: #fff;
  padding: 12px 18px;
  border-radius: 8px;
  font-size: 15px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  max-width: 320px;
}
.status .stage {
  color: #e2a30a;
  font-weight: bold;
}
.status .plate {
  color: #fbeecb;
  font-size: 13px;
  margin-top: 4px;
}

.panel {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 10;
  background: #fff;
  border: 1px solid #e4e5e8;
  border-radius: 8px;
  padding: 14px;
  font-size: 13px;
  width: 240px;
}
.panel h3 {
  margin: 0 0 8px;
  font-size: 14px;
}
.panel input[type="file"] {
  width: 100%;
  margin-bottom: 8px;
  font-size: 12px;
}
.panel button {
  width: 100%;
  margin-bottom: 6px;
  padding: 8px;
  border: none;
  border-radius: 6px;
  background: #e2a30a;
  color: #25262a;
  font-weight: bold;
  cursor: pointer;
}
.panel button:disabled {
  background: #e4e5e8;
  color: #aeb0b8;
  cursor: not-allowed;
}
.panel .result {
  margin-top: 8px;
  font-size: 12px;
  color: #4a4c53;
}
</style>
