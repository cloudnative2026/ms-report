package com.cloudnative.ms_report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.cloudnative.ms_report.dto.OrderEventMessage;
import com.cloudnative.ms_report.dto.OrderEventMessage.Item;
import com.cloudnative.ms_report.repository.*;
import com.cloudnative.ms_report.service.OrderProjection;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportApiTests {
    @Autowired MockMvc mvc;
    @Autowired OrderProjection projection;
    @Autowired OrderFactRepository facts;
    @Autowired ProcessedEventRepository processed;
    // 2026-11-02 and 2026-11-03 in America/Santiago (UTC-3).
    static final String FROM = "2026-11-02T03:00:00Z", TO = "2026-11-04T03:00:00Z";
    int seq;

    static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"));
    }

    void event(String type, long orderId, String status, String total, String at, List<Item> items, String createdAt) {
        projection.apply(new OrderEventMessage("ev-" + (++seq), type, Instant.parse(at), orderId, 101, status,
            new BigDecimal(total), items, Instant.parse(createdAt)));
    }

    static List<Item> items(int productId, int quantity, String price) {
        return List.of(new Item(productId, quantity, new BigDecimal(price)));
    }

    @BeforeEach void setup() {
        facts.deleteAll();
        processed.deleteAll();
        // Order 1: created on day 1, edited, delivered 26h later on day 2.
        event("OrderCreated", 1, "CREADO", "20.00", "2026-11-02T12:00:00Z", items(1, 2, "10.00"), "2026-11-02T12:00:00Z");
        event("OrderUpdated", 1, "CREADO", "30.00", "2026-11-02T12:30:00Z", items(1, 3, "10.00"), "2026-11-02T12:00:00Z");
        event("OrderAccepted", 1, "ACEPTADO", "30.00", "2026-11-02T13:00:00Z", items(1, 3, "10.00"), "2026-11-02T12:00:00Z");
        event("OrderDelivered", 1, "ENTREGADO", "30.00", "2026-11-03T14:00:00Z", items(1, 3, "10.00"), "2026-11-02T12:00:00Z");
        // Order 2: day 2, still open.
        event("OrderCreated", 2, "CREADO", "15.00", "2026-11-03T15:00:00Z",
            List.of(new Item(1, 1, new BigDecimal("10.00")), new Item(2, 1, new BigDecimal("5.00"))), "2026-11-03T15:00:00Z");
        // Order 3: cancelled, not a sale.
        event("OrderCreated", 3, "CREADO", "99.00", "2026-11-03T16:00:00Z", items(3, 9, "11.00"), "2026-11-03T16:00:00Z");
        event("OrderCancelled", 3, "CANCELADO", "99.00", "2026-11-03T17:00:00Z", items(3, 9, "11.00"), "2026-11-03T16:00:00Z");
        // Order 4: deleted.
        event("OrderCreated", 4, "CREADO", "50.00", "2026-11-03T18:00:00Z", items(4, 5, "10.00"), "2026-11-03T18:00:00Z");
        projection.apply(new OrderEventMessage("ev-del", "OrderDeleted", Instant.parse("2026-11-03T19:00:00Z"), 4L,
            null, null, null, null, null));
    }

    @Test void projectionIsIdempotentAndFollowsTheLatestSnapshot() {
        event("OrderCreated", 9, "CREADO", "1.00", "2026-11-03T20:00:00Z", items(1, 1, "1.00"), "2026-11-03T20:00:00Z");
        seq--; // same event id again
        event("OrderCreated", 9, "CREADO", "1.00", "2026-11-03T20:00:00Z", items(1, 1, "1.00"), "2026-11-03T20:00:00Z");
        assertThat(facts.findAll()).extracting(f -> f.getOrderId()).containsExactlyInAnyOrder(1L, 2L, 3L, 9L);
        var first = facts.findById(1L).orElseThrow();
        assertThat(first.getStatus()).isEqualTo("ENTREGADO");
        assertThat(first.getDeliveredAt()).isEqualTo(Instant.parse("2026-11-03T14:00:00Z"));
        assertThatThrownBy(() -> projection.apply(new OrderEventMessage("x", "OrderCreated", Instant.now(), 5L,
            null, null, null, null, null))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void kpisAndDailySeries() throws Exception {
        mvc.perform(get("/api/v1/report/kpis").param("from", FROM).param("to", TO).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orders").value(2))
            .andExpect(jsonPath("$.deliveredOrders").value(1))
            .andExpect(jsonPath("$.cancelledOrders").value(1))
            .andExpect(jsonPath("$.revenue").value(45.00))
            .andExpect(jsonPath("$.averageTicket").value(22.50))
            .andExpect(jsonPath("$.averageLeadTimeHours").value(26.0));
        mvc.perform(get("/api/v1/report/sales").param("from", FROM).param("to", TO).with(admin()))
            .andExpect(jsonPath("$[*].date", contains("2026-11-02", "2026-11-03")))
            .andExpect(jsonPath("$[0].orders").value(1)).andExpect(jsonPath("$[0].revenue").value(30.00))
            .andExpect(jsonPath("$[1].orders").value(1)).andExpect(jsonPath("$[1].revenue").value(15.00));
        mvc.perform(get("/api/v1/report/lead-time").param("from", FROM).param("to", TO).with(admin()))
            .andExpect(jsonPath("$[0].deliveredOrders").value(0)).andExpect(jsonPath("$[0].averageHours").doesNotExist())
            .andExpect(jsonPath("$[1].deliveredOrders").value(1)).andExpect(jsonPath("$[1].averageHours").value(26.0));
        mvc.perform(get("/api/v1/report/top-products").param("from", FROM).param("to", TO).with(admin()))
            .andExpect(jsonPath("$[*].productId", contains(1, 2)))
            .andExpect(jsonPath("$[0].quantity").value(4)).andExpect(jsonPath("$[0].revenue").value(40.00));
        mvc.perform(get("/api/v1/report/top-products").param("from", FROM).param("to", TO).param("limit", "1").with(admin()))
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test void invalidRangesAndRoles() throws Exception {
        mvc.perform(get("/api/v1/report/kpis").param("from", TO).param("to", FROM).with(admin())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/report/sales").param("from", "2024-01-01T00:00:00Z").param("to", TO).with(admin()))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/report/top-products").param("limit", "0").with(admin())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/report/kpis")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/report/kpis").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador"))))
            .andExpect(status().isForbidden());
    }
}
