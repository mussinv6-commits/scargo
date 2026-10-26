package com.scargo.ops.service;

import com.scargo.entity.Account;
import com.scargo.ops.entity.LogisticsCenter;
import com.scargo.ops.repository.LogisticsCenterRepository;
import com.scargo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 26.10.20 HUB_OP·CENTER_OP 소속 거점 검증 (DB CHECK 는 NOT NULL 만, center_type 은 여기서) */
@Service
@RequiredArgsConstructor
public class OpsAccountService {
    private final LogisticsCenterRepository centers;
    private final AccountRepository accounts;
    private final com.scargo.app.repository.HubRepository hubs;

    /** 26.10.21 DB 최종통합: HUB_OP → hub_id(hubs) 필수·center_id 금지, CENTER_OP → center_id(DISTRIBUTION_CENTER) 필수·hub_id 금지,
     *  그 외 권한은 둘 다 NULL (chk_accounts_hub_scope / chk_accounts_center_scope) */
    public void validateCenter(Account a) {
        Account.UserType t = a.getUserType();
        if (t == Account.UserType.HUB_OP) {
            if (a.getHubId() == null) throw new IllegalArgumentException("HUB_OP 계정은 소속 허브(hub_id)가 필요합니다.");
            if (a.getCenterId() != null) throw new IllegalArgumentException("HUB_OP 계정은 center_id 를 가질 수 없습니다 (hub_id 사용).");
            if (!hubs.existsById(a.getHubId())) throw new IllegalArgumentException("없는 허브입니다: " + a.getHubId());
        } else if (t == Account.UserType.CENTER_OP) {
            if (a.getCenterId() == null) throw new IllegalArgumentException("CENTER_OP 계정은 소속 지역센터(center_id)가 필요합니다.");
            if (a.getHubId() != null) throw new IllegalArgumentException("CENTER_OP 계정은 hub_id 를 가질 수 없습니다.");
            LogisticsCenter c = centers.findById(a.getCenterId()).orElseThrow(() -> new IllegalArgumentException("없는 거점입니다: " + a.getCenterId()));
            if (!"DISTRIBUTION_CENTER".equals(c.getCenterType()))
                throw new IllegalArgumentException("CENTER_OP 의 소속 거점은 DISTRIBUTION_CENTER 이어야 합니다 (지금: " + c.getCenterType() + ")");
        } else if (a.getHubId() != null || a.getCenterId() != null) {
            throw new IllegalArgumentException(t + " 계정은 hub_id·center_id 를 가질 수 없습니다.");
        }
    }

    /** 직원관리(W-16)에서 쓸 저장 경로. TODO: 1차 AccountService 의 생성/수정에도 이 검증 연결 */
    @Transactional
    public Account save(Account a) { validateCenter(a); validateCarrierContact(a); return accounts.save(a); }

    private final com.scargo.repository.CompanyRepository companies;

    /** 26.10.21: CORPORATE_APPROVED(운송사 담당자)는 company_id 필수, 그 회사가 is_carrier 여야 함 */
    public void validateCarrierContact(Account a) {
        if (a.getUserType() != Account.UserType.CORPORATE_APPROVED) return;
        if (a.getCompanyId() == null) throw new IllegalArgumentException("운송사 담당자는 소속 업체가 있어야 합니다.");
        if (!companies.findById(a.getCompanyId()).map(c -> Boolean.TRUE.equals(c.getIsCarrier())).orElse(false))
            throw new IllegalArgumentException("소속 업체가 운송사(is_carrier)가 아닙니다.");
    }
}
