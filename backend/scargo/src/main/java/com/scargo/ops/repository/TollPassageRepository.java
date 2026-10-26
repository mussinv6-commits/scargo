package com.scargo.ops.repository;

import com.scargo.ops.entity.TollPassage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TollPassageRepository extends JpaRepository<TollPassage, Long> {
    List<TollPassage> findByPassageStatusAndBlockReason(String s, String r);
}
