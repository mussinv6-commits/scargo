<template>
  <div id="home">
    <!-- 공지사항 흐르는 띠 -->
    <NoticeSlide />

    <!-- 히어로: 문구 고정, 배경 3장 자동 전환 -->
    <section class="home-hero" @mouseenter="pauseSlides" @mouseleave="playSlides">
      <div
        v-for="(slide, i) in slides"
        :key="slide.src"
        class="home-slide"
        :class="['is-' + slide.strength, { on: i === slideIndex }]"
        :style="{ backgroundImage: `url(${slide.src})` }"
      ></div>
      <div class="home-hero-inner">
        <div class="home-hero-text">
          <span class="home-kicker">SMART LOGISTICS PLATFORM</span>
          <h1 class="home-title">
            멈추지 않는 흐름,<br />
            <span>효율적인 물류 시스템</span>
          </h1>
          <p class="home-subtitle">
            배차, 차량, 운송, 관제 정보를 하나로 통합하여
            더 안전하고 효율적인 화물 운송을 관리합니다.
          </p>
        </div>
      </div>
      <div class="home-slide-dots" role="tablist" aria-label="홈 배경">
        <button
          v-for="(slide, i) in slides"
          :key="slide.label"
          type="button"
          role="tab"
          :aria-selected="i === slideIndex"
          :aria-label="slide.label"
          :class="{ on: i === slideIndex }"
          @click="goSlide(i)"
        ></button>
      </div>
    </section>

    <section class="home-lower">
    <!-- 서비스 소개. 오른쪽 날씨 위젯 너비만큼 카드 영역을 줄인다 -->
    <div class="home-features">
      <h2 class="home-section-title">이런 화면을 제공해요</h2>
      <p class="home-section-sub">계정 유형에 따라 필요한 정보만 골라서 보여드립니다</p>

      <div class="home-card-grid">
        <div class="home-card">
          <div class="home-card-icon orange">🚚</div>
          <h3>기사 화면</h3>
          <p>내 차량 정보와 배차·운행 현황을 한 곳에서 확인할 수 있습니다.</p>
        </div>

        <div class="home-card">
          <div class="home-card-icon navy">🏢</div>
          <h3>업체 화면</h3>
          <p>소속 차량과 기사 현황, 운행 이력을 한눈에 모아보고 필요한 정보를 빠르게 확인할 수 있습니다.</p>
        </div>

        <div class="home-card">
          <div class="home-card-icon sky">📋</div>
          <h3>공지 및 안내</h3>
          <p>야드 혼잡도, 운영 공지 등 도로·현장 정보를 실시간에 가깝게 확인할 수 있습니다.</p>
        </div>
      </div>
    </div>
    <aside class="home-weather-slot">
      <WeatherWidget />
    </aside>
    </section>
  </div>
</template>

<script>
import NoticeSlide from "@/components/notice/noticeSlide.vue";
import WeatherWidget from "@/components/home/WeatherWidget.vue";
import slideDay from "@/assets/home/main_slide_01_terminal_day.png";
import slideSunset from "@/assets/home/main_slide_02_port_sunset.png";
import slideEvening from "@/assets/home/main_slide_03_port_evening.png";

const SLIDE_MS = 5500;

export default {
  name: "home",
  components: {
    NoticeSlide,
    WeatherWidget,
  },
  data() {
    return {
      slideIndex: 0,
      slideTimer: null,
      slides: [
        { src: slideDay, strength: "strong", label: "낮, 컨테이너 터미널" },
        { src: slideSunset, strength: "medium", label: "석양, 대형 선박" },
        { src: slideEvening, strength: "soft", label: "저녁, 불 켜진 항만" },
      ],
    };
  },
  mounted() {
    this.playSlides();
  },
  beforeUnmount() {
    this.pauseSlides();
  },
  methods: {
    playSlides() {
      this.pauseSlides();
      this.slideTimer = setInterval(() => {
        this.slideIndex = (this.slideIndex + 1) % this.slides.length;
      }, SLIDE_MS);
    },
    pauseSlides() {
      clearInterval(this.slideTimer);
      this.slideTimer = null;
    },
    goSlide(i) {
      this.slideIndex = i;
      this.playSlides();
    },
  },
};
</script>

<style src="@/components/CSS/home.css" scoped></style>