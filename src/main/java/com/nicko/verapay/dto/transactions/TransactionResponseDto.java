// TransactionResponseDto.java
package com.nicko.verapay.dto.transactions;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponseDto(
        Long transactionId,
        String transactionRef,
        String transactionCode,
        String type,
        String status,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        Instant createdAt
) {}