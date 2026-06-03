package com.nicko.verapay.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "transactions_idempotency_key_key",
                columnList = "idempotency_key",
                unique = true),
        @Index(name = "transactions_transaction_ref_key",
                columnList = "transaction_ref",
                unique = true),
        @Index(name = "idx_tx_from_wallet",
                columnList = "from_wallet_id"),
        @Index(name = "idx_tx_to_wallet",
                columnList = "to_wallet_id"),
        @Index(name = "idx_tx_created",
                columnList = "created_at")})
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_wallet_id")
    private Wallet fromWallet;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_wallet_id", nullable = false)
    private Wallet toWallet;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Size(max = 20)
    @NotNull
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Size(max = 20)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Size(max = 100)
    @NotNull
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Size(max = 100)
    @NotNull
    @Column(name = "transaction_ref", nullable = false, length = 100)
    private String transactionRef;

    @Size(max = 255)
    @Column(name = "description")
    private String description;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;


}