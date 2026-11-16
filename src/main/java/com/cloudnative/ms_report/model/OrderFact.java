package com.cloudnative.ms_report.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

// Read model of one order, rebuilt from orders.events.
@Entity
@Data
@NoArgsConstructor
@Table(name = "order_fact")
public class OrderFact {
    @Id
    private Long orderId;
    @Column(nullable = false)
    private int customerId;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant deliveredAt;
    private Instant cancelledAt;
    @Column(nullable = false)
    private Instant lastEventAt;
    @ElementCollection
    @CollectionTable(name = "order_fact_item", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderFactItem> items = new ArrayList<>();
}
