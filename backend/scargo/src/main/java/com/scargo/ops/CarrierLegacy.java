package com.scargo.ops;

import com.scargo.entity.Account;
import com.scargo.entity.Company;
import com.scargo.repository.CompanyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 26.10.21 운송사 담당자 = accounts.user_type CORPORATE_APPROVED. 승인은 회사 단위(companies.is_carrier + approval_status) */
@Component
@RequiredArgsConstructor
public class CarrierLegacy {
    private static CompanyRepository repo;
    private final CompanyRepository companyRepository;

    @PostConstruct void init() { repo = companyRepository; }

    public static Company companyOf(Account a) {
        return (repo == null || a.getCompanyId() == null) ? null : repo.findById(a.getCompanyId()).orElse(null);
    }

    /** 승인된 운송사의 담당자인가 = CORPORATE_APPROVED + 회사 is_carrier + APPROVED */
    public static boolean isApprovedCarrierContact(Account a) {
        if (a.getUserType() != Account.UserType.CORPORATE_APPROVED) return false;
        Company c = companyOf(a);
        return c != null && Boolean.TRUE.equals(c.getIsCarrier()) && "APPROVED".equals(c.getApprovalStatus());
    }

    /** 26.10.21 v7 복구: AMR 관리자(S-Bot) = ADMIN 이면서 아이디가 sbot 으로 시작. AccountResponse.adminScope = SBOT 과 같은 규칙 */
    public static boolean isSbotAdmin(Account a) {
        return a.getUserType() == Account.UserType.ADMIN && a.getUserId() != null && a.getUserId().toLowerCase().startsWith("sbot");
    }

    /** 응답 표시용(저장·권한 아님): CORPORATE_APPROVED 인데 회사가 승인 전이면 CORPORATE_PENDING */
    public static String displayUserType(Account a) {
        if (a.getUserType() == Account.UserType.CORPORATE_APPROVED && !isApprovedCarrierContact(a)) return "CORPORATE_PENDING";
        return a.getUserType().name();
    }

    /** ROLE_<user_type> + (승인 운송사) ROLE_CARRIER.
     *  보안: 회사 승인 전 CORPORATE_APPROVED 계정에는 ROLE_CORPORATE_APPROVED 를 주지 않는다 (승인 전 운송사 화면/API 차단) */
    public static List<String> roles(Account a) {
        List<String> r = new ArrayList<>();
        // 26.10.21 역할 분리(v7 복구): S-Bot 관리자는 ROLE_ADMIN 을 받지 않는다 → S Cargo 관리자 API 전부 403
        if (isSbotAdmin(a)) { r.add("ROLE_AMR_ADMIN"); return r; }
        if (a.getUserType() == Account.UserType.CORPORATE_APPROVED && !isApprovedCarrierContact(a)) { r.add("ROLE_CORPORATE_PENDING"); return r; }
        // 26.10.21 v9: 가입 승인 전(PENDING)·반려 기사는 기사 권한 없음 → 앱은 승인 대기(A-03)만
        boolean driver = a.getUserType() == Account.UserType.CONTAINER_DRIVER || a.getUserType() == Account.UserType.BRANCH_DRIVER;
        if (driver && a.getApprovalStatus() != null && !"APPROVED".equals(a.getApprovalStatus())) { r.add("ROLE_PENDING_DRIVER"); return r; }
        r.add("ROLE_" + a.getUserType().name());
        if (isApprovedCarrierContact(a)) r.add("ROLE_CARRIER");
        return r;
    }
}
