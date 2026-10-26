package com.scargo.ops;

import com.scargo.app.security.CurrentAccount;
import com.scargo.entity.Account;
import com.scargo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 26.10.20 승인 운송사 담당자만 W-07·W-08·W-10. 다른 운송사 데이터는 403 */
@Component
@RequiredArgsConstructor
public class CarrierGuard {
    private final AccountRepository accounts;

    /** 내 운송사 company_id (승인 운송사 담당자가 아니면 403) */
    public Long myCompany() {
        Account me = accounts.findById(CurrentAccount.id()).orElseThrow();
        if (!CarrierLegacy.isApprovedCarrierContact(me)) throw new AccessDeniedException("승인된 운송사 담당자만 사용할 수 있습니다.");
        return me.getCompanyId();
    }

    public void requireOwn(Long companyId) {
        if (!Objects.equals(myCompany(), companyId)) throw new AccessDeniedException("다른 운송사의 데이터입니다.");
    }
}
