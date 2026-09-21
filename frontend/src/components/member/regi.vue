<template>
  <div class="regi-wrap">
    <div class="regi-card">
      <div class="regi-card-header">회원가입</div>
      <div class="regi-card-body">
        <form @submit.prevent="submitRegi">

          <!-- 회원 유형 선택 -->
          <div class="regi-field">
            <label class="regi-label">회원 유형</label>
            <div class="regi-radio-group">
              <label class="regi-radio">
                <input type="radio" value="GENERAL" v-model="form.userType" />
                일반회원
              </label>
              <label class="regi-radio">
                <input type="radio" value="CORPORATE_PENDING" v-model="form.userType" />
                기업회원
              </label>
            </div>
          </div>

          <!-- 아이디 -->
          <div class="regi-field">
            <label class="regi-label">아이디</label>
            <div class="regi-input-group">
              <input
                type="text"
                class="regi-input"
                id="userId"
                v-model="form.userId"
                placeholder="아이디를 입력하세요"
              />
              <button
                class="regi-btn regi-btn-outline"
                type="button"
                @click="checkId"
              >
                중복확인
              </button>
            </div>
            <div
              class="regi-idcheck"
              :style="{ color: idCheckColor }"
              v-html="idCheckMsg"
            ></div>
          </div>

          <!-- 비밀번호 -->
          <div class="regi-field">
            <label class="regi-label">비밀번호</label>
            <input
              type="password"
              class="regi-input regi-input-full"
              v-model="form.userPw"
              placeholder="비밀번호를 입력하세요"
            />
          </div>

          <!-- 이름 -->
          <div class="regi-field">
            <label class="regi-label">이름</label>
            <input
              type="text"
              class="regi-input regi-input-full"
              v-model="form.userName"
              placeholder="이름을 입력하세요"
            />
          </div>

          <!-- 휴대폰 번호 -->
          <div class="regi-field">
            <label class="regi-label">휴대폰 번호</label>
            <input
              type="text"
              class="regi-input regi-input-full"
              v-model="form.phoneNum"
              placeholder="휴대폰 번호를 입력하세요"
            />
          </div>

          <!-- 업체 선택: 일반/기업 공통 필수 -->
          <div class="regi-field">
            <label class="regi-label">소속 업체명</label>
            <select
              class="regi-input regi-input-full"
              v-model="selectedCompanyId"
              @change="onCompanySelect"
            >
              <option value="" disabled>업체를 선택하세요</option>
              <option v-for="c in companyOptions" :key="c.companyId" :value="c.companyId">
                {{ c.companyName }}
              </option>
            </select>
          </div>

          <!-- 업체 주소 (자동 입력, 읽기전용) -->
          <div class="regi-field" :class="{ 'regi-field-last': form.userType !== 'CORPORATE_PENDING' }">
            <label class="regi-label">업체 주소</label>
            <input
              type="text"
              class="regi-input regi-input-full"
              :value="selectedAddress"
              readonly
              placeholder="업체 선택 시 자동 입력됩니다"
            />
          </div>

          <!-- 사업자등록번호: 기업회원만 노출 -->
          <div class="regi-field regi-field-last" v-if="form.userType === 'CORPORATE_PENDING'">
            <label class="regi-label">사업자 등록번호</label>
            <input
              type="text"
              class="regi-input regi-input-full"
              v-model="form.businessNo"
              placeholder="숫자만 입력하세요"
            />
            <div class="regi-idcheck" :style="{ color: 'red' }" v-if="businessNoError">
              {{ businessNoError }}
            </div>
          </div>

          <button type="submit" class="regi-btn regi-btn-primary regi-btn-block">
            회원가입
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

<script>
import axios from "axios";

const API_BASE = "http://127.0.0.1:8080"; // 백엔드 포트에 맞춰 수정

export default {
  name: "Regi",
  data() {
    return {
      form: {
        userId: "",
        userPw: "",
        userName: "",
        phoneNum: "",
        userType: "GENERAL", // 기본값: 일반회원
        businessNo: "",
      },
      companyOptions: [],
      selectedCompanyId: "",
      selectedAddress: "",
      idCheckMsg: "",
      idCheckColor: "",
      idChecked: false,
      skipIdWatch: false,
      businessNoError: "",
    };
  },
  watch: {
    "form.userId"() {
      if (this.skipIdWatch) {
        this.skipIdWatch = false;
        return;
      }
      this.idChecked = false;
      this.idCheckMsg = "";
    },

    // 회원 유형이 바뀌면 사업자번호 관련 값 초기화
    "form.userType"(newVal) {
      if (newVal !== "CORPORATE_PENDING") {
        this.form.businessNo = "";
        this.businessNoError = "";
      }
    },

    // 사업자번호를 다시 입력하면 에러 메시지 초기화
    "form.businessNo"() {
      this.businessNoError = "";
    },
  },
  async mounted() {
    await this.fetchCompanyOptions();
  },
  methods: {
    async fetchCompanyOptions() {
      try {
        const resp = await axios.get(`${API_BASE}/api/companies/options`);
        this.companyOptions = resp.data;
      } catch (err) {
        console.log(err);
      }
    },

    onCompanySelect() {
      const selected = this.companyOptions.find(
        (c) => c.companyId === this.selectedCompanyId
      );
      this.selectedAddress = selected ? selected.address : "";
    },

    checkId() {
      if (this.form.userId.trim() === "") {
        this.idCheckColor = "red";
        this.idCheckMsg = "아이디를 입력해주세요.";
        this.$nextTick(() => document.getElementById("userId").focus());
        return;
      }

      axios
        .get(`${API_BASE}/api/accounts/check-id/${encodeURIComponent(this.form.userId)}`)
        .then((resp) => {
          if (resp.data.trim() === "YES") {
            this.idCheckColor = "blue";
            this.idCheckMsg = "사용 가능한 아이디입니다.";
            this.idChecked = true;
          } else {
            this.idCheckColor = "red";
            this.idCheckMsg = "<b>이미 사용중인 아이디입니다.</b>";
            this.idChecked = false;
            this.skipIdWatch = true;
            this.form.userId = "";
            this.$nextTick(() => document.getElementById("userId").focus());
          }
        })
        .catch(() => alert("error"));
    },

    submitRegi() {
      this.businessNoError = "";

      if (!this.selectedCompanyId) {
        alert("소속 업체를 선택해주세요.");
        return;
      }

      if (this.form.userType === "CORPORATE_PENDING" && !this.form.businessNo.trim()) {
        this.businessNoError = "사업자등록번호를 입력해주세요.";
        return;
      }

      const payload = {
        ...this.form,
        companyId: this.selectedCompanyId,
      };

      axios
        .post(`${API_BASE}/api/accounts`, payload)
        .then(() => {
          if (this.form.userType === "CORPORATE_PENDING") {
            alert("가입이 완료되었습니다. 관리자 승인 후 기업회원 기능을 이용하실 수 있습니다.");
          }
          this.$router.push("/login");
        })
        .catch((err) => {
          const msg = err.response?.data?.message || "회원가입에 실패했습니다.";

          // 사업자번호 관련 에러는 입력창 아래 인라인으로 표시
          if (msg.includes("사업자")) {
            this.businessNoError = msg;
            this.$nextTick(() => {
              document.querySelector('input[placeholder*="사업자"]')?.focus();
            });
          } else {
            alert(msg);
          }
        });
    },
  },
};
</script>

<style scoped>
.regi-wrap {
  width: 100%;
  min-height: 100%;
  display: flex;
  justify-content: center;
  padding: 48px 16px;
  box-sizing: border-box;
  background: #f5f7fa;
}

.regi-card {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border: none;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(13, 110, 253, 0.12);
  overflow: hidden;
  box-sizing: border-box;
}

.regi-card-header {
  background: linear-gradient(135deg, #0d6efd, #0b5ed7);
  color: white;
  font-size: 26px;
  font-weight: 700;
  text-align: center;
  padding: 26px 20px;
  letter-spacing: 0.5px;
}

.regi-card-body {
  padding: 32px 28px 28px;
}

.regi-field {
  margin-bottom: 1.1rem;
}

.regi-field-last {
  margin-bottom: 1.6rem;
}

.regi-label {
  display: block;
  font-size: 14px;
  color: #33475b;
  font-weight: 600;
  margin-bottom: 6px;
}

.regi-radio-group {
  display: flex;
  gap: 20px;
}

.regi-radio {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14.5px;
  color: #33475b;
  cursor: pointer;
}

.regi-input {
  width: 100%;
  box-sizing: border-box;
  padding: 11px 14px;
  font-size: 14.5px;
  line-height: 1.5;
  color: #212529;
  background-color: #fff;
  border: 1px solid #dde3ea;
  border-radius: 10px;
  transition: all 0.15s ease;
}

.regi-input:focus {
  outline: none;
  border-color: #0d6efd;
  box-shadow: 0 0 0 3px rgba(13, 110, 253, 0.15);
}

.regi-input-full {
  display: block;
}

.regi-input-group {
  display: flex;
  align-items: stretch;
  width: 100%;
  gap: 0;
}

.regi-input-group .regi-input {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}

.regi-idcheck {
  font-size: 12.5px;
  margin-top: 6px;
}

.regi-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 14.5px;
  padding: 0 16px;
  border-radius: 10px;
  white-space: nowrap;
  box-sizing: border-box;
  transition: all 0.15s ease;
}

.regi-btn-outline {
  background: #fff;
  color: #0d6efd;
  border: 1px solid #dde3ea;
  border-left: none;
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
}

.regi-btn-outline:hover {
  background: #0d6efd;
  color: #fff;
  border-color: #0d6efd;
}

.regi-btn-primary {
  background: linear-gradient(135deg, #0d6efd, #0b5ed7);
  color: #fff;
  border: none;
  font-weight: 600;
  font-size: 16px;
  padding: 12px;
}

.regi-btn-primary:hover {
  opacity: 0.9;
}

.regi-btn-block {
  display: flex;
  width: 100%;
}
</style>