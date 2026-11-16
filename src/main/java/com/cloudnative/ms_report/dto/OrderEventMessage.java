package com.cloudnative.ms_report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Record published by ms-orders to orders.events (snapshot of the order after the change).
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderEventMessage(String eventId, String type, Instant occurredAt, Long orderId, Integer customerId,
        String status, BigDecimal totalAmount, List<Item> items, Instant createdAt) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Integer productId, Integer quantity, BigDecimal unitPrice) {}
}
