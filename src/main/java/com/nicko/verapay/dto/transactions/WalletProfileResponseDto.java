package com.nicko.verapay.dto.transactions;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletProfileResponseDto(
        // User info
        String fullName,
        String email,
        String phoneNumber,
        Boolean isAccountActive,
        Instant memberSince,

        // Wallet info
        BigDecimal balance,
        String currency,
        Boolean isWalletActive
) {}