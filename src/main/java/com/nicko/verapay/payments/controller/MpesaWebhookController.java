package com.nicko.verapay.payments.controller;

import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.dto.mpesa.B2CResultDto;
import com.nicko.verapay.dto.mpesa.StkCallbackDto;
import com.nicko.verapay.entity.LedgerEntry;
import com.nicko.verapay.entity.Transaction;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.entity.Wallet;
import com.nicko.verapay.notifications.service.EmailService;
import com.nicko.verapay.payments.security.WebhookTokenValidator;
import com.nicko.verapay.repository.LedgerEntryRepository;
import com.nicko.verapay.repository.TransactionRepository;
import com.nicko.verapay.repository.WalletRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
@RequestMapping("/webhooks/mpesa")
@RequiredArgsConstructor
@Slf4j
public class MpesaWebhookController {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final EmailService emailService;
    private final WebhookTokenValidator webhookTokenValidator;

    // STK Push Callback — deposit result
    @PostMapping("/stk/callback")
    @Transactional
    public ResponseEntity<?> handleStkCallback(@RequestBody StkCallbackDto callback, HttpServletRequest request) {
        if (!webhookTokenValidator.isValid(request)) {
            log.warn("Invalid or missing webhook token for STK callback");
            return ResponseEntity.status(403).body("Forbidden");
        }

        var stkCallback = callback.body().stkCallback();
        String checkoutRequestID = stkCallback.checkoutRequestID();

        // 1. Find transaction by CheckoutRequestID
        Transaction transaction = transactionRepository
                .findByCheckoutRequestId(checkoutRequestID)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + checkoutRequestID));

        // 2. Prevent duplicate processing
        if (!transaction.getStatus().equals(ApplicationConstants.TRANSACTION_STATUS_PENDING)) {
            return ResponseEntity.ok("Already processed");
        }

        if (stkCallback.resultCode() == 0) {
            // SUCCESS: update the wallet
            Wallet wallet = transaction.getToWallet();
            wallet.setBalance(wallet.getBalance().add(transaction.getAmount()));
            walletRepository.save(wallet);

            transaction.setStatus(ApplicationConstants.TRANSACTION_STATUS_SUCCESS);
            transactionRepository.save(transaction);

            // Record the ledger entry here
            createLedgerEntry(wallet, transaction, transaction.getAmount(), wallet.getBalance(), ApplicationConstants.LEDGER_CREDIT);

            // Send deposit confirmation email
            User user = transaction.getToWallet().getOwner();
            emailService.sendDepositConfirmation(
                    user.getEmail(),
                    user.getFullName(),
                    transaction.getAmount(),
                    transaction.getToWallet().getBalance(),
                    transaction.getTransactionRef()
            );

        } else {
            // FAILED: Simply mark as failed.
            transaction.setStatus(ApplicationConstants.TRANSACTION_STATUS_FAILED);
            transactionRepository.save(transaction);
        }

        return ResponseEntity.ok("{\"ResultCode\":\"0\",\"ResultDesc\":\"Success\"}");
    }

    // B2C Result — withdrawal result
    @PostMapping("/b2c/result")
    @Transactional
    public ResponseEntity<?> handleB2CResult(@RequestBody B2CResultDto result, HttpServletRequest request) {

        if (!webhookTokenValidator.isValid(request)) {
            log.warn("Invalid or missing webhook token for B2C result");
            return ResponseEntity.status(403).body("Forbidden");
        }

        String transactionRef = result.result().originatorConversationID();

        Transaction transaction = transactionRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionRef));

        // 1. Skip if already processed
        if (!transaction.getStatus().equals(ApplicationConstants.TRANSACTION_STATUS_PENDING)) {
            return ResponseEntity.ok("Already processed");
        }

        if (result.result().resultCode() == 0) {
            // SUCCESS: Balance was already debited and ledger DEBIT recorded at initiation.
            transaction.setStatus(ApplicationConstants.TRANSACTION_STATUS_SUCCESS);
            transactionRepository.save(transaction);

            // Send withdrawal confirmation email
            User user = transaction.getFromWallet().getOwner();
            emailService.sendWithdrawalConfirmation(
                    user.getEmail(),
                    user.getFullName(),
                    transaction.getAmount(),
                    transaction.getFromWallet().getBalance(),
                    transaction.getTransactionRef()
            );

            log.info("Withdrawal confirmed: {}", transaction.getTransactionRef());
        } else {
            // FAILED: Revert immediate debit.
            Wallet wallet = transaction.getFromWallet();
            BigDecimal newBalance = wallet.getBalance().add(transaction.getAmount());

            wallet.setBalance(newBalance);
            walletRepository.save(wallet);

            transaction.setStatus(ApplicationConstants.TRANSACTION_STATUS_FAILED);
            transactionRepository.save(transaction);

            // Record compensating CREDIT ledger entry to refund user
            createLedgerEntry(wallet, transaction, transaction.getAmount(), newBalance, ApplicationConstants.LEDGER_CREDIT);

            // Send transaction failed email
            User user = wallet.getOwner();
            emailService.sendTransactionFailedEmail(
                    user.getEmail(),
                    user.getFullName(),
                    transaction.getType(),
                    transaction.getAmount(),
                    transaction.getTransactionRef(),
                    result.result().resultDesc()
            );

            log.warn("Withdrawal failed: {} reason: {}", transaction.getTransactionRef(), result.result().resultDesc());
        }

        return ResponseEntity.ok("{\"ResultCode\":\"0\",\"ResultDesc\":\"Success\"}");
    }

    // B2C Timeout — handle timeout
    @PostMapping("/b2c/timeout")
    @Transactional
    public ResponseEntity<?> handleB2CTimeout(
            @RequestBody B2CResultDto result, HttpServletRequest request) {

        if (!webhookTokenValidator.isValid(request)) {
            log.warn("Invalid or missing webhook token for B2C timeout");
            return ResponseEntity.status(403).body("Forbidden");
        }

        log.warn("B2C Timeout received: {}",
                result.result().originatorConversationID());

        String transactionRef = result.result().originatorConversationID();

        Transaction transaction = transactionRepository
                .findByTransactionRef(transactionRef)
                .orElseThrow(() -> new RuntimeException(
                        "Transaction not found: " + transactionRef));

        // Skip if already processed
        if (!transaction.getStatus().equals(ApplicationConstants.TRANSACTION_STATUS_PENDING)) {
            return ResponseEntity.ok("Already processed");
        }

        // Reverse wallet deduction on timeout
        transaction.setStatus(ApplicationConstants.TRANSACTION_STATUS_FAILED);
        transactionRepository.save(transaction);

        Wallet wallet = transaction.getFromWallet();
        BigDecimal newBalance = wallet.getBalance().add(transaction.getAmount());
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        // Record compensating CREDIT ledger entry
        createLedgerEntry(wallet, transaction, transaction.getAmount(), newBalance, ApplicationConstants.LEDGER_CREDIT);

        // Send transaction failed email
        User user = wallet.getOwner();
        emailService.sendTransactionFailedEmail(
                user.getEmail(),
                user.getFullName(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getTransactionRef(),
                "Transaction timed out"
        );

        return ResponseEntity.ok(
                "{\"ResultCode\":\"0\",\"ResultDesc\":\"Success\"}");
    }

    // Helper — create ledger entry
    private void createLedgerEntry(
            Wallet wallet, Transaction transaction,
            BigDecimal amount, BigDecimal balanceAfter,
            String entryType) {

        LedgerEntry entry = new LedgerEntry();
        entry.setWallet(wallet);
        entry.setTransaction(transaction);
        entry.setAmount(amount);
        entry.setBalanceAfter(balanceAfter);
        entry.setEntryType(entryType);
        entry.setCreatedAt(Instant.now());
        ledgerEntryRepository.save(entry);
    }
}
