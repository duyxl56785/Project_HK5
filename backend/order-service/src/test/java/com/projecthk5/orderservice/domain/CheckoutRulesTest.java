package com.projecthk5.orderservice.domain;

import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.ports.CartPort;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutRulesTest {
    @Test void financePolicyRequiresExplicitApprovalAndInputs() {
        MoneyRules denied = new MoneyRules(false,"","","");
        assertThrows(IllegalStateException.class,()->denied.fee(new BigDecimal("10010"),false));
        assertThrows(IllegalStateException.class,()->new MoneyRules(true,"","",""));
        MoneyRules approved = new MoneyRules(true,"0.05","0","true");
        assertEquals(new BigDecimal("501"),approved.fee(new BigDecimal("10010"),false));
        assertEquals(new BigDecimal("0"),approved.fee(new BigDecimal("10010"),true));
        assertEquals(new BigDecimal("30000"),MoneyRules.lineTotal(new BigDecimal("10000"),3));
        assertThrows(ArithmeticException.class,()->approved.fee(new BigDecimal("100.50"),false));
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

    @Test void malformedCartSnapshotMustNeverMatchExpectedItems() {
        var expected = List.of(new ExpectedItem("p1", 1));
        assertThrows(IllegalStateException.class, () -> CheckoutRules.requireSameItems(expected, null));
        assertThrows(IllegalStateException.class, () -> CheckoutRules.requireSameItems(expected, List.of()));
        assertThrows(IllegalStateException.class, () -> CheckoutRules.requireSameItems(expected,
            java.util.Arrays.asList((CartPort.Item) null)));
        for (String badId : java.util.Arrays.asList(null, "", "  ")) {
            assertThrows(IllegalStateException.class, () -> CheckoutRules.requireSameItems(expected,
                List.of(new CartPort.Item(badId, "store1", "seller1", 1, BigDecimal.ONE))));
        }
        for (int badQuantity : new int[]{-1, 0, 1001}) {
            assertThrows(IllegalStateException.class, () -> CheckoutRules.requireSameItems(expected,
                List.of(new CartPort.Item("p1", "store1", "seller1", badQuantity, BigDecimal.ONE))));
        }
        var one = new CartPort.Item("p1", "store1", "seller1", 1, BigDecimal.ONE);
        assertThrows(IllegalStateException.class, () -> CheckoutRules.requireSameItems(expected, List.of(one, one)));
    }

    @Test void malformedExpectedItemsFailClosedEvenWithoutControllerValidation() {
        var cart = List.of(new CartPort.Item("p1", "store1", "seller1", 1, BigDecimal.ONE));
        assertThrows(IllegalStateException.class, () -> CheckoutRules.requireSameItems(null, cart));
        assertThrows(IllegalStateException.class, () -> CheckoutRules.requireSameItems(List.of(), cart));
        assertThrows(IllegalStateException.class, () -> CheckoutRules.requireSameItems(
            java.util.Arrays.asList((ExpectedItem) null), cart));
        for (String badId : java.util.Arrays.asList(null, "", "  ")) {
            assertThrows(IllegalStateException.class, () -> CheckoutRules.requireSameItems(
                List.of(new ExpectedItem(badId, 1)), cart));
        }
        for (int badQuantity : new int[]{-1, 0, 1001}) {
            assertThrows(IllegalStateException.class, () -> CheckoutRules.requireSameItems(
                List.of(new ExpectedItem("p1", badQuantity)), cart));
        }
        assertDoesNotThrow(() -> CheckoutRules.requireSameItems(
            List.of(new ExpectedItem("p1", 1000)),
            List.of(new CartPort.Item("p1", "store1", "seller1", 1000, BigDecimal.ONE))));
    }
}
