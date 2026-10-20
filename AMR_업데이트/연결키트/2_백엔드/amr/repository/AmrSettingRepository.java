package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmrSettingRepository extends JpaRepository<AmrSetting, Long> {
}
