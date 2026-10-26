package com.scargo.ops.service;

import com.scargo.Enum.NotificationType;
import com.scargo.entity.Account;
import com.scargo.entity.Notification;
import com.scargo.entity.Truck;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.NotificationRepository;
import com.scargo.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 26.10.20 최종 알림 규칙 (notifications.notification_type CHECK 14종, chk_notification_target: account_id 또는 company_id)
 *  운송사 담당자 몫은 company_id 로만 넣는다 → 웹 알림 목록에만 보이고 푸시는 보내지 않는다 (NotificationService 의 company 단위 푸시 제외 규칙 그대로) */
@Service
@RequiredArgsConstructor
public class OpsNotifier {
    private final AccountRepository accounts;
    private final NotificationRepository notifications;
    private final TruckRepository trucks;

    public void toAccount(Long accountId, NotificationType type, String title, String msg, Long ref) {
        notifications.save(Notification.builder().accountId(accountId).title(title).message(msg)
                .notificationType(type.name()).referenceId(ref).build());
    }

    public void toCompany(Long companyId, NotificationType type, String title, String msg, Long ref) {
        if (companyId == null) return;
        notifications.save(Notification.builder().companyId(companyId).title(title).message(msg)
                .notificationType(type.name()).referenceId(ref).build());
    }

    /** CONTROL 전원 + ADMIN(fix_1530, sbot 제외) + (centerId 가 있으면) 그 거점의 HUB_OP·CENTER_OP */
    public void toOps(Long centerId, NotificationType type, String title, String msg, Long ref) {
        for (Account a : accounts.findAll()) {
            boolean to = a.getUserType() == Account.UserType.CONTROL
                    || (a.getUserType() == Account.UserType.ADMIN && !a.getUserId().toLowerCase().startsWith("sbot")) // fix_1530: 총관리자도 알림 종으로 받음 (S-Bot 전용 계정 제외)
                    || ((a.getUserType() == Account.UserType.HUB_OP || a.getUserType() == Account.UserType.CENTER_OP)
                        && centerId != null && Objects.equals(a.getScopeCenterId(), centerId));
            if (to) toAccount(a.getAccountId(), type, title, msg, ref);
        }
    }

    /** 그 거점의 HUB_OP·CENTER_OP 만 */
    public void toSiteOps(Long centerId, NotificationType type, String title, String msg, Long ref) {
        if (centerId == null) return;
        for (Account a : accounts.findAll())
            if ((a.getUserType() == Account.UserType.HUB_OP || a.getUserType() == Account.UserType.CENTER_OP) && Objects.equals(a.getScopeCenterId(), centerId))
                toAccount(a.getAccountId(), type, title, msg, ref);
    }

    /** 차량번호 → trucks.company_id (운송사). 미등록 차량이면 null */
    public Long companyOfVehicle(String vehicleNo) {
        if (vehicleNo == null) return null;
        return trucks.findById(vehicleNo).map(Truck::getCompany).map(c -> c.getCompanyId()).orElse(null);
    }
}
