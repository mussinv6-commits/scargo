package com.scargo.ops;

import com.scargo.ops.dto.SealRequests.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 26.10.21 씰번호 필수: 빈 값·형식 오류는 서버 검증에서 걸린다 (→ 400) */
class SealRequestValidationTests {
    private final Validator v = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void pickupRequiresSeal() {
        assertFalse(v.validate(new PickupRequest("SCGU1234565", "", true)).isEmpty());
        assertFalse(v.validate(new PickupRequest("SCGU1234565", "   ", true)).isEmpty());
        assertFalse(v.validate(new PickupRequest("SCGU1234565", null, true)).isEmpty());
        assertFalse(v.validate(new PickupRequest("SCGU1234565", "a b!", true)).isEmpty());
        assertTrue(v.validate(new PickupRequest("SCGU1234565", "SC-1001", true)).isEmpty());
    }

    @Test
    void departRequiresSeal() {
        assertFalse(v.validate(new DepartRequest("")).isEmpty());
        assertTrue(v.validate(new DepartRequest("KR-LH-208431")).isEmpty());
    }

    @Test
    void hubReceiveRequiresSealUnlessMissing() {
        assertFalse(v.validate(new HubReceiveRequest("", "INTACT")).isEmpty());
        assertFalse(v.validate(new HubReceiveRequest(null, "DAMAGED")).isEmpty());
        assertTrue(v.validate(new HubReceiveRequest(null, "MISSING")).isEmpty());
        assertTrue(v.validate(new HubReceiveRequest("SC-1009", "INTACT")).isEmpty());
        assertFalse(v.validate(new HubReceiveRequest("SC-1009", "BROKEN")).isEmpty());
    }
}
