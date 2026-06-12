package com.nicko.verapay.dto.transactions;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryDto(
        Long id,
        Long walletId,
        Long transactionId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String entryType, // "DEBIT" or "CREDIT"
        Instant createdAt
) {}