package com.cloudnative.ms_report.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.cloudnative.ms_report.dto.ReportDTOs.*;
import com.cloudnative.ms_report.model.OrderFact;
import com.cloudnative.ms_report.repository.OrderFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Sales and top products count orders created in [from, to); lead time counts orders delivered in it.
// Cancelled orders are not sales. Daily buckets use the business time zone.
@Service
@Transactional(readOnly = true)
public class ReportService {
    static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private final OrderFactRepository facts;

    public ReportService(OrderFactRepository facts) {
        this.facts = facts;
    }

    private List<OrderFact> sales(Instant from, Instant to) {
        return facts.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to).stream()
                .filter(f -> !"CANCELADO".equals(f.getStatus())).toList();
    }

    public Kpis kpis(Instant from, Instant to) {
        var created = facts.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);
        var active = created.stream().filter(f -> !"CANCELADO".equals(f.getStatus())).toList();
        var revenue = sum(active);
        var delivered = facts.findByDeliveredAtGreaterThanEqualAndDeliveredAtLessThan(from, to);
        return new Kpis(active.size(), delivered.size(), created.size() - active.size(), revenue,
                active.isEmpty() ? BigDecimal.ZERO.setScale(2) : revenue.divide(BigDecimal.valueOf(active.size()), 2, RoundingMode.HALF_UP),
                averageHours(delivered));
    }

    public List<SalesPoint> salesByDay(Instant from, Instant to) {
        var byDay = sales(from, to).stream().collect(Collectors.groupingBy(f -> day(f.getCreatedAt())));
        return days(from, to).stream().map(d -> {
            var orders = byDay.getOrDefault(d, List.of());
            return new SalesPoint(d, orders.size(), sum(orders));
        }).toList();
    }

    public List<LeadTimePoint> leadTimeByDay(Instant from, Instant to) {
        var byDay = facts.findByDeliveredAtGreaterThanEqualAndDeliveredAtLessThan(from, to).stream()
                .collect(Collectors.groupingBy(f -> day(f.getDeliveredAt())));
        return days(from, to).stream().map(d -> {
            var orders = byDay.getOrDefault(d, List.of());
            return new LeadTimePoint(d, orders.size(), averageHours(orders));
        }).toList();
    }

    public List<TopProduct> topProducts(Instant from, Instant to, int limit) {
        record Totals(long quantity, BigDecimal revenue) {}
        var totals = new HashMap<Integer, Totals>();
        for (var order : sales(from, to)) for (var item : order.getItems()) {
            var line = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            totals.merge(item.getProductId(), new Totals(item.getQuantity(), line),
                    (a, b) -> new Totals(a.quantity() + b.quantity(), a.revenue().add(b.revenue())));
        }
        return totals.entrySet().stream()
                .map(e -> new TopProduct(e.getKey(), e.getValue().quantity(), e.getValue().revenue()))
                .sorted(Comparator.comparingLong(TopProduct::quantity).reversed()
                        .thenComparing(TopProduct::revenue, Comparator.reverseOrder())
                        .thenComparingInt(TopProduct::productId))
                .limit(limit).toList();
    }

    private static BigDecimal sum(List<OrderFact> orders) {
        return orders.stream().map(OrderFact::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }

    private static Double averageHours(List<OrderFact> delivered) {
        if (delivered.isEmpty()) return null;
        double minutes = delivered.stream()
                .mapToLong(f -> Duration.between(f.getCreatedAt(), f.getDeliveredAt()).toMinutes()).average().orElseThrow();
        return Math.round(minutes / 6.0) / 10.0;
    }

    private static LocalDate day(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    private static List<LocalDate> days(Instant from, Instant to) {
        return day(from).datesUntil(day(to.minusNanos(1)).plusDays(1)).toList();
    }
}
