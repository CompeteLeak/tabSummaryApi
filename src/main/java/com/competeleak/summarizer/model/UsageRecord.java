package com.competeleak.summarizer.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "usage_records")
@Getter
@Setter
@NoArgsConstructor
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Null for anonymous free-tier requests (tracked by IP)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // For anonymous users — hashed IP or device fingerprint
    private String anonymousIdentifier;

    @Column(nullable = false)
    private Instant requestedAt = Instant.now();

    // Billing month key e.g. "2026-04" — simplifies monthly count queries
    @Column(nullable = false)
    private String billingMonth;
}
