package com.scargo.app.repository;

import com.scargo.app.entity.TollOffice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface TollOfficeRepository extends JpaRepository<TollOffice, String> {

    List<TollOffice> findByLatitudeBetweenAndLongitudeBetween(
            BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng);

    List<TollOffice> findByOfficeNameContainingOrRouteNameContaining(String name, String routeName);
}
