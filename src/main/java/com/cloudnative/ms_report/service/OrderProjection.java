package com.cloudnative.ms_report.service;

import java.time.Instant;
import com.cloudnative.ms_report.dto.OrderEventMessage;
import com.cloudnative.ms_report.model.*;
import com.cloudnative.ms_report.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderProjection {
    private final OrderFactRepository facts;
    private final ProcessedEventRepository processed;

    public OrderProjection(OrderFactRepository facts, ProcessedEventRepository processed) {
        this.facts = facts;
        this.processed = processed;
    }

    // Events of one order share a partition key, so they arrive in order.
    @Transactional
    public void apply(OrderEventMessage event) {
        if (event.eventId() == null || event.type() == null || event.occurredAt() == null || event.orderId() == null)
            throw new IllegalArgumentException("Evento incompleto: " + event);
        if (processed.existsById(event.eventId())) return;
        processed.save(new ProcessedEvent(event.eventId(), Instant.now()));
        if ("OrderDeleted".equals(event.type())) {
            facts.deleteById(event.orderId());
            return;
        }
        if (event.customerId() == null || event.status() == null || event.totalAmount() == null
                || event.createdAt() == null || event.items() == null)
            throw new IllegalArgumentException("Evento sin datos del pedido: " + event);
        var fact = facts.findById(event.orderId()).orElseGet(() -> {
            var created = new OrderFact();
            created.setOrderId(event.orderId());
            return created;
        });
        fact.setCustomerId(event.customerId());
        fact.setStatus(event.status());
        fact.setTotalAmount(event.totalAmount());
        fact.setCreatedAt(event.createdAt());
        fact.getItems().clear();
        for (var item : event.items())
            fact.getItems().add(new OrderFactItem(item.productId(), item.quantity(), item.unitPrice()));
        if ("OrderDelivered".equals(event.type())) fact.setDeliveredAt(event.occurredAt());
        if ("OrderCancelled".equals(event.type())) fact.setCancelledAt(event.occurredAt());
        fact.setLastEventAt(event.occurredAt());
        facts.save(fact);
    }
}
