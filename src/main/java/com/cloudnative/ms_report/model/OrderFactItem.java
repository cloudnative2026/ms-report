package com.cloudnative.ms_report.model;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Embeddable
@Data
public class OrderFactItem {
    @Column(nullable = false)
    private int productId;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;
}
