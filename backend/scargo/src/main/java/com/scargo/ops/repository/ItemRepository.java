package com.scargo.ops.repository;

import com.scargo.ops.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {
    long countByItemIdInAndStatus(java.util.Collection<Long> ids, String status);
}
