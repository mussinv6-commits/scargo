<template>
  <div id="bbswrite">
    <br />

    <table class="table table-bordered">
      <colgroup>
        <col width="150" />
        <col width="500" />
      </colgroup>
      <tbody>
        <tr>
          <th>아이디</th>
          <td>
            <input v-model="id" class="form-control" readonly />
          </td>
        </tr>
        <tr>
          <th>제목</th>
          <td>
            <input v-model="title" class="form-control" placeholder="제목을 입력" />
          </td>
        </tr>
        <tr>
          <th>내용</th>
          <td>
            <textarea
              rows="15"
              cols="50"
              v-model="content"
              class="form-control"
            ></textarea>
          </td>
        </tr>
      </tbody>
    </table>

    <button @click="bbswrite" class="btn btn-primary">글쓰기</button>
  </div>
</template>

<script>
import axios from "axios";
export default {
  data() {
    return {
      id: "",
      title: "",
      content: "",
    };
  },
  async mounted() {
    // login 했나?
    let login = sessionStorage.getItem("login");
    if (login === "null" || login === "") {
      alert("login 해 주십시오");

      // 경로를 저장
      sessionStorage.setItem("location", "/bbswrite");

      // router의 push로 이동시킬 때는 name으로 이동
      await this.$router.push({ name: "login" });
    }

    let json = JSON.parse(login); // 문자열 -> Json  == parsing
    this.id = json.id;
  },
  methods: {
    bbswrite() {
      // title, content 빈칸 검사!

      const param = {
        params: {
          id: this.id,
          title: this.title,
          content: this.content,
        },
      };

      axios
        .get("http://localhost:8080/bbs/bbswrite", param)
        .then((resp) => {
          if (resp.data === "YES") {
            this.$router.push({ name: "bbslist" });
          } else {
            alert("글이 추가되지 않았습니다");
          }
        })
        .catch((err) => {
          alert(err);
        });
    },
  },
};
</script>

<style>
#bbswrite {
  margin: 30px 0 30px 0;
}
.center {
  margin: auto;
  width: 800px;
  text-align: center;
}
td {
  padding: 2px;
}
th {
  background: rgb(234, 235, 255);
  color: black;
}
</style>
