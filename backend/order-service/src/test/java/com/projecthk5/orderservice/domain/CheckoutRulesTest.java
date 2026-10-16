package com.projecthk5.orderservice.domain;

import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.ports.CartPort;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutRulesTest {
    @Test void merchantFeeFivePercentAndHouseFeeZero() {
        assertEquals(new BigDecimal("501"),MoneyRules.fee(new BigDecimal("10010"),false));
        assertEquals(BigDecimal.ZERO,MoneyRules.fee(new BigDecimal("10010"),true));
        assertEquals(new BigDecimal("30000"),MoneyRules.lineTotal(new BigDecimal("10000"),3));
    }
    @Test void expectedItemsMustExactlyMatchCart() {
        var actual=List.of(new CartPort.Item("p1","store1","seller1",2,new BigDecimal("10000")));
        assertDoesNotThrow(() -> CheckoutRules.requireSameItems(List.of(new ExpectedItem("p1",2)),actual));
        assertThrows(IllegalStateException.class,() -> CheckoutRules.requireSameItems(List.of(new ExpectedItem("p1",3)),actual));
        assertThrows(IllegalStateException.class,() -> CheckoutRules.requireSameItems(List.of(new ExpectedItem("p1",2),new ExpectedItem("p1",2)),actual));
    }
    @Test void idempotencyFingerprintOrderIndependentButRevisionSensitive() {
        var a=new CheckoutRequest(List.of(new ExpectedItem("p2",1),new ExpectedItem("p1",2)),4L);
        var b=new CheckoutRequest(List.of(new ExpectedItem("p1",2),new ExpectedItem("p2",1)),4L);
        var c=new CheckoutRequest(List.of(new ExpectedItem("p1",2),new ExpectedItem("p2",1)),5L);
        assertEquals(RequestFingerprint.compute(a),RequestFingerprint.compute(b));
        assertNotEquals(RequestFingerprint.compute(a),RequestFingerprint.compute(c));
    }
}
