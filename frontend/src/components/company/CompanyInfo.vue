<template>
  <div class="dash-wrap">
    <div class="dash-card">
      <div class="dash-header">
        <h2>{{ company?.companyName || "업체 정보" }}</h2>
        <span class="badge">회사 계정</span>
      </div>

      <div v-if="company" class="company-info">
        <div class="info-row"><span>대표자</span><b>{{ company.representativeName || "-" }}</b></div>
        <div class="info-row"><span>업종</span><b>{{ company.industryType || "-" }}</b></div>
        <div class="info-row"><span>주소</span><b>{{ company.address }}</b></div>
        <div class="info-row"><span>사업자번호</span><b>{{ company.businessNo || "-" }}</b></div>
      </div>
      <p v-else-if="loadedCompany" class="empty-text">업체 정보를 불러오지 못했습니다.</p>

      <div class="section-title" style="display:flex; align-items:center; justify-content:space-between;">
        <span>소속 차량 ({{ trucks.length }}대)</span>
        <!-- 26.09.21 추가: 사업자가 바로 차량을 등록할 수 있는 버튼 -->
        <RouterLink
          to="/company/trucks/new"
          class="mapping-submit-btn"
          style="width:auto; padding:8px 16px; font-size:13px; display:inline-block; text-decoration:none;"
        >
          + 차량 등록
        </RouterLink>
      </div>

      <table class="truck-table" v-if="trucks.length">
        <thead>
          <tr>
            <th>차량번호</th>
            <th>차종</th>
            <th>세미트레일러</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in trucks" :key="t.vehicleNo">
            <td>{{ t.vehicleNo }}</td>
            <td>{{ t.truckType || "-" }}</td>
            <td>{{ t.isSemiTrailer ? "예" : "아니오" }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty-text">등록된 차량이 없습니다.</p>
      <p class="hint-text">트레일러 번호, 최대 적재 중량 등 상세 정보는 관리자 페이지에서 확인할 수 있습니다.</p>
    </div>
  </div>
</template>

<script>
import axios from "axios";
import { authState } from "@/auth/authState.js";
import { API_BASE } from "@/utils/apiBase.js";

export default {
  name: "CompanyInfo",
  data() {
    return {
      user: null,
      company: null,
      trucks: [],
      loadedCompany: false,
    };
  },
  async mounted() {
    this.user = authState.user;

    if (!this.user) {
      alert("로그인이 필요합니다.");
      this.$router.push("/login");
      return;
    }
    if (this.user.userType !== "CORPORATE_APPROVED") {
      alert("승인된 회사 계정만 접근할 수 있습니다.");
      this.$router.push("/");
      return;
    }

    await this.fetchCompany();
    await this.fetchTrucks();
  },
  methods: {
    async fetchCompany() {
      try {
        // 수정: 실제 백엔드 경로는 /api/companies/{companyId} 이다.
        const resp = await axios.get(`${API_BASE}/api/companies/${this.user.companyId}`);
        this.company = resp.data;
      } catch (err) {
        console.error(err);
      } finally {
        this.loadedCompany = true;
      }
    },
    async fetchTrucks() {
      try {
        // 수정: 회사별 차량 상세 목록 API가 없어, 드롭다운/옵션용 경량 목록 API를 사용한다.
        const resp = await axios.get(`${API_BASE}/api/trucks/options`, {
          params: { companyId: this.user.companyId },
        });
        this.trucks = resp.data;
      } catch (err) {
        console.error(err);
      }
    },
  },
};
</script>