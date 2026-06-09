package com.nicko.verapay.transaction.controller;

import com.nicko.verapay.dto.*;
import com.nicko.verapay.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    // Deposit — no step-up required
    @PostMapping(value = "/deposit", version = "1.0")
    public ResponseEntity<TransactionResponseDto> deposit(
            @RequestBody @Valid DepositRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.deposit(request));
    }

    // Withdraw — step-up required
    @PostMapping(value = "/withdraw", version = "1.0")
    public ResponseEntity<TransactionResponseDto> withdraw(
            @RequestBody @Valid WithdrawRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.withdraw(request));
    }

    // Transfer — step-up required
    @PostMapping(value = "/transfer", version = "1.0")
    public ResponseEntity<TransactionResponseDto> transfer(
            @RequestBody @Valid TransferRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.transfer(request));
    }
}
