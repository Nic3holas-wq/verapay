package com.nicko.verapay.dto.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletDto(
        UUID publicId,

        // We include the currency so the user knows if they are looking at KES or another asset
        String currency,

        // BigDecimal is safer for money than Double or Float
        BigDecimal balance,

        boolean isActive,

        Instant createdAt
) {}