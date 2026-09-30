<template>
  <div id="scrlist">
    <div class="filter-container">
      <input class="filter" placeholder="Filter posts..." />
    </div>

    <div id="posts-container">
      <div class="post" v-for="(post, index) in posts" :key="post.seq">
        <div class="number">{{ index + 1 }}</div>
        <div class="post-info">
          <h3 @click="bbsdetail(post.seq)" class="post-title">{{ post.title }}</h3>
          <h4 class="post-body">{{ post.wdate }}</h4>
          <p class="post-body">{{ post.id }}</p>
        </div>
      </div>
    </div>

    <div class="loader">
      <div class="circle"></div>
      <div class="circle"></div>
      <div class="circle"></div>
    </div>
  </div>
</template>

<script>
import axios from "axios";

export default {
  name: "scrlist",
  data() {
    return {
      limit: 5,
      page: 0,
      posts: [],
      isLoading: false, // 추가
      isEnd: false, // 추가: 더 이상 가져올 데이터 없음
    };
  },
  mounted() {
    this.showPosts();

    window.addEventListener("scroll", this.handleScroll);
  },

  beforeUnmount() {
    window.removeEventListener("scroll", this.handleScroll);
  },

  methods: {
    handleScroll() {
      // 이미 로딩 중이거나 더 가져올 데이터가 없으면 무시 (중복 호출 방지)
      if (this.isLoading || this.isEnd) return;

      const { scrollTop, scrollHeight, clientHeight } = document.documentElement;
      if (scrollTop + clientHeight >= scrollHeight) {
        this.showLoading();
      }
    },
    async getPosts() {
      let param = {
        params: { pageNumber: this.page },
      };

      let data = [];
      await axios
        .get("http://localhost:3000/bbs/scrList", param)
        .then((res) => {
          data = res.data;
        })
        .catch((err) => {
          alert(err);
        });

      return data;
    },
    async showPosts() {
      console.log("showPosts() 호출");
      this.isLoading = true; // 로딩 시작

      const newPosts = await this.getPosts();
      // 기존 목록 뒤에 이어붙이기 (무한스크롤)
      //this.posts.push(...newPosts);

      if (newPosts.length < this.limit) {
        this.isEnd = true; // 더 가져올 데이터 없음
      }

      this.posts.push(...newPosts);
      this.isLoading = false; // 로딩 끝
    },
    showLoading() {
      console.log("showLoading() 호출");
      this.isLoading = true; // 로더 표시~실제 fetch 사이의 딜레이 구간도 막기 위해 미리 true

      let loading = document.querySelector(".loader");
      loading.classList.add("show");

      setTimeout(() => {
        loading.classList.remove("show");

        setTimeout(() => {
          this.page += 1;
          this.showPosts();
        }, 300);
      }, 1000);
    },

    bbsdetail(seq) {
      this.$router.push({ name: "bbsdetail", params: { seq: seq } });
    },
  },
};
</script>

<style>
@import "./style.css";
</style>
