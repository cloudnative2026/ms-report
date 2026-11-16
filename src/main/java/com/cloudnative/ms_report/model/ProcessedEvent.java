package com.cloudnative.ms_report.model;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

// Event ids already applied: redelivered records are skipped.
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "processed_event")
public class ProcessedEvent {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false)
    private Instant processedAt;
}
