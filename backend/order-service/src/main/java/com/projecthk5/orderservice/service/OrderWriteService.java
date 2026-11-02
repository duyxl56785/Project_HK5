package com.projecthk5.orderservice.service;

import com.projecthk5.orderservice.entity.*;
import com.projecthk5.orderservice.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class OrderWriteService {
    private final OrderRepository orders;
    private final CheckoutAttemptRepository attempts;
    private final OutboxEventRepository outbox;
    private final CheckoutDraftJournal drafts;
    public OrderWriteService(OrderRepository orders,CheckoutAttemptRepository attempts,OutboxEventRepository outbox,
            CheckoutDraftJournal drafts){this.orders=orders;this.attempts=attempts;this.outbox=outbox;this.drafts=drafts;}
    /** Independent commit before Inventory; deliberately does not create a successful Order or outbox row. */
    public void prepareDraft(com.projecthk5.orderservice.domain.CheckoutDraft draft){drafts.prepare(draft);}
    @Transactional
    public Order savePlaced(Order order,java.util.UUID attemptId,String outboxPayload){
        Order saved=orders.saveAndFlush(order);
        outbox.save(new OutboxEvent("order.placed.v1",saved.getId(),outboxPayload));
        CheckoutAttempt a=attempts.findById(attemptId).orElseThrow();
        if(a.getState()!=CheckoutAttempt.State.PROCESSING) throw new IllegalStateException("Checkout attempt changed");
        a.succeed(saved.getId());attempts.saveAndFlush(a);
        return saved;
    }
}
