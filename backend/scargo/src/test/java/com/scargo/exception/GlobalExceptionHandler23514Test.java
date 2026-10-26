package com.scargo.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/** v12: SQLState 23514 (CHECK·트리거) → 409, 다른 제약 → 400 */
class GlobalExceptionHandler23514Test {
    private final GlobalExceptionHandler h = new GlobalExceptionHandler();

    @Test
    void check23514IsConflict() {
        var e = new DataIntegrityViolationException("x", new SQLException("ERROR: 반납 기사는 오더 운송사 소속 CONTAINER_DRIVER 여야 합니다\n  Where: ...", "23514"));
        var r = h.handleDataIntegrity(e);
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals("반납 기사는 오더 운송사 소속 CONTAINER_DRIVER 여야 합니다", r.getBody().get("message"));
    }

    @Test
    void otherConstraintIsBadRequest() {
        var r = h.handleDataIntegrity(new DataIntegrityViolationException("x", new SQLException("ERROR: duplicate key", "23505")));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    @Test
    void wrapped23514IsConflict() {
        var r = h.handleDataAccess(new UncategorizedDataAccessException("x", new SQLException("ERROR: 공컨 점검자는 HUB_OP 또는 ADMIN 이어야 합니다", "23514")) {});
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
    }
}
