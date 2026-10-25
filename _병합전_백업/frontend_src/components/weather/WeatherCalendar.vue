<template>
  <div class="wcal">
    <div class="wcal-header">
      <button @click="prevMonth">&lt;</button>
      <h2>{{ year }}년 {{ month + 1 }}월</h2>
      <button @click="nextMonth">&gt;</button>
    </div>

    <div class="wcal-input">
      <input v-model="city" placeholder="도시명 (예: Seoul)" @keyup.enter="fetchWeather" />
      <button @click="fetchWeather">날씨 불러오기</button>
      <span v-if="loading">로딩중...</span>
      <span v-if="errorMsg" class="error">{{ errorMsg }}</span>
    </div>

    <div class="wcal-grid">
      <div class="wcal-dow" v-for="d in dows" :key="d">{{ d }}</div>

      <div
        v-for="(cell, idx) in calendarCells"
        :key="idx"
        class="wcal-cell"
        :class="{ empty: !cell.date, today: cell.isToday }"
        @click="cell.date && openDetail(cell)"
      >
        <template v-if="cell.date">
          <div class="date-num">{{ cell.day }}</div>
          <div v-if="weatherByDate[cell.key]" class="weather">
            <img
              :src="iconUrl(weatherByDate[cell.key].icon)"
              :alt="weatherByDate[cell.key].description"
            />
            <div class="temp">{{ weatherByDate[cell.key].temp }}°C</div>
          </div>
        </template>
      </div>
    </div>

    <!-- 상세 팝업 -->
    <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal-box">
        <div class="modal-header">
          <h3>{{ selectedKey }} 날씨 상세</h3>
          <button class="close-btn" @click="closeModal">✕</button>
        </div>

        <div class="tabs">
          <button :class="{ active: activeTab === 'day' }" @click="activeTab = 'day'">일간</button>
          <button :class="{ active: activeTab === 'week' }" @click="activeTab = 'week'">주간</button>
          <button :class="{ active: activeTab === 'month' }" @click="activeTab = 'month'">월간</button>
        </div>

        <!-- 일간: 3시간 단위 -->
        <div v-if="activeTab === 'day'" class="tab-content">
          <div v-if="dailyDetail.length === 0" class="no-data">예보 데이터가 없습니다.</div>
          <div v-else class="hour-list">
            <div v-for="h in dailyDetail" :key="h.time" class="hour-row">
              <span class="hour-time">{{ h.time }}</span>
              <img :src="iconUrl(h.icon)" :alt="h.description" class="small-icon" />
              <span class="hour-desc">{{ h.description }}</span>
              <span class="hour-temp">{{ h.temp }}°C</span>
            </div>
          </div>
        </div>

        <!-- 주간: 일~토 -->
        <div v-if="activeTab === 'week'" class="tab-content">
          <div class="week-list">
            <div v-for="w in weeklyDetail" :key="w.key" class="week-row">
              <span class="week-label">{{ w.label }}</span>
              <template v-if="w.data">
                <img :src="iconUrl(w.data.icon)" :alt="w.data.description" class="small-icon" />
                <span>{{ w.data.temp }}°C</span>
              </template>
              <span v-else class="no-data-inline">예보 없음</span>
            </div>
          </div>
        </div>

        <!-- 월간: 이번 달 전체 -->
        <div v-if="activeTab === 'month'" class="tab-content">
          <div class="month-list">
            <div v-for="m in monthlyDetail" :key="m.key" class="month-row">
              <span class="month-label">{{ m.day }}일</span>
              <template v-if="m.data">
                <img :src="iconUrl(m.data.icon)" :alt="m.data.description" class="small-icon" />
                <span>{{ m.data.temp }}°C</span>
              </template>
              <span v-else class="no-data-inline">예보 없음</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'

const API_KEY = '6b144ee4dd2306755be21d9f00eb8850'

const city = ref('Seoul')
const loading = ref(false)
const errorMsg = ref('')

const today = new Date()
const year = ref(today.getFullYear())
const month = ref(today.getMonth()) // 0-based

const dows = ['일', '월', '화', '수', '목', '금', '토']

// key: 'YYYY-MM-DD' -> 대표(정오 근접) 데이터 { temp, icon, description }
const weatherByDate = ref({})
// key: 'YYYY-MM-DD' -> 해당 날짜의 3시간 단위 원본 리스트
const hourlyByDate = ref({})

// 팝업 상태
const showModal = ref(false)
const activeTab = ref('day')
const selectedKey = ref('')

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

function dateKey(y, m, d) {
  return `${y}-${pad(m + 1)}-${pad(d)}`
}

const calendarCells = computed(() => {
  const y = year.value
  const m = month.value
  const firstDay = new Date(y, m, 1).getDay()
  const lastDate = new Date(y, m + 1, 0).getDate()
  const cells = []

  for (let i = 0; i < firstDay; i++) {
    cells.push({ date: null })
  }

  for (let d = 1; d <= lastDate; d++) {
    const isToday =
      y === today.getFullYear() && m === today.getMonth() && d === today.getDate()
    cells.push({
      date: new Date(y, m, d),
      day: d,
      key: dateKey(y, m, d),
      isToday,
    })
  }

  return cells
})

function prevMonth() {
  if (month.value === 0) {
    month.value = 11
    year.value -= 1
  } else {
    month.value -= 1
  }
}

function nextMonth() {
  if (month.value === 11) {
    month.value = 0
    year.value += 1
  } else {
    month.value += 1
  }
}

function iconUrl(icon) {
  return `https://openweathermap.org/img/wn/${icon}@2x.png`
}

async function fetchWeather() {
  if (!city.value) return
  loading.value = true
  errorMsg.value = ''
  weatherByDate.value = {}
  hourlyByDate.value = {}

  try {
    const url = `https://api.openweathermap.org/data/2.5/forecast?q=${encodeURIComponent(
      city.value
    )}&units=metric&lang=kr&appid=${API_KEY}`

    const res = await fetch(url)
    if (!res.ok) {
      throw new Error(`API 오류: ${res.status}`)
    }
    const data = await res.json()

    const grouped = {}
    const hourly = {}

    for (const item of data.list) {
      const dt = new Date(item.dt * 1000)
      const key = dateKey(dt.getFullYear(), dt.getMonth(), dt.getDate())
      const hour = dt.getHours()

      const entry = {
        temp: Math.round(item.main.temp),
        icon: item.weather[0].icon,
        description: item.weather[0].description,
      }

      // 대표값: 정오(12시)에 가장 가까운 슬롯
      if (!grouped[key] || Math.abs(hour - 12) < Math.abs(grouped[key].hour - 12)) {
        grouped[key] = { ...entry, hour }
      }

      if (!hourly[key]) hourly[key] = []
      hourly[key].push({
        time: `${pad(hour)}:00`,
        ...entry,
      })
    }

    weatherByDate.value = grouped
    hourlyByDate.value = hourly
  } catch (e) {
    errorMsg.value = e.message || '날씨 정보를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

function openDetail(cell) {
  selectedKey.value = cell.key
  activeTab.value = 'day'
  showModal.value = true
}

function closeModal() {
  showModal.value = false
}

// 일간 상세: 선택된 날짜의 3시간 단위 리스트
const dailyDetail = computed(() => {
  return hourlyByDate.value[selectedKey.value] || []
})

// 주간 상세: 선택된 날짜가 속한 일~토 7일
const weeklyDetail = computed(() => {
  if (!selectedKey.value) return []
  const [y, m, d] = selectedKey.value.split('-').map(Number)
  const base = new Date(y, m - 1, d)
  const dow = base.getDay()
  const sunday = new Date(base)
  sunday.setDate(base.getDate() - dow)

  const result = []
  for (let i = 0; i < 7; i++) {
    const dt = new Date(sunday)
    dt.setDate(sunday.getDate() + i)
    const key = dateKey(dt.getFullYear(), dt.getMonth(), dt.getDate())
    result.push({
      key,
      label: `${dows[i]} (${dt.getMonth() + 1}/${dt.getDate()})`,
      data: weatherByDate.value[key] || null,
    })
  }
  return result
})

// 월간 상세: 선택된 날짜가 속한 달 전체
const monthlyDetail = computed(() => {
  if (!selectedKey.value) return []
  const [y, m] = selectedKey.value.split('-').map(Number)
  const lastDate = new Date(y, m, 0).getDate()
  const result = []
  for (let d = 1; d <= lastDate; d++) {
    const key = dateKey(y, m - 1, d)
    result.push({
      key,
      day: d,
      data: weatherByDate.value[key] || null,
    })
  }
  return result
})

onMounted(() => {
  fetchWeather()
})
</script>

<style scoped>
.wcal {
  max-width: 560px;
  margin: 0 auto;
  font-family: sans-serif;
}
.wcal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.wcal-input {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.wcal-input input {
  padding: 4px 8px;
}
.error {
  color: red;
  font-size: 0.85em;
}
.wcal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}
.wcal-dow {
  text-align: center;
  font-weight: bold;
  padding: 4px 0;
}
.wcal-cell {
  min-height: 80px;
  border: 1px solid #ddd;
  border-radius: 6px;
  padding: 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
}
.wcal-cell:hover {
  background: #f5f5f5;
}
.wcal-cell.empty {
  border: none;
  cursor: default;
}
.wcal-cell.empty:hover {
  background: none;
}
.wcal-cell.today {
  border-color: #3b82f6;
  background: #eff6ff;
}
.date-num {
  font-size: 0.85em;
  align-self: flex-start;
}
.weather {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.weather img {
  width: 36px;
  height: 36px;
}
.temp {
  font-size: 0.8em;
}

/* 모달 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal-box {
  background: white;
  border-radius: 10px;
  padding: 16px;
  width: 360px;
  max-height: 80vh;
  overflow-y: auto;
}
.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.close-btn {
  border: none;
  background: none;
  font-size: 1.1em;
  cursor: pointer;
}
.tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 12px;
}
.tabs button {
  flex: 1;
  padding: 6px 0;
  border: 1px solid #ddd;
  background: #fafafa;
  cursor: pointer;
  border-radius: 4px;
}
.tabs button.active {
  background: #3b82f6;
  color: white;
  border-color: #3b82f6;
}
.no-data {
  color: #888;
  font-size: 0.9em;
  text-align: center;
  padding: 16px 0;
}
.hour-list,
.week-list,
.month-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.hour-row,
.week-row,
.month-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9em;
  border-bottom: 1px solid #eee;
  padding-bottom: 4px;
}
.hour-time,
.week-label,
.month-label {
  width: 80px;
  flex-shrink: 0;
}
.hour-desc {
  flex: 1;
}
.small-icon {
  width: 28px;
  height: 28px;
}
.no-data-inline {
  color: #aaa;
  font-size: 0.85em;
}
</style>
