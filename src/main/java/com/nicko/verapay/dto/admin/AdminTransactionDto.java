package com.nicko.verapay.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminTransactionDto(
        Long id,
        String transactionRef,
        String type,
        String status,
        BigDecimal amount,
        String fromUserEmail,
        String toUserEmail,
        String description,
        Instant createdAt
) {}
