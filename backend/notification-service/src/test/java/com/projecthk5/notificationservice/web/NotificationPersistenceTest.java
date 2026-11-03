package com.projecthk5.notificationservice.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class NotificationPersistenceTest {
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationController controller = new NotificationController(repository, "test-only");

    private NotificationController.Create notice(String key) {
        return new NotificationController.Create("customer-a", "ORDER_ACCEPTED", "title", "message", null, key);
    }

    @Test void unrelatedConstraintFailureMustReachRetryHandler() {
        var failure = new DataIntegrityViolationException("storage constraint failed");
        when(repository.existsByEventKey("event-a")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> controller.store(notice("event-a"))));
    }

    @Test void missingDedupeKeyCannotProveSuccessfulDelivery() {
        var failure = new DataIntegrityViolationException("storage constraint failed");
        when(repository.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> controller.store(notice(null))));
        verify(repository, never()).existsByEventKey(any());
    }

    @Test void committedConcurrentDuplicateIsAccepted() {
        when(repository.existsByEventKey("event-a")).thenReturn(false, true);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique event key"));
        assertDoesNotThrow(() -> controller.store(notice("event-a")));
        verify(repository, times(2)).existsByEventKey("event-a");
    }

    @Test void existingEventDoesNotWriteAgain() {
        when(repository.existsByEventKey("event-a")).thenReturn(true);
        controller.store(notice("event-a"));
        verify(repository, never()).saveAndFlush(any());
    }
}
