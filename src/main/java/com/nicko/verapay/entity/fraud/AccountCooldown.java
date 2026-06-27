package com.nicko.verapay.entity.fraud;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "account_cooldowns")
public class AccountCooldown {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "modification_type", nullable = false)
    private String modificationType;

    @Column(name = "cooldown_expires_at", nullable = false)
    private LocalDateTime cooldownExpiresAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters, Setters, and Constructors
}
