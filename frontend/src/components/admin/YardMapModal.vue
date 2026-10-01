<!-- src/components/admin/YardMapModal.vue -->
<template>
  <div v-if="show" class="modal-backdrop fade show" @click.self="close"></div>
  <div v-if="show" class="modal d-block" tabindex="-1" @click.self="close">
    <div class="modal-dialog modal-lg modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">
            {{ selectedYard ? `${selectedYard.yardName} 위치` : '전체 야드 지도 보기' }}
          </h5>
          <button type="button" class="btn-close" @click="close"></button>
        </div>
        <div class="modal-body p-0">
          <div id="map" ref="mapContainer" style="height: 450px; width: 100%;"></div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="close">닫기</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import L from 'leaflet'

import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'

delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow
})

const props = defineProps({
  show: Boolean,
  yards: { type: Array, default: () => [] },
  selectedYard: { type: Object, default: null }
})

const emit = defineEmits(['close'])
const mapContainer = ref(null)
let map = null

function close() {
  // 모달을 닫을 때 지도 인스턴스 파괴
  destroyMap()
  emit('close')
}

function destroyMap() {
  if (map) {
    map.remove()
    map = null
  }
}

function initMap() {
  if (!mapContainer.value) return

  // 기존 지도가 남아있다면 제거
  destroyMap()

  // 1. 새 지도 생성
  map = L.map(mapContainer.value).setView([37.4810, 126.6070], 14)

  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap'
  }).addTo(map)

  const markerGroup = L.layerGroup().addTo(map)

  // 2. 단일 야드 선택 시
  if (props.selectedYard && props.selectedYard.latitude && props.selectedYard.longitude) {
    const lat = Number(props.selectedYard.latitude)
    const lng = Number(props.selectedYard.longitude)
    
    L.marker([lat, lng])
      .bindPopup(`<b>${props.selectedYard.yardName}</b>`)
      .addTo(markerGroup)

    map.setView([lat, lng], 16)
  } else {
    // 3. 전체 야드 선택 시
    const bounds = []
    const validYards = props.yards.filter(y => y.latitude && y.longitude)

    validYards.forEach(yard => {
      const lat = Number(yard.latitude)
      const lng = Number(yard.longitude)
      bounds.push([lat, lng])

      L.marker([lat, lng])
        .bindPopup(`<b>${yard.yardName || '야드'}</b><br>상태: ${yard.status || '-'}`)
        .addTo(markerGroup)
    })

    if (bounds.length > 0) {
      map.fitBounds(bounds, { padding: [50, 50], maxZoom: 16 })
    }
  }

  // 모달애니메이션이 완전히 끝난 후 지도 리사이즈 트리거 (흰 화면 방지 핵심)
  setTimeout(() => {
    if (map) map.invalidateSize()
  }, 200)
}

// show 값이 true로 바뀔 때 DOM이 그려진 후 지도 초기화
watch(() => props.show, (newVal) => {
  if (newVal) {
    nextTick(() => {
      initMap()
    })
  } else {
    destroyMap()
  }
})
</script>

<style scoped>
.modal-backdrop {
  background-color: rgba(0, 0, 0, 0.5);
}
</style>