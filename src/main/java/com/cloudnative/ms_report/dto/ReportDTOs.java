package com.cloudnative.ms_report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ReportDTOs {
    private ReportDTOs() {}

    public record Kpis(long orders, long deliveredOrders, long cancelledOrders, BigDecimal revenue,
            BigDecimal averageTicket, Double averageLeadTimeHours) {}

    public record SalesPoint(LocalDate date, long orders, BigDecimal revenue) {}

    public record LeadTimePoint(LocalDate date, long deliveredOrders, Double averageHours) {}

    public record TopProduct(int productId, long quantity, BigDecimal revenue) {}
}
