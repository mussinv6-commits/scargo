<template>
  <div class="map-container">
    <div id="map" ref="mapContainer"></div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

const mapContainer = ref(null);

const locationMap = {
  1: { id: 1, name: "1번 위치", lat: 37.48361, lng: 126.60833 },
  2: { id: 2, name: "2번 위치", lat: 37.48264, lng: 126.60692 },
  3: { id: 3, name: "3번 위치", lat: 37.48272, lng: 126.60406 },
  4: { id: 4, name: "4번 위치", lat: 37.48073, lng: 126.61138 },
  5: { id: 5, name: "5번 위치", lat: 37.47928, lng: 126.61224 },
  6: { id: 6, name: "6번 위치", lat: 37.47995, lng: 126.60932 },
  7: { id: 7, name: "7번 위치", lat: 37.47903, lng: 126.60836 },
  8: { id: 8, name: "8번 위치", lat: 37.47921, lng: 126.60554 },
  9: { id: 9, name: "9번 위치", lat: 37.47988, lng: 126.60175 },
};

const containerList = ref([
  { containerNo: 'SUDU9939088', loadingLocationId: 1, containerType: '일반' },
  { containerNo: 'PONU8142250', loadingLocationId: 1, containerType: '일반' },
  { containerNo: 'APLU2251273', loadingLocationId: 2, containerType: '일반' },
  { containerNo: 'ZIMU7604207', loadingLocationId: 2, containerType: '일반' },
  { containerNo: 'HJSU4451408', loadingLocationId: 3, containerType: '일반' },
  { containerNo: 'CMAU9261313', loadingLocationId: 3, containerType: '냉동' },
  { containerNo: 'MSCU2318213', loadingLocationId: 4, containerType: '일반' },
  { containerNo: 'APLU6355810', loadingLocationId: 4, containerType: '냉동' },
]);

onMounted(() => {
  const map = L.map(mapContainer.value).setView([37.4810, 126.6070], 15);

  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap'
  }).addTo(map);

  const locationCounts = {};

  containerList.value.forEach(container => {
    const locId = container.loadingLocationId;
    const baseLoc = locationMap[locId];

    if (!baseLoc) return;

    locationCounts[locId] = (locationCounts[locId] || 0) + 1;
    const index = locationCounts[locId];

    let lat = baseLoc.lat;
    let lng = baseLoc.lng;

    // 흩뿌리기 오프셋 적용
    if (index > 1) {
      const angle = (index - 2) * (Math.PI / 3);
      const radius = 0.00025 + Math.floor((index - 2) / 6) * 0.00015;
      
      lat += radius * Math.cos(angle);
      lng += radius * Math.sin(angle) * 1.2;

      L.polyline([[baseLoc.lat, baseLoc.lng], [lat, lng]], {
        color: '#2b6cb0',
        weight: 1,
        dashArray: '3, 3',
        opacity: 0.6
      }).addTo(map);
    }

    // SVG 기반 원형 마커 생성 (반지름 radius: 6 ~ 8px로 설정)
    const circle = L.circleMarker([lat, lng], {
      radius: 7,             // 마커 반지름 크기 (전체 지름 14px)
      color: '#ffffff',      // 테두리 색상
      weight: 2,             // 테두리 두께
      fillColor: '#2b6cb0',  // 내부 채우기 색상
      fillOpacity: 0.95
    }).addTo(map);

    // 마커 클릭 팝업
    circle.bindPopup(`
      <div style="font-size: 13px; line-height: 1.5;">
        <strong style="color: #2b6cb0; font-size: 14px;">📦 ${container.containerNo}</strong><br>
        구분: ${container.containerType}<br>
        적재 위치: ${baseLoc.name} (순번: ${index})
      </div>
    `);

    // (선택 사항) 툴팁으로 번호나 마커 정보 표시
    circle.bindTooltip(`${container.containerNo}`, {
      permanent: false,
      direction: 'top'
    });
  });
});
</script>

<style scoped>
.map-container {
  width: 100%;
  height: 500px;
}

#map {
  width: 100%;
  height: 100%;
  border-radius: 8px;
}
</style>