package com.projecthk5.orderservice.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryOperationIntentTest {
    private static final UUID ATTEMPT = UUID.fromString("47e38db6-75b9-46ef-949e-d73776798651");
    private static final InventoryOperationIntent.Line A = new InventoryOperationIntent.Line("sku-A", 2);
    private static final InventoryOperationIntent.Line B = new InventoryOperationIntent.Line("sku-B", 1);

    @Test void requestHashIsCanonicalButOperationIdIsSeparate() {
        var one = InventoryOperationIntent.prepare(ATTEMPT, "reserve-1", InventoryOperationIntent.Type.RESERVE, List.of(B, A));
        var anotherId = InventoryOperationIntent.prepare(ATTEMPT, "reserve-2", InventoryOperationIntent.Type.RESERVE, List.of(A, B));
        assertEquals(one.fingerprint(), anotherId.fingerprint());
        assertEquals(List.of(A, B), one.lines());
        assertEquals("reserve-1", one.operationId());
        assertEquals(ATTEMPT, one.checkoutAttemptId());
        assertEquals(64, one.fingerprint().length());
        // Canonical payload vector independently checked against TV4 requestHash().
        assertEquals("c1132a9943e91f226d2a8ee3149194d9617324e4f0351fcfe7baf1016dcb7b2a", one.fingerprint());
    }

    @Test void journalRehydrateRequiresIdenticalFingerprint() {
        var intent = InventoryOperationIntent.prepare(ATTEMPT, "reserve-1", InventoryOperationIntent.Type.RESERVE, List.of(B, A));
        var loaded = InventoryOperationIntent.fromJournal(ATTEMPT, "reserve-1", InventoryOperationIntent.Type.RESERVE,
                List.of(A, B), intent.fingerprint());
        assertEquals(intent.fingerprint(), loaded.fingerprint());
        assertThrows(IllegalStateException.class, () -> InventoryOperationIntent.fromJournal(ATTEMPT,
                "reserve-1", InventoryOperationIntent.Type.RESERVE, List.of(A, B), "wrong"));
    }

    @Test void changedTypeOrQuantityIsNotAValidReplay() {
        var intent = InventoryOperationIntent.prepare(ATTEMPT, "reserve-1", InventoryOperationIntent.Type.RESERVE, List.of(A, B));
        assertDoesNotThrow(() -> intent.requireSamePayload(InventoryOperationIntent.Type.RESERVE, List.of(B, A)));
        assertThrows(IllegalArgumentException.class, () -> intent.requireSamePayload(InventoryOperationIntent.Type.RELEASE, List.of(A, B)));
        assertThrows(IllegalArgumentException.class, () -> intent.requireSamePayload(InventoryOperationIntent.Type.RESERVE,
                List.of(new InventoryOperationIntent.Line("sku-A", 3), B)));
    }

    @Test void lineSetCannotBeMutatedOrHaveDuplicates() {
        var input = new ArrayList<>(List.of(A, B));
        var intent = InventoryOperationIntent.prepare(ATTEMPT, "reserve-1", InventoryOperationIntent.Type.RESERVE, input);
        input.clear();
        assertEquals(2, intent.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> intent.lines().add(A));
        assertThrows(IllegalArgumentException.class, () -> InventoryOperationIntent.prepare(ATTEMPT,
                "reserve-2", InventoryOperationIntent.Type.RESERVE, List.of(A, A)));
    }

    @Test void unicodeBoundaryPaddedSkuFailsClosedWithoutChangingExactIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryOperationIntent.Line("\u2003sku-A", 2));
        assertThrows(IllegalArgumentException.class, () -> new InventoryOperationIntent.Line("sku-A\u2003", 2));
        assertThrows(IllegalArgumentException.class, () -> new InventoryOperationIntent.Line("\u00a0sku-A", 2));
        assertThrows(IllegalArgumentException.class, () -> new InventoryOperationIntent.Line("sku-A\u00a0", 2));
        // Preserve the previous ASCII-control check; neither trim nor fingerprint normalizes IDs.
        assertThrows(IllegalArgumentException.class, () -> new InventoryOperationIntent.Line("\u0000sku-A", 2));
        assertEquals("sku\u2003A", new InventoryOperationIntent.Line("sku\u2003A", 2).sku());
        assertEquals("sku-A", A.sku());
    }

    @Test void invalidExternalInventoryIdentifiersFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryOperationIntent.Line(" sku-A", 2));
        assertThrows(IllegalArgumentException.class, () -> new InventoryOperationIntent.Line("sku-A ", 2));
        assertThrows(IllegalArgumentException.class, () -> new InventoryOperationIntent.Line("sku-A", 0));
        assertThrows(IllegalArgumentException.class, () -> InventoryOperationIntent.prepare(ATTEMPT,
                "reserve-1", InventoryOperationIntent.Type.RESERVE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> InventoryOperationIntent.prepare(ATTEMPT,
                " ", InventoryOperationIntent.Type.RESERVE, List.of(A)));
        assertThrows(IllegalArgumentException.class, () -> InventoryOperationIntent.prepare(ATTEMPT,
                "x".repeat(129), InventoryOperationIntent.Type.RESERVE, List.of(A)));
    }
}
