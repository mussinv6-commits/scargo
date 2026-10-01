<template>
  <div class="regi-page">
    <div class="regi-box">
      <div class="card">
        <div class="card-header">회원가입</div>

        <div class="card-body p-4">
          <form @submit.prevent="submitRegi">

            <!-- 1. 회원 유형 -->
            <div class="form-row">
              <label class="form-label">회원 유형</label>
              <div class="radio-group">
                <label class="radio-option">
                  <input type="radio" value="GENERAL" v-model="form.userType" />
                  일반회원
                </label>
                <label class="radio-option">
                  <input type="radio" value="CORPORATE_PENDING" v-model="form.userType" />
                  기업회원
                </label>
              </div>
            </div>

            <!-- 2. 아이디 -->
            <div class="form-row">
              <label class="form-label">아이디</label>
              <input
                type="text"
                class="form-control"
                id="userId"
                v-model="form.userId"
                placeholder="아이디를 입력하세요"
              />
              <button class="id-check-btn" type="button" @click="checkId">
                중복확인
              </button>
            </div>
            <div class="id-check-msg" :style="{ color: idCheckColor }" v-html="idCheckMsg"></div>

            <!-- 3, 4. 비밀번호 + 표시/숨김 -->
            <div class="form-row">
              <label class="form-label">비밀번호</label>
              <div class="password-wrapper">
                <input
                  :type="pwVisible ? 'text' : 'password'"
                  class="form-control"
                  v-model="form.userPw"
                  placeholder="비밀번호를 입력하세요"
                />
                <button
                  type="button"
                  class="pw-toggle-btn"
                  @click="pwVisible = !pwVisible"
                  :aria-label="pwVisible ? '비밀번호 숨기기' : '비밀번호 보기'"
                >
                  <svg v-if="pwVisible" viewBox="0 0 24 24" width="18" height="18">
                    <path
                      fill="currentColor"
                      d="M12 5c-7 0-10 7-10 7s3 7 10 7 10-7 10-7-3-7-10-7zm0 12a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-2a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"
                    />
                  </svg>
                  <svg v-else viewBox="0 0 24 24" width="18" height="18">
                    <path
                      fill="currentColor"
                      d="M3.28 2.22 2.22 3.28l4.02 4.02C4.16 8.6 2.6 10.53 2 12c0 0 3 7 10 7 1.8 0 3.36-.46 4.68-1.14l3.04 3.04 1.06-1.06L3.28 2.22zM12 17c-4.42 0-6.86-3.44-7.62-5C5.03 10.7 6.22 9.24 7.7 8.4l1.6 1.6a3 3 0 0 0 4.7 3.7l1.24 1.24C14.36 15.7 13.24 17 12 17zm.02-9.98c.98.05 1.92.24 2.78.55l-1.6 1.6a3 3 0 0 0-1.18-.15l-2-2c.66-.02 1.32 0 2 0zM22 12s-3-7-10-7c-.63 0-1.23.06-1.8.15l1.72 1.72c.03 0 .06-.01.08-.01a3 3 0 0 1 3 3c0 .03 0 .06-.01.08l3.15 3.15C19.9 11.94 20.9 10.24 22 12z"
                    />
                  </svg>
                </button>
              </div>
            </div>

            <!-- 5. 이름 -->
            <div class="form-row">
              <label class="form-label">이름</label>
              <input
                type="text"
                class="form-control"
                v-model="form.userName"
                placeholder="이름을 입력하세요"
              />
            </div>

            <!-- 6, 7. 휴대폰 번호 (숫자만 입력하면 '-'가 자동으로 들어감, 최대 13자) -->
            <!-- 26.09.30 수정: 하이픈이 포함된 형태(010-1234-5678)로 저장하기 위해 maxlength를 13으로 변경 -->
            <div class="form-row">
              <label class="form-label">휴대폰 번호</label>
              <input
                type="text"
                inputmode="numeric"
                class="form-control"
                v-model="form.phoneNum"
                @input="onPhoneInput"
                maxlength="13"
                placeholder="숫자만 입력하면 '-'가 자동으로 들어갑니다"
              />
            </div>

            <!-- 8. 소속 업체 -->
            <!-- 26.09.21 수정: 기존/신규 업체 토글은 "기업회원"만 필요.
                 화물차 기사(일반회원)는 이미 있는 소속 회사를 고르기만 하면 되므로 토글 자체를 숨긴다. -->
            <div class="form-row form-row--wide-field" v-if="form.userType === 'CORPORATE_PENDING'">
              <label class="form-label">소속 업체</label>
              <div class="radio-group">
                <label class="radio-option">
                  <input type="radio" value="EXISTING" v-model="companyMode" />
                  기존 업체에서 선택
                </label>
                <label class="radio-option">
                  <input type="radio" value="NEW" v-model="companyMode" />
                  우리 업체가 목록에 없어요 (새로 등록)
                </label>
              </div>
            </div>

            <!-- 8-A. 기존 업체 선택 모드 (일반회원은 항상 이 모드, 기업회원은 토글로 선택 시) -->
            <template v-if="companyMode === 'EXISTING'">
              <div class="form-row">
                <label class="form-label">업체명</label>
                <select
                  class="form-select"
                  v-model="selectedCompanyId"
                  @change="onCompanySelect"
                >
                  <option value="" disabled>업체를 선택하세요</option>
                  <option v-for="c in companyOptions" :key="c.companyId" :value="c.companyId">
                    {{ c.companyName }}
                  </option>
                </select>
              </div>

              <div class="form-row">
                <label class="form-label">업체 주소</label>
                <input
                  type="text"
                  class="form-control"
                  :value="selectedAddress"
                  readonly
                  placeholder="업체 선택 시 자동 입력됩니다"
                />
              </div>

              <!-- 기존 업체를 선택한 경우: 그 업체의 등록된 사업자번호와 일치하는지 확인 -->
              <template v-if="form.userType === 'CORPORATE_PENDING'">
                <div class="form-row">
                  <label class="form-label">사업자 등록번호</label>
                  <input
                    type="text"
                    class="form-control"
                    v-model="form.businessNo"
                    @input="onBusinessNoInput"
                    maxlength="12"
                    inputmode="numeric"
                    placeholder="선택한 업체와 동일한 사업자번호 10자리(숫자만)"
                  />
                </div>
                <div class="field-error" v-if="businessNoError">{{ businessNoError }}</div>
              </template>
            </template>

            <!-- 8-B. 새 업체 등록 모드: 직접 입력 -->
            <template v-else>
              <div class="form-row">
                <label class="form-label">업체명</label>
                <input
                  type="text"
                  class="form-control"
                  v-model="newCompany.companyName"
                  placeholder="업체명을 입력하세요"
                />
              </div>

              <div class="form-row">
                <label class="form-label">업체 주소</label>
                <input
                  type="text"
                  class="form-control"
                  v-model="newCompany.address"
                  placeholder="업체 주소를 입력하세요"
                />
              </div>

              <div class="form-row">
                <label class="form-label">업종 (선택)</label>
                <input
                  type="text"
                  class="form-control"
                  v-model="newCompany.industryType"
                  placeholder="예: 컨테이너 운송업"
                />
              </div>

              <div class="form-row">
                <label class="form-label">대표자명 (선택)</label>
                <input
                  type="text"
                  class="form-control"
                  v-model="newCompany.representativeName"
                  placeholder="대표자명을 입력하세요"
                />
              </div>

              <!-- 기업회원이 새 업체를 등록하는 경우: 사업자등록번호는 필수 -->
              <template v-if="form.userType === 'CORPORATE_PENDING'">
                <div class="form-row">
                  <label class="form-label">사업자 등록번호</label>
                  <input
                    type="text"
                    class="form-control"
                    v-model="form.businessNo"
                    @input="onBusinessNoInput"
                    maxlength="12"
                    inputmode="numeric"
                    placeholder="'-' 없이 숫자 10자리만 입력하세요"
                  />
                </div>
                <div class="field-error" v-if="businessNoError">{{ businessNoError }}</div>
              </template>
              <div class="field-error" v-if="newCompanyError">{{ newCompanyError }}</div>
            </template>

            <button type="submit" class="regi-submit-btn">회원가입하기</button>
          </form>
        </div>
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
      companyMode: "EXISTING", // 26.09.21 추가: EXISTING(기존 업체 선택) | NEW(새 업체 직접 등록)
      newCompany: {
        companyName: "",
        address: "",
        industryType: "",
        representativeName: "",
      },
      newCompanyError: "",
      idCheckMsg: "",
      idCheckColor: "",
      idChecked: false,
      skipIdWatch: false,
      businessNoError: "",
      pwVisible: false,
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
        // 26.09.21 추가: 일반회원으로 바뀌면 "새 업체 등록" 모드였더라도 무조건 기존 업체 선택으로 되돌린다.
        this.companyMode = "EXISTING";
        this.newCompany = { companyName: "", address: "", industryType: "", representativeName: "" };
        this.newCompanyError = "";
      }
    },

    // 사업자번호를 다시 입력하면 에러 메시지 초기화
    "form.businessNo"() {
      this.businessNoError = "";
    },

    // 26.09.21 추가: 기존 업체 선택 <-> 새 업체 등록 전환 시 서로의 입력값/에러 초기화
    companyMode() {
      this.businessNoError = "";
      this.newCompanyError = "";
      if (this.companyMode === "EXISTING") {
        this.newCompany = { companyName: "", address: "", industryType: "", representativeName: "" };
      } else {
        this.selectedCompanyId = "";
        this.selectedAddress = "";
      }
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

    // 7. 휴대폰 번호: 숫자만 남기고 '-'를 자동으로 넣는다
    // 26.09.30 수정: 기존에는 숫자만 저장했지만, 이제 하이픈이 포함된 값(010-1234-5678)을 그대로 저장한다.
    onPhoneInput(e) {
      const formatted = this.formatPhone(e.target.value);
      this.form.phoneNum = formatted;
      // form 값만 바꾸면 포맷 결과가 이전과 같을 때 화면이 갱신되지 않아 입력한 문자가 남을 수 있으므로 화면 값도 맞춘다.
      e.target.value = formatted;
    },

    // 26.09.30 추가: 숫자만 추출해서 전화번호 형식으로 변환
    formatPhone(value) {
      const n = String(value || "").replace(/\D/g, "").slice(0, 11);

      if (n.length <= 3) return n;
      if (n.length <= 7) return `${n.slice(0, 3)}-${n.slice(3)}`;

      // 010 외 번호(011, 016 등)는 10자리일 수 있음 -> 3-3-4
      if (n.length <= 10 && !n.startsWith("010")) {
        return `${n.slice(0, 3)}-${n.slice(3, 6)}-${n.slice(6)}`;
      }

      // 11자리 -> 3-4-4
      return `${n.slice(0, 3)}-${n.slice(3, 7)}-${n.slice(7)}`;
    },

    // 26.09.21 추가: 사업자등록번호도 '-' 없이 숫자 10자리만 입력받도록 제한
    onBusinessNoInput(e) {
      // 붙여넣기(123-45-67890) 시 maxlength=10 이면 하이픈 포함 앞 10글자만 들어와 숫자가 8자리가 된다.
      const digitsOnly = String(e.target.value || "").replace(/\D/g, "").slice(0, 10);
      this.form.businessNo = digitsOnly;
      e.target.value = digitsOnly;
    },

    // 26.09.21 추가: 화면에는 숫자 10자리만 입력받지만, DB(companies.business_no)는
    // "000-00-00000" 형식(chk_companies_biz_no_format 제약조건)을 요구하므로
    // 실제 서버로 보낼 때만 하이픈을 넣어 변환한다.
    formatBusinessNo(digits) {
      if (!digits || digits.length !== 10) return digits; // 10자리가 아니면 그대로 반환 (백엔드 검증에서 에러 처리)
      return `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5, 10)}`;
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
      this.newCompanyError = "";

      // 26.09.30 수정: 휴대폰 번호는 입력한 경우에만 형식(010-1234-5678)을 검사한다.
      // (기존 코드도 비워두면 통과하는 구조였으므로 동일하게 유지. 필수로 바꾸려면 앞의 this.form.phoneNum && 를 지운다)
      if (this.form.phoneNum && !/^01[016789]-\d{3,4}-\d{4}$/.test(this.form.phoneNum)) {
        alert("휴대폰 번호를 올바르게 입력해주세요. (예: 010-1234-5678)");
        return;
      }

      if (this.form.userType === "CORPORATE_PENDING" && this.form.businessNo.trim().length !== 10) {
        this.businessNoError = "사업자등록번호 10자리를 모두 입력해주세요.";
        return;
      }

      if (this.companyMode === "EXISTING") {
        // 기존 방식: 이미 등록된 업체를 선택 + (기업회원이면) 그 업체의 사업자번호와 일치 확인
        if (!this.selectedCompanyId) {
          alert("소속 업체를 선택해주세요.");
          return;
        }
        this.createAccountWithCompany(this.selectedCompanyId);
      } else {
        // 26.09.21 추가: 목록에 없는 업체 -> 직접 입력받아 업체부터 새로 만든 뒤, 그 companyId로 계정 생성
        if (!this.newCompany.companyName.trim() || !this.newCompany.address.trim()) {
          this.newCompanyError = "업체명과 업체 주소를 입력해주세요.";
          return;
        }

        const companyPayload = {
          companyName: this.newCompany.companyName,
          address: this.newCompany.address,
          industryType: this.newCompany.industryType || null,
          representativeName: this.newCompany.representativeName || null,
          // 기업회원이 새 업체를 등록하는 경우, 방금 입력한 사업자번호를 업체 레코드에도 그대로 심어둔다.
          // (그래야 뒤이은 계정 생성 시 "사업자번호가 업체 정보와 일치하는지" 검증을 자연스럽게 통과한다)
          // 26.09.21 수정: 화면엔 숫자 10자리만 받고, DB 형식(000-00-00000)에 맞춰 하이픈을 넣어 전송
          businessNo: this.form.userType === "CORPORATE_PENDING" ? this.formatBusinessNo(this.form.businessNo) : null,
        };

        axios
          .post(`${API_BASE}/api/companies`, companyPayload)
          .then((resp) => {
            const newCompanyId = resp.data.companyId;
            this.createAccountWithCompany(newCompanyId);
          })
          .catch((err) => {
            this.newCompanyError =
              err.response?.data?.message || "업체 등록에 실패했습니다.";
          });
      }
    },

    // 26.09.21 추가: 계정 생성 요청을 공통 함수로 분리 (기존 업체 선택 / 새 업체 등록 두 경로 모두 여기로 합류)
    createAccountWithCompany(companyId) {
      const payload = {
        ...this.form,
        companyId,
        // 26.09.21 수정: 화면엔 숫자 10자리만 받고, 업체의 사업자번호(DB엔 000-00-00000 형식으로 저장됨)와
        // 정확히 비교되도록 전송 직전에 하이픈을 넣어 변환
        businessNo:
          this.form.userType === "CORPORATE_PENDING"
            ? this.formatBusinessNo(this.form.businessNo)
            : this.form.businessNo,
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

<style src="@/components/CSS/regi.css" scoped></style>