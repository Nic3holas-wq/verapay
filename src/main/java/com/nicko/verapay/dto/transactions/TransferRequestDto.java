package com.nicko.verapay.dto.transactions;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TransferRequestDto(
        @NotBlank(message = "Recipient email is required")
        @Email(message = "Invalid recipient email")
        String recipientEmail,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.00", message = "Minimum transfer is KES 1.00")
        BigDecimal amount,

        String description,

        @NotNull(message = "Idempotency key is required")
        String idempotencyKey,

        @NotBlank(message = "Step-up token is required")
        String stepUpToken
) {}