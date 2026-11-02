package com.projecthk5.authservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.service.AuthService;
import com.projecthk5.authservice.service.WriteReservations;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InternalAccountControllerTest {
    private final AuthService auth = mock(AuthService.class);
    private final WriteReservations reservations = mock(WriteReservations.class);
    private final InternalAccountController controller = new InternalAccountController(auth, reservations, "pair-token");

    /** A suspended Seller keeps the role, so the fence must close on a grant too or a write checked before it commits after. */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void everySellerStateChangeStopsWritesInFlightAfterItCommits(boolean granted) {
        UUID accountId = UUID.randomUUID();
        when(auth.changeSellerRole(any(), anyBoolean(), anyLong())).thenReturn(new Account(accountId, "seller@example.com",
                "hash", AuthService.ACTIVE, Set.of(Account.Role.CUSTOMER), Instant.now()));

        controller.sellerRole("pair-token", accountId, new InternalAccountController.RoleChange(granted, 3L));

        var order = inOrder(auth, reservations);
        order.verify(auth).changeSellerRole(eq(accountId), eq(granted), eq(3L));
        order.verify(reservations).closeAll(accountId);
    }

    @Test
    void anotherCallerCannotChangeTheRole() {
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> controller.sellerRole(
                "wrong", UUID.randomUUID(), new InternalAccountController.RoleChange(true, 3L)));
    }
}
