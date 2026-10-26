package com.scargo.ops.repository;

import com.scargo.ops.entity.Parcel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ParcelRepository extends JpaRepository<Parcel, String> {
    List<Parcel> findByUnitId(String unitId);
    long countByUnitId(String unitId);
}
