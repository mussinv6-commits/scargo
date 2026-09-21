<template>
  <div id="bbslist">
    <br /><br />

    <table class="table table-hover">
      <colgroup>
        <col width="50" />
        <col width="500" />
        <col width="50" />
        <col width="50" />
      </colgroup>

      <thead>
        <tr>
          <th>번호</th>
          <th>제목</th>
          <th>조회수</th>
          <th>작성자</th>
        </tr>
      </thead>

      <tbody v-for="(bbs, index) in bbslist" v-bind:key="index">
        <tr>
          <td>{{ index + 1 }}</td>
          <td @click="bbsdetail(bbs.seq)" align="left" class="text-underline-hover">
            {{ dot3(bbs.title) }}
          </td>
          <td>{{ bbs.readcount }}</td>
          <td>{{ bbs.id }}</td>
        </tr>
      </tbody>
    </table>

    <!-- paging -->
    <div class="overflow-auto">
      <b-pagination
        v-model="pageNumber"
        :total-rows="cnt"
        :per-page="10"
        align="center"
        @page-click="pageClick"
        aria-controls="my-table"
      >
      </b-pagination>

      <div class="d-flex justify-content-center align-items-center">
        <!-- 검색목록 -->
        <select v-model="category" class="form-select" style="width: auto">
          <option
            v-for="(option, index) in options"
            v-bind:value="option.value"
            v-bind:key="index"
          >
            {{ option.text }}
          </option></select
        >&nbsp;&nbsp;

        <!-- 검색창 -->
        <div class="col-sm-3 my-1" style="width: auto">
          <input
            v-model="keyword"
            size="45"
            placeholder="검색어입력"
            class="form-control"
          />
        </div>
        &nbsp;&nbsp;

        <!-- 검색 버튼 -->
        <button @click="searchBtn()" class="btn btn-primary">검색</button>
      </div>

      <br />
    </div>

    <a href="/bbswrite">글추가</a>
    <br /><br />
  </div>
</template>

<script>
import axios from "axios";
export default {
  data() {
    return {
      bbslist: [],
      category: "start",
      keyword: "",
      options: [
        { text: "선택", value: "start" },
        { text: "제목", value: "title" },
        { text: "내용", value: "content" },
        { text: "작성자", value: "writer" },
      ],
      pageNumber: 1,
      cnt: 0, // 글의 총수
    };
  },
  mounted() {
    this.getBbslist();
  },
  methods: {
    getBbslist() {
      let params = {
        category: this.category,
        keyword: this.keyword,
        pageNumber: this.pageNumber - 1,
      };

      axios
        .get("http://localhost:3000/bbs/bbslist", { params: params })
        .then((resp) => {
          //alert("success");
          //alert(resp.data.bbslist);
          //alert(resp.data.cnt);

          this.bbslist = resp.data.bbslist;
          this.cnt = resp.data.cnt;
        })
        .catch((err) => {
          alert(err);
        });
    },
    pageClick(button, page) {
      this.pageNumber = page;
      this.getBbslist();
    },
    dot3(title) {
      let str = "";
      if (title.length >= 45) {
        str = title.substring(0, 35);
        str += "...";
      } else {
        str = title.trim();
      }
      return str;
    },
    searchBtn() {
      this.pageNumber = 1;
      this.getBbslist();
    },
    bbsdetail(seq) {
      this.$router.push({ name: "bbsdetail", params: { seq: seq } });
    },
  },
};
</script>

<style>
#bbslist {
  margin: 0 auto;
  width: 1000px;
}
.text-underline-hover {
  text-decoration: none;
}

.text-underline-hover:hover {
  text-decoration: underline;
}
</style>
