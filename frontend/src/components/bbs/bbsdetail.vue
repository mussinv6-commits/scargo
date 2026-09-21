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
            <th>작성일</th>
            <td>{{ wdate }}</td>
          </tr>
          <tr>
            <th>조회수</th>
            <td>{{ readcount }}</td>
          </tr>
          <tr>
            <td colspan="2">{{ title }}</td>
          </tr>
          <tr>
            <td colspan="2">
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

      <button @click="bbsupdate" v-if="isShow" class="btn btn-primary">글수정</button
      >&nbsp;
      <button @click="bbsdelete" v-if="isShow" class="btn btn-primary">글삭제</button
      >&nbsp;
    </div>

    <br /><br />

    <!-- 댓글 -->
    <table class="table">
      <colgroup>
        <col width="650px" />
        <col width="150px" />
      </colgroup>
      <tbody>
        <tr>
          <td colspan="2">댓글작성</td>
        </tr>
        <tr>
          <td>
            <textarea rows="2" class="form-control" v-model="comment"></textarea>
          </td>
          <td style="padding-left: 30px">
            <button @click="bbsCommentWrite" class="btn btn-primary">작성완료</button>
          </td>
        </tr>
      </tbody>
    </table>

    <br /><br />

    <!-- 댓글목록-->
    <table class="table">
      <tbody v-for="(comm, index) in commentList" :key="index">
        <tr class="table-info">
          <td>작성자: {{ comm.id }}</td>
          <td>작성일: {{ comm.wdate }}</td>
        </tr>
        <tr>
          <td colspan="2" style="text-align: left">{{ comm.content }}</td>
        </tr>
        <tr>
          <td colspan="2">
            <div style="line-height: 10px">&nbsp;</div>
          </td>
        </tr>
      </tbody>
    </table>
    <br /><br />
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
      wdate: "",
      readcount: "",

      isShow: false, // 버튼이 보일지 결정하는 플러그

      comment: "",
      commentList: [], // 댓글목록
    };
  },
  mounted() {
    //alert(this.$route.params.seq);

    // url
    let url = "http://localhost:3000/bbs/getBbs";

    // parameter <- 보내주는 값
    const param = { params: { seq: this.$route.params.seq } };

    axios
      .get(url, param)
      .then((resp) => {
        let bbs = resp.data;
        //alert(JSON.stringify(bbs));
        this.seq = bbs.seq;
        this.id = bbs.id;
        this.title = bbs.title;
        this.content = bbs.content;
        this.wdate = bbs.wdate;
        this.readcount = bbs.readcount;

        // 버튼이 보일지 설정
        let login = JSON.parse(sessionStorage.getItem("login"));
        if (login && login.id === this.id) {
          // 로그인한 유저와 작성자가 같을 시
          this.isShow = true; // 버튼을 보여준다
        }
      })
      .catch((err) => {
        alert(err);
      });

    this.bbsCommentList();
  },
  methods: {
    bbsCommentWrite() {
      let login = JSON.parse(sessionStorage.getItem("login"));

      if (!login) {
        alert("로그인이 필요합니다");
        return;
      }

      const param = {
        params: {
          bbsseq: this.seq,
          id: login.id,
          content: this.comment,
        },
      };

      axios
        .get("http://localhost:3000/bbs/commentWrite", param)
        .then((resp) => {
          if (resp.data === "NO") {
            alert("댓글리 추가되지 않았습니다");
          }

          this.bbsCommentList(); // 리스트를 갱신
        })
        .catch((err) => {
          alert(err);
        });
    },
    bbsCommentList() {
      axios
        .get("http://localhost:3000/bbs/commentList", {
          params: { bbsseq: this.$route.params.seq },
        })
        .then((resp) => {
          this.commentList = resp.data;
        })
        .catch((err) => {
          alert(err);
        });
    },
    bbsupdate() {
      this.$router.push({ name: "bbsupdate", params: { seq: this.$route.params.seq } });
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
