package com.cloudnative.ms_report.repository;

import java.time.Instant;
import java.util.List;
import com.cloudnative.ms_report.model.OrderFact;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderFactRepository extends JpaRepository<OrderFact, Long> {
    @EntityGraph(attributePaths = "items")
    List<OrderFact> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    List<OrderFact> findByDeliveredAtGreaterThanEqualAndDeliveredAtLessThan(Instant from, Instant to);
}
