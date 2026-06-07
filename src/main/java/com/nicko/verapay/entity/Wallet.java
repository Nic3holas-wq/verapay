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
@Table(name = "wallets", indexes = {@Index(name = "wallets_owner_id_key",
        columnList = "owner_id",
        unique = true)})
public class Wallet extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @NotNull
    @ColumnDefault("0.0000")
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Size(max = 10)
    @NotNull
    @ColumnDefault("'KES'")
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @NotNull
    @Version
    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private Long version;
    
}