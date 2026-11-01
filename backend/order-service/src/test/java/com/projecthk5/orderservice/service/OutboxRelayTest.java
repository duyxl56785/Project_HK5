package com.projecthk5.orderservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.entity.OutboxEvent;
import com.projecthk5.orderservice.ports.InventoryPort;
import com.projecthk5.orderservice.ports.StoreDirectory;
import com.projecthk5.orderservice.repository.OrderRepository;
import com.projecthk5.orderservice.repository.OutboxEventRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRelayTest {
    private final ObjectMapper json = new ObjectMapper();
    private final InventoryPort inventory = mock(InventoryPort.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final StoreDirectory stores = mock(StoreDirectory.class);
    private final OutboxRelay relay = new OutboxRelay(mock(OutboxEventRepository.class), mock(OrderRepository.class),
            inventory, kafka, stores, json, mock(TransactionTemplate.class));

    private OutboxEvent event(String topic, Object payload) throws Exception {
        return new OutboxEvent(topic, UUID.randomUUID(), json.writeValueAsString(payload));
    }

    private OrderEvents.Notice notice() {
        return new OrderEvents.Notice(null, "store-1", "ORDER_CANCELLED", "t", "m", "/seller/orders", "k1",
                "so-1", "order-1", "customer-1", "PLACED", "CANCELLED", "CUSTOMER", null);
    }

    private void storeExists() {
        when(stores.facts(Set.of("store-1"))).thenReturn(List.of(
                new StoreDirectory.Store("store-1", "seller-1", "owner-account", "Shop", "shop", true, true, false)));
    }

    private OutboxEvent releaseEvent() throws Exception {
        return event(OrderEvents.INVENTORY, new OrderEvents.InventoryOperation("seller-order-x-release", "RELEASE",
                List.of(new OrderEvents.Line("p1", 2, "SKU-1"))));
    }

    @Test
    void inventoryOperationIsSentWithStableIdAndMarkedPublished() throws Exception {
        OutboxEvent e = releaseEvent();
        relay.deliver(e);
        verify(inventory).apply("seller-order-x-release", "RELEASE", List.of(new InventoryPort.Reservation("p1", 2, "SKU-1")));
        assertNotNull(e.getPublishedAt());
        assertFalse(e.isDead());
    }

    @Test
    void transientFailureStaysQueuedForRetry() throws Exception {
        OutboxEvent e = releaseEvent();
        doThrow(new IllegalStateException("timeout")).when(inventory).apply(anyString(), anyString(), anyList());
        relay.deliver(e);
        assertNull(e.getPublishedAt());
        assertFalse(e.isDead());
        assertEquals(1, e.getAttempts());
    }

    @Test
    void definitiveInventoryRejectionIsParkedForOperators() throws Exception {
        OutboxEvent e = releaseEvent();
        doThrow(new InventoryPort.Rejected("INSUFFICIENT_RESERVATION", "gone")).when(inventory).apply(anyString(), anyString(), anyList());
        relay.deliver(e);
        assertTrue(e.isDead());
        assertTrue(e.getLastError().startsWith("INSUFFICIENT_RESERVATION"));
    }

    @Test
    void retriesStopAfterTheLimit() throws Exception {
        OutboxEvent e = releaseEvent();
        doThrow(new IllegalStateException("down")).when(inventory).apply(anyString(), anyString(), anyList());
        for (int i = 0; i < OutboxEvent.MAX_ATTEMPTS; i++) relay.deliver(e);
        assertTrue(e.isDead());
    }

    @Test
    void storeNoticeIsDeliveredToTheCurrentStoreOwner() throws Exception {
        storeExists();
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        OutboxEvent e = event(OrderEvents.NOTIFY, notice());
        relay.deliver(e);
        var sent = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(OrderEvents.STATUS_CHANGED), eq("so-1"), sent.capture());
        var published = json.readTree(sent.getValue());
        assertEquals("owner-account", published.path("notification").path("recipient").asText());
        assertEquals("owner-account", published.path("sellerAccountId").asText());
        assertEquals("CANCELLED", published.path("toStatus").asText());
        assertEquals("k1", published.path("notification").path("eventKey").asText());
        assertNotNull(e.getPublishedAt());
    }

    @Test
    void brokerOutageKeepsEventsQueuedWithoutSpendingAttempts() throws Exception {
        storeExists();
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        OutboxEvent first = event(OrderEvents.NOTIFY, notice());
        OutboxEvent second = event(OrderEvents.NOTIFY, notice());
        for (int i = 0; i < OutboxEvent.MAX_ATTEMPTS + 1; i++) relay.deliver(first);
        relay.deliver(second);
        assertFalse(first.isDead());
        assertEquals(0, first.getAttempts());
        assertNull(first.getPublishedAt());
        verify(kafka, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void inventoryStillFlowsDuringBrokerOutage() throws Exception {
        storeExists();
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        relay.deliver(event(OrderEvents.NOTIFY, notice()));
        OutboxEvent e = releaseEvent();
        relay.deliver(e);
        assertNotNull(e.getPublishedAt());
    }

    @Test
    void unknownTopicIsParkedNotRetried() throws Exception {
        OutboxEvent e = event("mystery.v9", List.of());
        relay.deliver(e);
        assertTrue(e.isDead());
        verifyNoInteractions(inventory, kafka);
    }
}
