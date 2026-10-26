package com.scargo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** fix_1300: @PreAuthorize 에서 차량 소유 회사 확인용 */
@Component("truckOwnership")
@RequiredArgsConstructor
public class TruckOwnership {
    private final JdbcTemplate jdbc;

    public boolean owns(String vehicleNo, Long companyId) {
        if (vehicleNo == null || companyId == null) return false;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM trucks WHERE vehicle_no = ? AND company_id = ?", Integer.class, vehicleNo, companyId);
        return n != null && n > 0;
    }
}
