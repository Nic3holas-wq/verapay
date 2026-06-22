package com.nicko.verapay.dto.admin;

import com.nicko.verapay.dto.transactions.TransactionHistoryDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDetailAdminDto(
        UUID publicId,
        String fullName,
        String email,
        String phoneNumber,
        String role,
        Boolean isActive,
        Instant memberSince,
        BigDecimal walletBalance,
        String walletCurrency,
        Boolean walletActive,
        List<TransactionHistoryDto> recentTransactions
) {}
