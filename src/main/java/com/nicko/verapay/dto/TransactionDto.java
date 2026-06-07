package com.nicko.verapay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransactionDto(
        @NotNull(message = "Source wallet ID is required")
        Long fromWalletId,

        @NotNull(message = "Destination wallet ID is required")
        Long toWalletId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Idempotency key is required")
        @Size(min = 36, max = 100, message = "Invalid idempotency key format")
        String idempotencyKey,

        @Size(max = 255)
        String description
) {}