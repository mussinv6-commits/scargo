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
      <div v-if="user" class="company-info">
        <div class="info-row"><span>회원 번호</span><b>{{ user.accountId ?? "-" }}</b></div>
        <div class="info-row"><span>가입일</span><b>{{ formatDate(user.createdAt) }}</b></div>
      </div>
      <p v-else-if="loadedCompany" class="empty-text">업체 정보를 불러오지 못했습니다.</p>

      <div class="section-title" style="display:flex; align-items:center; justify-content:space-between;">
        <span>소속 차량 ({{ trucks.length }}대)</span>
        <RouterLink to="/company/trucks/new" style="font-size:13px; font-weight:700;">차량 관리 ›</RouterLink>
      </div>

      <table class="truck-table" v-if="trucks.length">
        <thead>
          <tr>
            <th style="width:64px;">번호</th>
            <th>차량번호</th>
            <th>차종</th>
            <th>세미트레일러</th>
            <th>배정 기사</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(t, idx) in trucks" :key="t.vehicleNo">
            <td>{{ idx + 1 }}</td>
            <td>{{ t.vehicleNo }}</td>
            <td>{{ t.truckType || "-" }}</td>
            <td>{{ t.isSemiTrailer ? "예" : "아니오" }}</td>
            <td>
              <span v-if="t.assignedDriverName">{{ t.assignedDriverName }}</span>
              <span v-else class="empty-text" style="padding:0;">미배정</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty-text">등록된 차량이 없습니다.</p>
      <!-- 26.09.30 추가: 소속 기사 정보 (기존에는 불러오지 못해 표시되지 않았음) -->
      <div class="section-title" style="display:flex; align-items:center; justify-content:space-between;">
        <span>소속 기사 ({{ drivers.length }}명)</span>
        <RouterLink to="/company/drivers" style="font-size:13px; font-weight:700;">기사 관리 ›</RouterLink>
      </div>
      <table class="truck-table" v-if="drivers.length">
        <thead>
          <tr>
            <th style="width:64px;">번호</th>
            <th>이름</th>
            <th>아이디</th>
            <th>연락처</th>
            <th>배정 차량</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(d, idx) in drivers" :key="d.accountId">
            <td>{{ idx + 1 }}</td>
            <td>{{ d.userName || "-" }}</td>
            <td>{{ d.userId }}</td>
            <td>{{ d.phoneNum || "-" }}</td>
            <td>{{ truckOfDriver(d.accountId) || "미배정" }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty-text">{{ driverError || "소속된 기사가 없습니다." }}</p>

      <!-- 26.09.22 수정: 기사 배정 관리는 "기사 관리" 탭으로 이동. 여기는 조회만. -->
      <p class="hint-text">
        기사 배정은 <RouterLink to="/company/drivers">기사 관리</RouterLink> 메뉴에서 하실 수 있습니다.
        트레일러 번호, 최대 적재 중량 등 상세 정보는 관리자 페이지에서 확인할 수 있습니다.
      </p>
    </div>
  </div>
</template>

<script>
import axios from "axios";
import { authState } from "@/auth/authState.js";
import { API_BASE } from "@/utils/apiBase.js";
import { fetchCompanyDrivers, fetchCompanyTrucks } from "@/utils/companyDrivers.js";

export default {
  name: "CompanyInfo",
  data() {
    return {
      user: null,
      company: null,
      trucks: [],
      drivers: [],
      driverError: "",
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

    await Promise.all([this.fetchCompany(), this.fetchTrucks(), this.fetchDrivers()]);
  },
  methods: {
    async fetchCompany() {
      try {
        // 수정: 실제 백엔드 경로는 /api/companies/{companyId} 이다.
        const resp = await axios.get(`${API_BASE}/api/companies/${this.user.companyId}`, {
          withCredentials: true,
        });
        this.company = resp.data;
      } catch (err) {
        console.error(err);
      } finally {
        this.loadedCompany = true;
      }
    },
    async fetchTrucks() {
      try {
        this.trucks = await fetchCompanyTrucks();
      } catch (err) {
        console.error(err);
      }
    },
    async fetchDrivers() {
      try {
        this.drivers = await fetchCompanyDrivers();
      } catch (err) {
        this.driverError = err.message;
      }
    },
    truckOfDriver(accountId) {
      return this.trucks.find((t) => t.assignedAccountId === accountId)?.vehicleNo || "";
    },
    formatDate(d) {
      return d ? new Date(d).toLocaleDateString("ko-KR") : "-";
    },
  },
};
</script>