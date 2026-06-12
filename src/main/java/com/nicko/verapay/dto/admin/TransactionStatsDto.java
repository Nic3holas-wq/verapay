package com.nicko.verapay.dto.admin;

import java.math.BigDecimal;

public record TransactionStatsDto(
        long totalTransactions,
        long pendingCount,
        long successCount,
        long failedCount,
        BigDecimal totalDepositVolume,
        BigDecimal totalWithdrawVolume,
        BigDecimal totalTransferVolume
) {}
