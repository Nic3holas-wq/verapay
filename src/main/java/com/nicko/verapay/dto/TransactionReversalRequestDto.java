package com.nicko.verapay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TransactionReversalRequestDto(
        @NotBlank(message = "Reason is required")
        @Size(min = 5, max = 255, message = "Reason must be between 5 and 255 characters")
        String reason
) {}
