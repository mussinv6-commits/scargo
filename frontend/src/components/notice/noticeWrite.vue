
<template>

  <div id="notice-write" class="container py-5">

    <h3 class="fw-bold mb-4">
      공지사항 작성
    </h3>


    <form @submit.prevent="writeNotice">


      <!-- ================================= -->
      <!-- 제목 -->
      <!-- ================================= -->

      <div class="mb-3">

        <label class="form-label fw-bold">
          제목
        </label>


        <input
          v-model="title"
          type="text"
          class="form-control"
          placeholder="공지사항 제목을 입력하세요."
          maxlength="200"
          required
        />

      </div>



      <!-- ================================= -->
      <!-- 작성자 -->
      <!-- ================================= -->

      <div class="mb-3">

        <label class="form-label fw-bold">
          작성자
        </label>


        <input
          v-model="writer"
          type="text"
          class="form-control"
          placeholder="작성자"
          required
        />

      </div>



      <!-- ================================= -->
      <!-- 내용 -->
      <!-- ================================= -->

      <div class="mb-3">

        <label class="form-label fw-bold">
          내용
        </label>


        <textarea
          v-model="content"
          class="form-control"
          rows="12"
          placeholder="공지사항 내용을 입력하세요."
          required
        ></textarea>

      </div>



      <!-- ================================= -->
      <!-- 첨부파일 -->
      <!-- ================================= -->

      <div class="mb-4">

        <label class="form-label fw-bold">
          첨부파일
        </label>


        <input
          type="file"
          class="form-control"
          multiple
          @change="fileChange"
        />


        <small class="text-muted">

          여러 파일을 선택할 수 있습니다.

        </small>



        <!-- 선택한 파일 목록 -->
        <ul
          v-if="files.length > 0"
          class="mt-3"
        >

          <li
            v-for="(file, index) in files"
            :key="index"
          >

            {{ file.name }}


            <button
              type="button"
              class="btn btn-sm btn-link text-danger"
              @click="removeFile(index)"
            >

              삭제

            </button>

          </li>

        </ul>

      </div>



      <!-- ================================= -->
      <!-- 버튼 -->
      <!-- ================================= -->

      <div class="text-center">


        <!-- 취소 -->
        <button
          type="button"
          class="btn btn-outline-secondary me-2"
          @click="goList"
        >

          취소

        </button>


        <!-- 등록 -->
        <button
          type="submit"
          class="btn btn-primary"
          :disabled="saving"
        >

          {{ saving ? "등록 중..." : "등록" }}

        </button>


      </div>


    </form>

  </div>

</template>



<script>

import axios from "axios";


export default {

  data() {

    return {

      // 제목
      title: "",


      // 작성자
      writer: "",


      // 내용
      content: "",


      // 첨부파일
      files: [],


      // 등록 중인지 확인
      saving: false

    };

  },


  methods: {


    // ========================================
    // 파일 선택
    // ========================================
    fileChange(event) {

      this.files =
        Array.from(
          event.target.files || []
        );

    },


    // ========================================
    // 선택한 파일 삭제
    // ========================================
    removeFile(index) {

      this.files.splice(index, 1);

    },


    // ========================================
    // 공지사항 등록
    // ========================================
    writeNotice() {


      // 제목 확인
      if (!this.title.trim()) {

        alert("제목을 입력하세요.");

        return;

      }


      // 내용 확인
      if (!this.content.trim()) {

        alert("내용을 입력하세요.");

        return;

      }



      // FormData 생성
      const formData = new FormData();



      /*
       * 현재 프로젝트에서
       * 공지사항 = NOTICE
       */
      formData.append(
        "category",
        "NOTICE"
      );


      // 제목
      formData.append(
        "title",
        this.title
      );


      // 내용
      formData.append(
        "content",
        this.content
      );


      // 작성자
      formData.append(
        "writer",
        this.writer
      );



      // 첨부파일 추가
      this.files.forEach((file) => {

        formData.append(
          "files",
          file
        );

      });



      // 등록 시작
      this.saving = true;



      // Spring Boot에 전송
      axios

        .post(
          "http://localhost:3000/api/v1/posts",
          formData,
          {
            headers: {

              "Content-Type":
                "multipart/form-data"

            }

          }
        )

        .then(() => {

          alert(
            "공지사항이 등록되었습니다."
          );


          // 목록으로 이동
          this.$router.push({

            name: "notice"

          });

        })

        .catch((err) => {

          console.error(
            "공지사항 등록 실패:",
            err
          );


          alert(
            "공지사항 등록에 실패했습니다."
          );

        })

        .finally(() => {

          this.saving = false;

        });

    },


    // ========================================
    // 목록으로
    // ========================================
    goList() {

      this.$router.push({

        name: "notice"

      });

    }

  }

};

</script>


<style scoped src="./noticeWrite.css"></style>
