package com.nicko.verapay.dto.transactions;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionHistoryDto(
        Long transactionId,
        String transactionRef,
        String type,
        String status,
        BigDecimal amount,
        String description,
        Instant createdAt,
        String direction  // "DEBIT" or "CREDIT" relative to this wallet
) {}