package com.projecthk5.productservice.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.projecthk5.security.ApiException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class InitialStockTest {
    private final DependencyClients inventory = mock(DependencyClients.class);

    @BeforeEach void transaction() { TransactionSynchronizationManager.initSynchronization(); }
    @AfterEach void clear() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test void setsEachPositiveTargetAndSkipsZeroWithoutAnInventoryRow() {
        InitialStock.apply(inventory, List.of(line("A", 5), line("B", 0)));
        verify(inventory).setAvailable("A", 5, "op-A");
        verify(inventory, never()).setAvailable(eq("B"), anyLong(), anyString());
    }

    @Test void aLeftoverRowIsSetEvenToZero() {
        when(inventory.balance("B")).thenReturn(new DependencyClients.Balance("B", 7, 0));
        InitialStock.apply(inventory, List.of(line("B", 0)));
        verify(inventory).setAvailable("B", 0, "op-B");
    }

    @Test void aRollbackPutsEverySkuItSetBackToZero() {
        InitialStock.apply(inventory, List.of(line("A", 5), line("C", 2)));
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(inventory).setAvailable(eq("A"), eq(0L), startsWith("stock-undo-"));
        verify(inventory).setAvailable(eq("C"), eq(0L), startsWith("stock-undo-"));
    }

    @Test void aCommitKeepsTheStock() {
        InitialStock.apply(inventory, List.of(line("A", 5)));
        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(inventory, never()).setAvailable(eq("A"), eq(0L), anyString());
    }

    @Test void anUnknownOutcomeKeepsTheStock() {
        // A commit that may have succeeded must not lose its stock; a retry of the same SKU sets it again.
        InitialStock.apply(inventory, List.of(line("A", 5)));
        complete(TransactionSynchronization.STATUS_UNKNOWN);
        verify(inventory, never()).setAvailable(eq("A"), eq(0L), anyString());
    }

    @Test void aFailureMidwayStopsAndTheRollbackUndoesTheSkusTouchedSoFar() {
        doThrow(ApiException.unavailable("INVENTORY_UNAVAILABLE", "down")).when(inventory).setAvailable("B", 4, "op-B");
        assertThrows(ApiException.class, () -> InitialStock.apply(inventory, List.of(line("A", 5), line("B", 4), line("C", 2))));
        verify(inventory, never()).setAvailable(eq("C"), anyLong(), anyString());

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(inventory).setAvailable(eq("A"), eq(0L), startsWith("stock-undo-"));
        verify(inventory).setAvailable(eq("B"), eq(0L), startsWith("stock-undo-")); // it may have applied before failing
        verify(inventory, never()).setAvailable(eq("C"), eq(0L), anyString());
    }

    @Test void aFailedUndoDoesNotStopTheOthers() {
        InitialStock.apply(inventory, List.of(line("A", 5), line("C", 2)));
        doThrow(ApiException.unavailable("INVENTORY_UNAVAILABLE", "down"))
                .when(inventory).setAvailable(eq("A"), eq(0L), anyString());
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(inventory).setAvailable(eq("C"), eq(0L), startsWith("stock-undo-"));
    }

    private static InitialStock.Line line(String sku, long target) {
        return new InitialStock.Line(sku, target, "op-" + sku);
    }

    private static void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(status));
    }
}
