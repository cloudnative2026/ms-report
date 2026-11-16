package com.cloudnative.ms_report.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.cloudnative.ms_report.dto.ReportDTOs.*;
import com.cloudnative.ms_report.service.ReportService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// Read-only: the data only enters through Kafka. Range defaults to the last 30 days.
@RestController
@RequestMapping("/api/v1/report")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('Admin')")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    record Range(Instant from, Instant to) {}

    private static Range range(Instant from, Instant to) {
        var end = to != null ? to : Instant.now();
        var start = from != null ? from : end.minus(Duration.ofDays(30));
        if (!start.isBefore(end) || Duration.between(start, end).toDays() > 366)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rango invalido: from < to y maximo 366 dias.");
        return new Range(start, end);
    }

    @GetMapping("/kpis")
    public Kpis kpis(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant to) {
        var r = range(from, to);
        return service.kpis(r.from(), r.to());
    }

    @GetMapping("/sales")
    public List<SalesPoint> sales(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant to) {
        var r = range(from, to);
        return service.salesByDay(r.from(), r.to());
    }

    @GetMapping("/lead-time")
    public List<LeadTimePoint> leadTime(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant to) {
        var r = range(from, to);
        return service.leadTimeByDay(r.from(), r.to());
    }

    @GetMapping("/top-products")
    public List<TopProduct> topProducts(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "5") int limit) {
        if (limit < 1 || limit > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit entre 1 y 50.");
        var r = range(from, to);
        return service.topProducts(r.from(), r.to(), limit);
    }
}
