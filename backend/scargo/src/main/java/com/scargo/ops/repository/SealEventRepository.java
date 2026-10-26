package com.scargo.ops.repository;

import com.scargo.ops.entity.SealEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SealEventRepository extends JpaRepository<SealEvent, Long> {
    List<SealEvent> findBySealIdOrderByOccurredAtDesc(Long sealId);
    List<SealEvent> findByOrderIdOrderByOccurredAtDesc(Long orderId); // fix_1630: W-07 오더별 씰 이력
}
