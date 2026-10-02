package com.scargo.service;

import com.scargo.Enum.NotificationType;
import com.scargo.dto.NotificationCreateRequest;
import com.scargo.entity.Account;
import com.scargo.entity.Container;
import com.scargo.entity.LoadingLocation;
import com.scargo.entity.Truck;
import com.scargo.repository.YardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DriverAlertService {

    private final NotificationService notificationService;
    private final YardRepository yardRepository;

    // 사업자가 기사에게 차량을 배정했을 때
    public void notifyDriverAssigned(Truck truck) {
        Account driver = driverOf(truck);
        if (driver == null) return;
        send(driver.getAccountId(), "차량 배정", truck.getVehicleNo() + " 차량이 배정되었습니다.");
    }

    // 사업자가 차량에 컨테이너를 매핑했을 때. 그 차량의 담당 기사에게 보낸다.
    public void notifyContainerMapped(Truck truck, Container container) {
        Account driver = driverOf(truck);
        if (driver == null || container == null) return;
        String message = "컨테이너 번호 " + container.getContainerNo() + ", 위치 " + locationLabel(container.getLoadingLocation());
        send(driver.getAccountId(), "컨테이너 배정", message);
    }

    private Account driverOf(Truck truck) {
        if (truck == null || truck.getAssignedDriver() == null || truck.getAssignedDriver().getAccountId() == null) {
            return null;
        }
        return truck.getAssignedDriver();
    }

    private String locationLabel(LoadingLocation location) {
        if (location == null) return "미지정";
        String yardName = "";
        if (location.getYardId() != null) {
            yardName = yardRepository.findById(location.getYardId())
                    .map(yard -> yard.getYardName() == null ? "" : yard.getYardName())
                    .orElse("");
        }
        String sector = location.getSector() == null ? "" : location.getSector();
        String label = (yardName + " " + sector).trim();
        return label.isEmpty() ? "미지정" : label;
    }

    private void send(Long accountId, String title, String message) {
        notificationService.createNotification(NotificationCreateRequest.builder()
                .accountId(accountId)
                .title(title)
                .message(message)
                .notificationType(NotificationType.NOTICE)
                .build());
    }
}
