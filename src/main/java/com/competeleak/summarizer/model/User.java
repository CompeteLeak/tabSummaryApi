package com.competeleak.summarizer.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String apiKey;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tier tier = Tier.FREE;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // Lemon Squeezy subscription/order reference for lookup on webhook events
    private String lemonSqueezySubscriptionId;

    public enum Tier {
        FREE, PAID
    }
}
