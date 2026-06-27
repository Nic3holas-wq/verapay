package com.nicko.verapay.entity.fraud;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "user_limits")
public class UserLimit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "daily_limit", precision = 19, scale = 4, nullable = false)
    private BigDecimal dailyLimit = new BigDecimal("50000.0000");

    @Column(name = "monthly_limit", precision = 19, scale = 4, nullable = false)
    private BigDecimal monthlyLimit = new BigDecimal("250000.0000");

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
