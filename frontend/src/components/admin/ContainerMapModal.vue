<!-- src/components/admin/ContainerMapModal.vue -->
<template>
  <div v-if="show" class="modal-backdrop fade show" @click.self="close"></div>
  <div v-if="show" class="modal d-block" tabindex="-1" @click.self="close">
    <div class="modal-dialog modal-xl modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header d-flex justify-content-between align-items-center">
          <h5 class="modal-title m-0">{{ title }} (총 {{ filteredContainers.length }} / {{ validCount }}개)</h5>
          
          <!-- 🔍 검색 및 필터 컨트롤 영역 -->
          <div class="d-flex align-items-center gap-2 ms-auto me-3">
            <!-- 1. 타입별 필터 (전체 / 일반 / 냉동) -->
            <select class="form-select form-select-sm" v-model="selectedType" @change="updateMapMarkers" style="width: 130px;">
              <option value="">모든 타입</option>
              <option value="GENERAL">일반 (GENERAL)</option>
              <option value="FROZEN">냉동 (FROZEN)</option>
            </select>

            <!-- 2. 컨테이너 번호 검색 입력창 -->
            <div class="input-group input-group-sm" style="width: 200px;">
              <input 
                type="text" 
                class="form-control" 
                placeholder="번호 검색..." 
                v-model="searchQuery"
                @input="updateMapMarkers"
              />
              <button v-if="searchQuery || selectedType" class="btn btn-outline-secondary" type="button" @click="resetFilter">초기화</button>
            </div>
          </div>

          <!-- 상단 범례(Legend) 표시 -->
          <div class="d-flex align-items-center gap-2 style-legend me-3">
            <span class="badge bg-primary">● 일반</span>
            <span class="badge bg-info text-dark">● 냉동</span>
          </div>
          <button type="button" class="btn-close" @click="close"></button>
        </div>
        <div class="modal-body p-0">
          <div id="map" ref="mapContainer" style="height: 600px; width: 100%;"></div>
        </div>
        <div class="modal-footer d-flex justify-content-between">
          <small class="text-muted">💡 타입 필터 및 검색어를 조합하여 원하는 컨테이너 위치만 지도에서 확인할 수 있습니다.</small>
          <button type="button" class="btn btn-secondary" @click="close">닫기</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, computed } from 'vue'
import L from 'leaflet'

const props = defineProps({
  show: Boolean,
  title: { type: String, default: '컨테이너 위치 지도' },
  containers: { type: Array, default: () => [] }
})

const emit = defineEmits(['close'])
const mapContainer = ref(null)
let map = null
let markerGroup = null

const searchQuery = ref('')
const selectedType = ref('') // 'GENERAL' 또는 'FROZEN' 필터 상태

const validContainers = computed(() => {
  return props.containers.filter(c => c.latitude && c.longitude)
})

const validCount = computed(() => validContainers.value.length)

// 검색어 및 타입 필터가 모두 적용된 컨테이너 목록
const filteredContainers = computed(() => {
  return validContainers.value.filter(item => {
    const typeStr = String(item.containerType || '일반').toUpperCase()
    
    // 1. 타입 필터 검사
    if (selectedType.value === 'FROZEN') {
      if (!typeStr.includes('FROZEN') && !typeStr.includes('REEFER') && !typeStr.includes('냉동') && !typeStr.includes('냉장')) {
        return false
      }
    } else if (selectedType.value === 'GENERAL') {
      if (typeStr.includes('FROZEN') || typeStr.includes('REEFER') || typeStr.includes('냉동') || typeStr.includes('냉장')) {
        return false
      }
    }

    // 2. 검색어 검사 (번호 또는 화물명)
    if (searchQuery.value.trim()) {
      const query = searchQuery.value.trim().toLowerCase()
      const containerNo = (item.containerNo || '').toLowerCase()
      
      let cargoName = ''
      if (item.reservedCargoInfo) {
        try {
          const cargo = typeof item.reservedCargoInfo === 'string'
            ? JSON.parse(item.reservedCargoInfo)
            : item.reservedCargoInfo
          cargoName = (cargo.cargo_name || '').toLowerCase()
        } catch (e) {
          cargoName = ''
        }
      }

      if (!containerNo.includes(query) && !cargoName.includes(query) && !typeStr.includes(query)) {
        return false
      }
    }

    return true
  })
})

function resetFilter() {
  searchQuery.value = ''
  selectedType.value = ''
  updateMapMarkers()
}

function close() {
  destroyMap()
  emit('close')
}

function destroyMap() {
  if (map) {
    map.remove()
    map = null
    markerGroup = null
  }
}

// 🎲 고정 시드 난수 생성기
function seededRandom(seed) {
  const x = Math.sin(seed) * 10000
  return x - Math.floor(x)
}

// 🎨 컨테이너 타입별 마커 색상 판별 함수
function getMarkerColor(typeStr) {
  if (!typeStr) return '#0d6efd'
  const type = String(typeStr).toUpperCase()
  if (type.includes('FROZEN') || type.includes('REEFER') || type.includes('냉동') || type.includes('냉장')) {
    return '#0dcaf0' // 냉동 (하늘색)
  } else {
    return '#0d6efd' // 일반 (파란색)
  }
}

function initMap() {
  if (!mapContainer.value) return

  destroyMap()

  map = L.map(mapContainer.value).setView([37.4810, 126.6070], 15)

  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap'
  }).addTo(map)

  markerGroup = L.layerGroup().addTo(map)
  updateMapMarkers()
}

// 🗺️ 마커 렌더링 및 필터링 반영 함수
function updateMapMarkers() {
  if (!map || !markerGroup) return

  markerGroup.clearLayers()
  const bounds = []

  // 적재 위치별 그룹화
  const locationGroups = {}
  filteredContainers.value.forEach(item => {
    const groupKey = item.loadingLocationId 
      ? `loc_${item.loadingLocationId}` 
      : `${Number(item.latitude).toFixed(3)},${Number(item.longitude).toFixed(3)}`
    
    if (!locationGroups[groupKey]) {
      locationGroups[groupKey] = []
    }
    locationGroups[groupKey].push(item)
  })

  Object.keys(locationGroups).forEach(key => {
    const items = locationGroups[key]
    const baseLat = Number(items[0].latitude)
    const baseLng = Number(items[0].longitude)

    items.forEach((item, index) => {
      let lat = baseLat
      let lng = baseLng

      if (items.length > 1) {
        const idSeed = item.containerId || (index + 1) * 997
        const seed1 = idSeed * 1337 + (item.loadingLocationId || 1) * 31
        const seed2 = seed1 + 7829

        const minRadius = 0.00008
        const maxRadius = 0.00025
        const randomR = minRadius + seededRandom(seed1) * (maxRadius - minRadius)
        const randomAngle = seededRandom(seed2) * 2 * Math.PI

        lat += Math.sin(randomAngle) * randomR
        lng += Math.cos(randomAngle) * randomR * 1.25
      }

      bounds.push([lat, lng])

      let cargoName = '-'
      if (item.reservedCargoInfo) {
        try {
          const cargo = typeof item.reservedCargoInfo === 'string'
            ? JSON.parse(item.reservedCargoInfo)
            : item.reservedCargoInfo
          cargoName = cargo.cargo_name || '-'
        } catch (e) {
          cargoName = '-'
        }
      }

      const containerNo = item.containerNo || '번호없음'
      const isoCode = item.isoSizeTypeCode || item.isoCode || '-'
      const type = item.containerType || '일반'
      const sector = item.sector || `위치 #${item.loadingLocationId}`
      const markerColor = getMarkerColor(type)

      const marker = L.circleMarker([lat, lng], {
        radius: 6,
        color: '#ffffff',
        weight: 1.5,
        fillColor: markerColor,
        fillOpacity: 0.95
      })

      marker.bindTooltip(`<b>${containerNo}</b> (${type})`, {
        permanent: false,
        direction: 'top'
      })

      marker.bindPopup(`
        <div style="font-size: 13px; line-height: 1.6; min-width: 190px;">
          <div style="font-size: 14px; font-weight: bold; color: ${markerColor}; border-bottom: 1px solid #eee; padding-bottom: 4px; margin-bottom: 6px;">
            📦 ${containerNo}
          </div>
          <b>ISO 규격:</b> ${isoCode}<br/>
          <b>타입:</b> ${type}<br/>
          <b>적재 섹터:</b> ${sector}<br/>
          <b>화물 명칭:</b> ${cargoName}
        </div>
      `)

      marker.addTo(markerGroup)
    })
  })

  if (bounds.length > 0) {
    map.fitBounds(bounds, { padding: [50, 50], maxZoom: 17 })
  }
}

watch(() => props.show, (newVal) => {
  if (newVal) {
    searchQuery.value = ''
    selectedType.value = ''
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

.style-legend span {
  font-size: 12px;
  font-weight: 500;
}
</style>