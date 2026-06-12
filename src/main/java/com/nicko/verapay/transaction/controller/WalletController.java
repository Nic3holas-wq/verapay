package com.nicko.verapay.transaction.controller;

import com.nicko.verapay.dto.transactions.TransactionHistoryDto;
import com.nicko.verapay.dto.transactions.WalletProfileResponseDto;
import com.nicko.verapay.transaction.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    // GET /api/wallet/profile — balance + account info
    @GetMapping(value = "/profile", version = "1.0")
    public ResponseEntity<WalletProfileResponseDto> getProfile() {
        return ResponseEntity.ok(walletService.getProfile());
    }

    // GET /api/wallet/transactions?limit=10
    @GetMapping(value = "/transactions", version = "1.0")
    public ResponseEntity<List<TransactionHistoryDto>> getRecentTransactions(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(walletService.getRecentTransactions(limit));
    }
}