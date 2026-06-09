package com.nicko.verapay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record WithdrawRequestDto(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.00", message = "Minimum withdrawal is KES 1.00")
        BigDecimal amount,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^(0|254|\\+254)\\d{9}$",
                message = "Invalid Kenyan phone number")
        String phoneNumber,  // ← add this

        String description,

        @NotNull(message = "Idempotency key is required")
        String idempotencyKey,

        @NotBlank(message = "Step-up token is required")
        String stepUpToken
) {}