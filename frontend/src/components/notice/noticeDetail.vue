
<template>

  <!-- 상세 페이지 -->
  <div id="notice-detail">

    <br /><br />


    <!-- 상세 내용 영역 -->
    <div class="notice-detail">


      <!-- ================================= -->
      <!-- 제목 영역 -->
      <!-- ================================= -->

      <div class="border-bottom pb-3 mb-4">


        <!-- 공지 표시 -->
        <div class="mb-2">

          <span class="badge-type badge-default">
            공지
          </span>

        </div>


        <!-- 제목 -->
        <h3 class="fw-bold mb-3">
          {{ notice.title }}
        </h3>


        <!-- 작성자 / 날짜 / 조회수 -->
        <div
          class="d-flex justify-content-between text-muted small"
        >

          <!-- 작성자 -->
          <span>
            작성자 {{ notice.writer }}
          </span>


          <div>

            <!-- 등록일 -->
            <span>
              등록일 {{ formatDate(notice.createdAt) }}
            </span>


            <!-- 수정일 -->
            <span v-if="notice.updatedAt">

              &nbsp;|&nbsp;

              수정일
              {{ formatDate(notice.updatedAt) }}

            </span>


            <!-- 조회수 -->
            <span v-if="notice.viewCount !== undefined">

              &nbsp;|&nbsp;

              조회수
              {{ notice.viewCount }}

            </span>

          </div>

        </div>

      </div>



      <!-- ================================= -->
      <!-- 본문 -->
      <!-- ================================= -->

      <div
        class="content-body py-4 mb-4"
        v-html="notice.content"
      ></div>



      <!-- ================================= -->
      <!-- 첨부파일 -->
      <!-- ================================= -->

      <div
        v-if="attachments.length > 0"
        class="attachment-box border-top border-bottom py-3 mb-4"
      >

        <h6 class="fw-bold mb-3">
          첨부파일
        </h6>


        <!-- 첨부파일 목록 -->
        <div
          v-for="file in attachments"
          :key="
            file.fileId ||
            file.id ||
            file.fileName
          "
          class="mb-2"
        >

          <a
            :href="
              file.downloadUrl ||
              file.url
            "
            target="_blank"
            rel="noopener noreferrer"
          >

            📎

            {{ file.fileName || file.originalFileName }}

          </a>

        </div>

      </div>



      <!-- ================================= -->
      <!-- 이전글 / 다음글 -->
      <!-- ================================= -->

      <table
        class="table border-top border-bottom mb-4"
      >

        <tbody>


          <!-- 이전글 -->
          <tr>

            <th
              style="width: 120px;"
              class="text-secondary"
            >
              이전글
            </th>


            <td
              v-if="prevNext.prevId"
              @click="
                moveDetail(prevNext.prevId)
              "
              class="cursor-pointer text-truncate"
            >

              {{ prevNext.prevTitle }}

            </td>


            <td
              v-else
              class="text-muted"
            >

              이전글이 없습니다.

            </td>

          </tr>



          <!-- 다음글 -->
          <tr>

            <th class="text-secondary">
              다음글
            </th>


            <td
              v-if="prevNext.nextId"
              @click="
                moveDetail(prevNext.nextId)
              "
              class="cursor-pointer text-truncate"
            >

              {{ prevNext.nextTitle }}

            </td>


            <td
              v-else
              class="text-muted"
            >

              다음글이 없습니다.

            </td>

          </tr>


        </tbody>

      </table>



      <!-- ================================= -->
      <!-- 목록 버튼 -->
      <!-- ================================= -->

      <div class="text-center">

        <button
          @click="goList"
          class="btn btn-outline-secondary px-5"
        >
          목록
        </button>

      </div>


    </div>


    <br /><br />

  </div>

</template>



<script>

import axios from "axios";


export default {

  data() {

    return {

      // /notice/:id에서 id 가져오기
      postId: this.$route.params.id,


      // 상세 게시글
      notice: {},


      // 첨부파일
      attachments: [],


      // 이전글 / 다음글
      prevNext: {}

    };

  },


  mounted() {

    // 상세 게시글 조회
    this.getNoticeDetail();

  },


  methods: {


    // ========================================
    // 상세 게시글 조회
    // ========================================
    getNoticeDetail() {

      axios

        .get(
          `http://localhost:3000/api/v1/posts/${this.postId}`
        )

        .then((resp) => {

          /*
           * 백엔드 응답 예시
           *
           * {
           *   post: {...},
           *   attachments: [...],
           *   prevNext: {...}
           * }
           *
           */


          // 게시글
          this.notice =
            resp.data.post || resp.data;


          // 첨부파일
          this.attachments =
            resp.data.attachments || [];


          // 이전글 / 다음글
          this.prevNext =
            resp.data.prevNext || {};

        })

        .catch((err) => {

          console.error(
            "공지사항 상세 조회 실패:",
            err
          );

          alert(
            "공지사항을 불러오지 못했습니다."
          );

        });

    },


    // ========================================
    // 이전글 / 다음글
    // ========================================
    moveDetail(postId) {

      if (!postId) {

        return;

      }


      this.$router.push({

        name: "noticeDetail",

        params: {

          id: postId

        }

      });


      this.postId = postId;


      this.getNoticeDetail();

    },


    // ========================================
    // 목록으로 이동
    // ========================================
    goList() {

      this.$router.push({

        name: "notice"

      });

    },


    // ========================================
    // 날짜 표시
    // ========================================
    formatDate(date) {

      if (!date) {

        return "";

      }


      return String(date)
        .replace("T", " ")
        .substring(0, 16);

    }

  }

};

</script>



<style scoped>

.notice-detail {

  max-width: 1000px;

  margin: 0 auto;

  padding: 30px;

  background: #fff;

}


.cursor-pointer {

  cursor: pointer;

}


.cursor-pointer:hover {

  text-decoration: underline;

}


.content-body {

  min-height: 300px;

  line-height: 1.8;

}


.attachment-box {

  background: #fafafa;

}


.attachment-box a {

  color: #333;

  text-decoration: none;

}


.attachment-box a:hover {

  text-decoration: underline;

}

</style>

