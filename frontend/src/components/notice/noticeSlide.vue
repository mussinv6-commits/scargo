<template>
<!--공지사항 슬라이드 전체 영역-->
<div class="notice-slide">
  <!-- 화면 보여줄 영역 -->
  <div class="notice-content">
    <!-- 현재 공지의 종류 표시 -->
      <span class="notice-type">
        {{ currentNotice.type }}
      </span>

      <!-- 현재 공지 제목 표시 -->
      <span
        class="notice-title"
        @click="goDetail(currentNotice.seq)"
      >
        {{ currentNotice.title }}
      </span>

    </div>

    <!-- 현재 공지의 날짜 표시 -->
    <div class="notice-date">
      {{ currentNotice.date }}
    </div>
</div>
    
</template>


<script>
// Vue의 컴포넌트 영역 시작

export default {

  // 현재 Vue 컴포넌트의 이름
  name: "noticeslide",

  data() {

    // 화면에서 사용할 데이터를 반환
    return {

      // 현재 보여주고 있는 공지의 순서
      currentIndex: 0,

      // ⭐ 테스트용 공지사항 데이터
      // 나중에는 백엔드 DB에서 axios로 받아오면 됨
      notices: [
        {
          seq: 10,
          // 게시글 번호

          type: "일반",
          // 공지 종류

          title: "내부점검에 따른 서비스 일시중단 안내(9/17(목))",
          // 공지 제목

          date: "2026-09-14"
          // 작성 날짜
        },

        {
          seq: 9,
          type: "필독",
          title: "(필독) EMPTY 반출입 제한 안내(26년 9월 10일)",
          date: "2026-09-10"
        },

        {
          seq: 8,
          type: "긴급",
          title: "(긴급공지) GATE 반출입 운영 안내(26년 9월 10일)",
          date: "2026-09-10"
        }
      ],

       
      // 자동으로 다음 공지로 넘어가기 위한 타이머
      slideTimer: null
    };
  },


  computed: {

    // ⭐ 현재 화면에 보여줄 공지 1개
    currentNotice() {

      // notices 배열에서 currentIndex에 해당하는 공지를 가져옴
      return this.notices[this.currentIndex];
    }
  },


  mounted() {

    // 컴포넌트가 화면에 나타나면 자동 슬라이드를 시작
    this.startSlide();
  },


  beforeUnmount() {

    // 컴포넌트가 화면에서 사라지면 타이머를 제거
    clearInterval(this.slideTimer);
  },


  methods: {

    // ⭐ 자동으로 다음 공지로 이동
    startSlide() {

      // 3초마다 다음 공지로 변경
      this.slideTimer = setInterval(() => {

        this.nextNotice();

      }, 3000);
    },


    // 다음 공지
    nextNotice() {

      // 현재 번호를 1 증가
      this.currentIndex++;

      // 마지막 공지까지 갔다면 처음 공지로 돌아감
      if (this.currentIndex >= this.notices.length) {

        this.currentIndex = 0;
      }
    },


    // 이전 공지
    prevNotice() {

      // 현재 번호를 1 감소
      this.currentIndex--;

      // 첫 번째 공지보다 앞이면 마지막 공지로 이동
      if (this.currentIndex < 0) {

        this.currentIndex = this.notices.length - 1;
      }
    },


    // ⭐ 공지 제목을 클릭했을 때 상세 페이지로 이동
    goDetail(seq) {

      // Vue Router를 이용해 공지 상세 페이지로 이동
      this.$router.push({

        // router에 등록한 이름
        name: "noticeDetail",

        // 게시글 번호 전달
        params: {
          id: seq
        }
      });
    }
  }
};
</script>


<style scoped src="./noticeSlide.css"></style>
