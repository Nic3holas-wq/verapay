package com.nicko.verapay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DepositRequestDto(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.00", message = "Minimum deposit is KES 1.00")
        BigDecimal amount,

        String description,

        @NotNull(message = "Idempotency key is required")
        String idempotencyKey
) {}