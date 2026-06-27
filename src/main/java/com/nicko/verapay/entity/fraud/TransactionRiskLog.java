package com.nicko.verapay.entity.fraud;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "transaction_risk_logs")
public class TransactionRiskLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "rules_triggered", columnDefinition = "text[]")
    private String[] rulesTriggerged;

    @Column(name = "ml_risk_score", precision = 3, scale = 2, nullable = false)
    private BigDecimal mlRiskScore;

    @Column(name = "action_taken", nullable = false)
    private String actionTaken;

    @Column(name = "is_false_positive")
    private Boolean isFalsePositive = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters, Setters, and Constructors
}
