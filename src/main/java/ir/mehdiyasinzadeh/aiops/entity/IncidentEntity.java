package ir.mehdiyasinzadeh.aiops.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "idx_incidents_signature", columnList = "incident_signature"),
        @Index(name = "idx_incidents_service", columnList = "service_name"),
        @Index(name = "idx_incidents_status", columnList = "status"),
        @Index(name = "idx_incidents_last_seen", columnList = "last_seen_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_signature", nullable = false, unique = true, length = 255)
    private String incidentSignature;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "severity", nullable = false, length = 20)
    @Builder.Default
    private String severity = "CRITICAL";

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "occurrence_count", nullable = false)
    @Builder.Default
    private Long occurrenceCount = 1L;

    @Column(name = "root_cause_summary", columnDefinition = "TEXT")
    private String rootCauseSummary;

    @Column(name = "actionable_remediation", columnDefinition = "TEXT")
    private String actionableRemediation;

    @Column(name = "diagnostic_turns", nullable = false)
    @Builder.Default
    private Integer diagnosticTurns = 1;

    @Column(name = "raw_stack_trace", columnDefinition = "TEXT")
    private String rawStackTrace;
}
