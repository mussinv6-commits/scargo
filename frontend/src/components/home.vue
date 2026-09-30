<template>
  <div id="home">
    <!-- 공지사항 흐르는 띠 -->
    <NoticeSlide />

    <!-- 히어로 -->
    <section class="home-hero">
      <div class="home-hero-inner">
        <span class="home-kicker">SMART LOGISTICS PLATFORM</span>
        <h1 class="home-title">
          <span>못먹어도S카고</span>와 함께하는<br />
          스마트한 화물 운송 관리
        </h1>
        <p class="home-subtitle">
          기사, 업체, 관리자가 하나의 화면에서 배차부터 입출차,
          운행 이력까지 실시간으로 확인하고 관리할 수 있습니다.
        </p>
      </div>
    </section>

    <!-- 서비스 소개 -->
    <section class="home-features">
      <h2 class="home-section-title">이런 화면을 제공해요</h2>
      <p class="home-section-sub">계정 유형에 따라 필요한 정보만 골라서 보여드립니다</p>

      <div class="home-card-grid">
        <div class="home-card">
          <div class="home-card-icon orange">🚚</div>
          <h3>기사 화면</h3>
          <p>내 차량 정보, 오늘의 배차, 적재 위치 안내와 입·출차 체크인을 한 곳에서 처리할 수 있습니다.</p>
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
    </section>
  </div>
</template>

<script>
import NoticeSlide from "@/components/notice/noticeSlide.vue";

export default {
  name: "home",
  components: {
    NoticeSlide,
  },
};
</script>

<style src="@/components/CSS/home.css" scoped></style>