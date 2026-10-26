package com.scargo.app.repository;

import com.scargo.app.entity.TransportOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransportOrderRepository extends JpaRepository<TransportOrder, Long> {

    /** 기사 본인(계정) 또는 배정 차량 기준 진행 중 오더 */
    List<TransportOrder> findByCompanyIdOrderByOrderIdDesc(Long companyId);
    List<TransportOrder> findByDriverAccountIdAndStatusInOrderByAssignedAtAsc(Long driverAccountId, Collection<String> statuses);

    List<TransportOrder> findByVehicleNoAndStatusInOrderByAssignedAtAsc(String vehicleNo, Collection<String> statuses);

    Optional<TransportOrder> findFirstByVehicleNoAndContainerNoAndStatusIn(String vehicleNo, String containerNo, Collection<String> statuses);

    List<TransportOrder> findByDriverAccountIdAndStatusAndHandedOverAtBetweenOrderByHandedOverAtDesc(
            Long driverAccountId, String status, OffsetDateTime from, OffsetDateTime to);

    boolean existsByOrderNo(String orderNo);
}
