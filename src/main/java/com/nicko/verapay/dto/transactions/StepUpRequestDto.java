package com.nicko.verapay.dto.transactions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StepUpRequestDto(
        @NotBlank(message = "Transaction PIN is required")
        @Pattern(regexp = "^\\d{4}$", message = "Transaction PIN must be exactly 4 digits")
        String transactionPin
) {
        @Override
        public String toString() {
                return "StepUpRequestDto[transactionPin=****]";
        }
}