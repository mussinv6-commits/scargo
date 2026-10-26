package com.scargo.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.scargo.app.entity.Terminal;

public interface TerminalRepository extends JpaRepository<Terminal, Long> {
}
