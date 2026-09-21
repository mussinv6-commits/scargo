<script setup>
import axios from "axios";
</script>

<template>
  <div>
    <br />
    <div class="center">
      <table class="table table-bordered">
        <colgroup>
          <col width="200" />
          <col width="500" />
        </colgroup>
        <tbody>
          <tr>
            <th>작성자</th>
            <td>{{ id }}</td>
          </tr>
          <tr>
            <th>제목</th>
            <td>
              <input v-model="title" class="form-control" />
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
      <br />

      <button @click="bbsupdateAf" class="btn btn-primary">수정완료</button>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    // 변수선언
    return {
      seq: this.$route.params.seq,
      id: "",
      title: "",
      content: "",
    };
  },
  mounted() {
    // url
    let url = "http://localhost:3000/getBbs";

    // parameter <- 보내주는 값
    const param = { params: { seq: this.$route.params.seq } };

    axios
      .get(url, param)
      .then((resp) => {
        let bbs = resp.data;
        this.seq = bbs.seq;
        this.id = bbs.id;
        this.title = bbs.title;
        this.content = bbs.content;
      })
      .catch((err) => {
        alert(err);
      });
  },
  methods: {
    bbsupdateAf() {
      axios
        .post("http://localhost:3000/bbsupdate", {
          seq: this.seq,
          title: this.title,
          content: this.content,
        })
        .then((resp) => {
          if (resp.data === "NO") {
            alert("수정되지 않았습니다");
          }

          this.$router.push({ name: "bbsdetail", params: { seq: this.seq } });
        })
        .catch((err) => {
          alert(err);
        });
    },
  },
};
</script>

<style scoped>
@import "./bbsdetail.css";

/* table {
  margin: auto;
  width: 800px;
}
.center td {
  padding: 2px;
  text-align: left;
  padding: 10px;
} */
</style>
