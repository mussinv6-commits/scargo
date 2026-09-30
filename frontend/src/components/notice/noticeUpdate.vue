<script setup>
// axios를 사용해서 백엔드 서버와 통신하기 위해 가져온다.
import axios from "axios";

// Vue Router의 현재 경로 정보와 페이지 이동 기능을 가져온다.
import { useRoute, useRouter } from "vue-router";

// 현재 URL의 파라미터를 확인할 때 사용한다.
const route = useRoute();

// 수정 완료 후 다른 페이지로 이동할 때 사용한다.
const router = useRouter();
</script>

<script>
// Vue 컴포넌트의 데이터를 정의한다.
export default {
  data() {
    return {
      // URL의 :id 값을 저장한다.
      // 예: /notice/update/1 → id는 1
      id: this.$route.params.id,

      // 공지사항 제목
      title: "",

      // 공지사항 내용
      content: "",

      // 작성자
      writer: "",

      // 데이터를 불러오는 중인지 확인한다.
      loading: false,
    };
  },

  // 컴포넌트가 화면에 표시될 때 실행된다.
  mounted() {
    // 공지사항 상세 정보를 가져온다.
    this.getNotice();
  },

  methods: {
    // 공지사항 상세 정보를 조회하는 함수
    getNotice() {
      // 백엔드의 공지사항 상세 조회 API 주소
      // 실제 백엔드 API 주소에 맞게 수정해야 한다.
      const url = "http://localhost:3000/getNotice";

      // URL에서 받은 id를 백엔드로 전달한다.
      const param = {
        params: {
          id: this.id,
        },
      };

      // 로딩 시작
      this.loading = true;

      // GET 요청으로 공지사항 정보를 가져온다.
      axios
        .get(url, param)
        .then((resp) => {
          // 백엔드에서 보내준 공지사항 데이터를 받는다.
          const notice = resp.data;

          // 받아온 데이터를 화면의 입력창에 넣는다.
          this.id = notice.id;
          this.title = notice.title;
          this.content = notice.content;
          this.writer = notice.writer;
        })
        .catch((err) => {
          // 조회 중 오류가 발생하면 콘솔에 출력한다.
          console.error("공지사항 조회 오류:", err);

          // 사용자에게 오류를 알려준다.
          alert("공지사항을 불러오지 못했습니다.");
        })
        .finally(() => {
          // 조회가 끝나면 로딩을 종료한다.
          this.loading = false;
        });
    },

    // 수정완료 버튼을 눌렀을 때 실행되는 함수
    noticeUpdateAf() {
      // 제목이 비어 있는지 확인한다.
      if (!this.title.trim()) {
        alert("제목을 입력해주세요.");
        return;
      }

      // 내용이 비어 있는지 확인한다.
      if (!this.content.trim()) {
        alert("내용을 입력해주세요.");
        return;
      }

      // 백엔드의 공지사항 수정 API 주소
      // 실제 백엔드 API 주소에 맞게 수정해야 한다.
      const url = "http://localhost:3000/noticeUpdate";

      // 수정할 데이터를 객체로 만든다.
      const notice = {
        id: this.id,
        title: this.title,
        content: this.content,
      };

      // POST 요청으로 수정 데이터를 백엔드에 전달한다.
      axios
        .post(url, notice)
        .then((resp) => {
          // 백엔드 응답 결과를 확인한다.
          if (resp.data === "NO") {
            alert("공지사항이 수정되지 않았습니다.");
            return;
          }

          // 수정이 완료되었다고 알려준다.
          alert("공지사항이 수정되었습니다.");

          // 수정한 공지사항의 상세 페이지로 이동한다.
          // 예: /notice/1
          this.$router.push({
            name: "noticeDetail",
            params: {
              id: this.id,
            },
          });
        })
        .catch((err) => {
          // 수정 중 오류가 발생하면 콘솔에 출력한다.
          console.error("공지사항 수정 오류:", err);

          // 사용자에게 오류를 알려준다.
          alert("공지사항 수정 중 오류가 발생했습니다.");
        });
    },

    // 수정하지 않고 상세 페이지로 돌아가는 함수
    goBack() {
      // 현재 공지사항의 상세 페이지로 이동한다.
      this.$router.push({
        name: "noticeDetail",
        params: {
          id: this.id,
        },
      });
    },
  },
};
</script>

<template>
  <div class="notice-update">
    <h2>공지사항 수정</h2>

    <!-- 공지사항 수정 입력 영역 -->
    <table class="notice-table">
      <tbody>
        <tr>
          <th>번호</th>
          <td>{{ id }}</td>
        </tr>

        <tr>
          <th>작성자</th>
          <td>{{ writer }}</td>
        </tr>

        <tr>
          <th>제목</th>
          <td>
            <!-- 공지사항 제목 수정 입력창 -->
            <input
              v-model="title"
              type="text"
              placeholder="공지사항 제목을 입력하세요."
            />
          </td>
        </tr>

        <tr>
          <th>내용</th>
          <td>
            <!-- 공지사항 내용 수정 입력창 -->
            <textarea
              v-model="content"
              rows="15"
              placeholder="공지사항 내용을 입력하세요."
            ></textarea>
          </td>
        </tr>
      </tbody>
    </table>

    <div class="button-area">
      <!-- 수정완료 버튼 -->
      <button
        type="button"
        @click="noticeUpdateAf"
        :disabled="loading"
      >
        수정완료
      </button>

      <!-- 취소 버튼 -->
      <button
        type="button"
        @click="goBack"
      >
        취소
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 공지사항 수정 페이지 전체 영역 */
.notice-update {
  width: 800px;
  max-width: 100%;
  margin: 40px auto;
  padding: 20px;
  box-sizing: border-box;
}

/* 제목 */
.notice-update h2 {
  margin-bottom: 20px;
}

/* 테이블 */
.notice-table {
  width: 100%;
  border-collapse: collapse;
}

/* 테이블의 셀 */
.notice-table th,
.notice-table td {
  border: 1px solid #ddd;
  padding: 12px;
}

/* 왼쪽 항목 이름 */
.notice-table th {
  width: 120px;
  background-color: #f5f5f5;
  text-align: center;
}

/* 제목 입력창 */
.notice-table input {
  width: 100%;
  padding: 10px;
  box-sizing: border-box;
}

/* 내용 입력창 */
.notice-table textarea {
  width: 100%;
  padding: 10px;
  box-sizing: border-box;
  resize: vertical;
}

/* 버튼 영역 */
.button-area {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
}

/* 버튼 */
.button-area button {
  padding: 10px 20px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

/* 비활성화된 버튼 */
.button-area button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>