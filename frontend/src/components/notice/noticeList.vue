<template>
  <div id="notice-list">
    <br /><br />

    <!-- 공지사항 목록 테이블 -->
    <table class="table notice-table">

      <thead>
        <tr>
          <th style="width: 80px;">번호</th>
          <th style="width: 100px;">구분</th>
          <th>제목 및 내용</th>
          <th style="width: 120px;" class="text-end">등록일</th>
        </tr>
      </thead>

      <tbody>

        <!-- 공지사항 목록 -->
        <!--
          notice = 현재 공지사항 한 개의 데이터
          index = 현재 목록에서 몇 번째인지 나타내는 번호
          index는 0부터 시작
        -->
        <tr
          v-for="(notice, index) in noticeList"
          :key="notice.postId"
        >

          <!--
            게시판에서 보여줄 번호

            DB의 postId를 직접 보여주는 것이 아니라
            전체 게시글 개수(cnt)를 기준으로
            1, 2, 3, 4... 형태의 게시판 번호를 계산함

            예)
            전체 글이 4개라면

            첫 번째 글 → 4
            두 번째 글 → 3
            세 번째 글 → 2
            네 번째 글 → 1

            ※ DB의 postId 값은 변경되지 않음
          -->
          <td class="align-middle text-muted">
            {{ cnt - ((pageNumber - 1) * 10) - index }}
          </td>


          <!-- 현재 프로젝트는 공지사항만 사용 -->
          <td class="align-middle">

            <!-- 공지사항이라는 표시 -->
            <span class="badge-type badge-default">
              공지
            </span>

          </td>


          <!-- 제목 클릭 -->
          <td
            class="align-middle"

            <!--
              제목을 클릭하면
              실제 DB의 postId를 사용해서
              상세 페이지로 이동
            -->
            @click="noticeDetail(notice.postId)"

            style="cursor: pointer;"
          >

            <!-- 작성자 -->
            <div class="writer-text text-muted small">
              {{ notice.writer }}
            </div>

            <!-- 제목 -->
            <div class="title-text text-truncate">
              {{ notice.title }}
            </div>

          </td>


          <!-- DB : posts.created_at -->
          <td class="align-middle text-end text-muted small">

            <!--
              createdAt 날짜를
              YYYY-MM-DD 형태로 표시
            -->
            {{ formatDate(notice.createdAt) }}

          </td>

        </tr>


        <!-- 공지사항이 없을 경우 -->
        <tr v-if="noticeList.length === 0">

          <td
            colspan="4"
            class="text-center py-5 text-muted"
          >
            등록된 공지사항이 없습니다.
          </td>

        </tr>

      </tbody>

    </table>


    <!-- 페이징 및 검색 -->
    <div class="overflow-auto mt-4">

      <!-- 페이징 -->
      <b-pagination

        <!-- 현재 페이지 -->
        v-model="pageNumber"

        <!-- 전체 게시글 개수 -->
        :total-rows="cnt"

        <!-- 한 페이지에 10개 -->
        :per-page="10"

        align="center"

        <!-- 페이지를 클릭했을 때 실행 -->
        @page-click="pageClick"

      ></b-pagination>


      <!-- 검색 -->
      <div
        class="d-flex justify-content-center align-items-center mt-3"
      >

        <div style="width: 250px;">

          <!-- 검색어 입력 -->
          <input
            v-model="keyword"
            placeholder="공지사항 검색"
            class="form-control form-control-sm"

            <!-- 엔터를 누르면 검색 -->
            @keyup.enter="searchBtn"
          />

        </div>

        &nbsp;

        <!-- 검색 버튼 -->
        <button
          @click="searchBtn"
          class="btn btn-primary btn-sm px-3"
        >
          검색
        </button>

      </div>

    </div>


    <!-- 글쓰기 버튼 -->
    <div class="text-end mt-3">

      <router-link
        to="/notice/write"
        class="btn btn-outline-primary btn-sm"
      >
        글쓰기
      </router-link>

    </div>


    <br /><br />

  </div>
</template>


<script>

import axios from "axios";


export default {

  data() {

    return {

      // ========================================
      // 공지사항 목록
      // ========================================
      noticeList: [],


      // ========================================
      // 검색어
      // ========================================
      keyword: "",


      // ========================================
      // 현재 페이지
      // ========================================
      // 1페이지부터 시작
      pageNumber: 1,


      // ========================================
      // 전체 게시글 개수
      // ========================================
      // 게시판 번호를 계산할 때 사용
      cnt: 0

    };

  },


  mounted() {

    // ========================================
    // 페이지가 열리면 공지사항 목록 조회
    // ========================================
    this.getNoticeList();

  },


  methods: {


    // ========================================
    // 공지사항 목록 조회
    // ========================================
    getNoticeList() {

      const params = {

        // ========================================
        // Spring Pageable은 0페이지부터 시작
        //
        // Vue의 페이지
        // 1페이지 → Spring 0페이지
        // 2페이지 → Spring 1페이지
        // ========================================
        page: this.pageNumber - 1,


        // ========================================
        // 한 페이지에 10개씩 조회
        // ========================================
        size: 10

      };


      // ========================================
      // Spring Boot 서버에 공지사항 목록 요청
      // ========================================
      axios

        .get(
          "http://localhost:3000/api/v1/posts/category/NOTICE",
          { params }
        )

        .then((resp) => {

          // ========================================
          // Spring Page의 게시글 목록
          // ========================================
          this.noticeList =
            resp.data.content || [];


          // ========================================
          // DB에 있는 전체 공지사항 개수
          //
          // 게시판 번호 계산에도 사용
          // ========================================
          this.cnt =
            resp.data.totalElements || 0;

        })

        .catch((err) => {

          // ========================================
          // 공지사항 목록 조회 실패
          // ========================================
          console.error(
            "공지사항 목록 조회 실패:",
            err
          );

        });

    },


    // ========================================
    // 페이지 클릭
    // ========================================
    pageClick(button, page) {

      // 현재 페이지 변경
      this.pageNumber = page;


      // 변경된 페이지의 공지사항 조회
      this.getNoticeList();

    },


    // ========================================
    // 검색
    // ========================================
    searchBtn() {

      // 검색하면 1페이지부터 시작
      this.pageNumber = 1;


      // ========================================
      // 검색어가 없으면
      // 전체 공지사항 조회
      // ========================================
      if (this.keyword.trim() === "") {

        this.getNoticeList();

        return;

      }


      // ========================================
      // 공지사항 검색
      // ========================================
      axios

        .get(
          "http://localhost:3000/api/v1/posts/search",
          {

            params: {

              // 사용자가 입력한 검색어
              keyword: this.keyword,


              // ========================================
              // 공지사항만 검색
              // ========================================
              category: "NOTICE",


              // 검색 결과 첫 번째 페이지
              page: 0,


              // 검색 결과도 10개씩 표시
              size: 10

            }

          }

        )

        .then((resp) => {

          // 검색된 공지사항 목록
          this.noticeList =
            resp.data.content || [];


          // 검색된 전체 게시글 개수
          this.cnt =
            resp.data.totalElements || 0;

        })

        .catch((err) => {

          // 검색 실패
          console.error(
            "공지사항 검색 실패:",
            err
          );

        });

    },


    // ========================================
    // 상세 페이지 이동
    // ========================================
    noticeDetail(postId) {

      // ========================================
      // 클릭한 게시글의 실제 postId를 가지고
      // NoticeDetail.vue로 이동
      //
      // 화면에 보이는 게시판 번호와
      // DB의 postId는 서로 다를 수 있음
      // ========================================
      this.$router.push({

        // notice.js에 등록한 route 이름
        name: "noticeDetail",


        // URL의 :id 부분에 실제 postId 전달
        params: {

          id: postId

        }

      });

    },


    // ========================================
    // 날짜 표시
    // ========================================
    formatDate(date) {

      // 날짜가 없으면 빈 문자열
      if (!date) {

        return "";

      }


      // ========================================
      // 날짜에서 YYYY-MM-DD 부분만 가져옴
      //
      // 예)
      // 2026-09-21T14:30:00
      // ↓
      // 2026-09-21
      // ========================================
      return String(date).substring(0, 10);

    }

  }

};

</script>
<style scoped src="./noticeList.css"></style>
