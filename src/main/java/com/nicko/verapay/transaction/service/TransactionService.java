package com.nicko.verapay.transaction.service;


import com.nicko.verapay.auth.StepUpService;
import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.dto.*;
import com.nicko.verapay.entity.*;
import com.nicko.verapay.exception.InsufficientFundsException;
import com.nicko.verapay.exception.WalletNotFoundException;
import com.nicko.verapay.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final StepUpService stepUpService;

    // DEPOSIT
    @Transactional
    public TransactionResponseDto deposit(DepositRequestDto request) {

        // 1. Get logged-in user's wallet
        String email = getLoggedInEmail();
        Wallet wallet = getWalletByEmail(email);

        // 2. Check idempotency — prevent duplicate transactions
        if (transactionRepository.findByIdempotencyKey(
                request.idempotencyKey()).isPresent()) {
            log.warn("Duplicate deposit request for key: {}",
                    request.idempotencyKey());
            throw new IllegalArgumentException("Duplicate transaction request");
        }

        // 3. Credit wallet balance
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(request.amount());
        wallet.setBalance(balanceAfter);
        walletRepository.save(wallet);

        // 4. Create transaction record
        Transaction transaction = createTransaction(
                null,
                wallet,
                request.amount(),
                ApplicationConstants.TRANSACTION_TYPE_DEPOSIT,
                ApplicationConstants.TRANSACTION_STATUS_SUCCESS,
                request.idempotencyKey(),
                request.description()
        );

        // 5. Create ledger entry
        createLedgerEntry(
                wallet,
                transaction,
                request.amount(),
                balanceAfter,
                ApplicationConstants.LEDGER_CREDIT
        );

        log.info("Confirmed deposit of {} KES to wallet {} successful",
                request.amount(), wallet.getId());

        return buildResponse(transaction, balanceAfter);
    }

    // WITHDRAW
    @Transactional
    public TransactionResponseDto withdraw(WithdrawRequestDto request) {

        // 1. Validate step-up token
        stepUpService.validateStepUpToken(request.stepUpToken());

        // 2. Get logged-in user's wallet
        String email = getLoggedInEmail();
        Wallet wallet = getWalletByEmail(email);

        // 3. Check idempotency
        if (transactionRepository.findByIdempotencyKey(
                request.idempotencyKey()).isPresent()) {
            throw new IllegalArgumentException("Duplicate transaction request");
        }

        // 4. Check sufficient balance
        if (wallet.getBalance().compareTo(request.amount()) < 0) {
            log.warn("Insufficient funds for wallet: {}", wallet.getId());
            throw new InsufficientFundsException("Insufficient funds. " +
                    "Available balance: " + wallet.getBalance() + " KES");
        }

        // 5. Debit wallet balance
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(request.amount());
        wallet.setBalance(balanceAfter);
        walletRepository.save(wallet);

        // 6. Create transaction record
        Transaction transaction = createTransaction(
                wallet,
                null,
                request.amount(),
                ApplicationConstants.TRANSACTION_TYPE_WITHDRAW,
                ApplicationConstants.TRANSACTION_STATUS_SUCCESS,
                request.idempotencyKey(),
                request.description()
        );

        // 7. Create ledger entry
        createLedgerEntry(
                wallet,
                transaction,
                request.amount(),
                balanceAfter,
                ApplicationConstants.LEDGER_DEBIT
        );

        log.info("Confirmed withdrawal of {} KES from wallet {} successful",
                request.amount(), wallet.getId());

        return buildResponse(transaction, balanceAfter);
    }

    // TRANSFER
    @Transactional
    public TransactionResponseDto transfer(TransferRequestDto request) {

        // 1. Validate step-up token
        stepUpService.validateStepUpToken(request.stepUpToken());

        // 2. Get sender wallet
        String senderEmail = getLoggedInEmail();
        Wallet senderWallet = getWalletByEmail(senderEmail);

        // 3. Get recipient wallet
        Wallet recipientWallet = walletRepository
                .findByOwnerEmail(request.recipientEmail())
                .orElseThrow(() -> new WalletNotFoundException(
                        "Recipient wallet not found for: "
                                + request.recipientEmail()));

        // 4. Prevent self transfer
        if (senderWallet.getId().equals(recipientWallet.getId())) {
            throw new IllegalArgumentException(
                    "Cannot transfer to your own wallet");
        }

        // 5. Check idempotency
        if (transactionRepository.findByIdempotencyKey(
                request.idempotencyKey()).isPresent()) {
            throw new IllegalArgumentException("Duplicate transaction request");
        }

        // 6. Check sufficient balance
        if (senderWallet.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException("Insufficient funds. " +
                    "Available balance: " + senderWallet.getBalance() + " KES");
        }

        // 7. Debit sender
        BigDecimal senderBalanceAfter = senderWallet.getBalance()
                .subtract(request.amount());
        senderWallet.setBalance(senderBalanceAfter);
        walletRepository.save(senderWallet);

        // 8. Credit recipient
        BigDecimal recipientBalanceAfter = recipientWallet.getBalance()
                .add(request.amount());
        recipientWallet.setBalance(recipientBalanceAfter);
        walletRepository.save(recipientWallet);

        // 9. Create transaction record
        Transaction transaction = createTransaction(
                senderWallet,
                recipientWallet,
                request.amount(),
                ApplicationConstants.TRANSACTION_TYPE_TRANSFER,
                ApplicationConstants.TRANSACTION_STATUS_SUCCESS,
                request.idempotencyKey(),
                request.description()
        );

        // 10. Create ledger entries for both wallets
        createLedgerEntry(senderWallet, transaction, request.amount(),
                senderBalanceAfter, ApplicationConstants.LEDGER_DEBIT);

        createLedgerEntry(recipientWallet, transaction, request.amount(),
                recipientBalanceAfter, ApplicationConstants.LEDGER_CREDIT);

        log.info("Confirmed Transfer of {} KES from {} to {} successful",
                request.amount(), senderEmail, request.recipientEmail());

        return buildResponse(transaction, senderBalanceAfter);
    }

    // Helper — create transaction
    private Transaction createTransaction(
            Wallet fromWallet, Wallet toWallet,
            BigDecimal amount, String type,
            String status, String idempotencyKey,
            String description) {

        Transaction transaction = new Transaction();
        transaction.setFromWallet(fromWallet);
        transaction.setToWallet(toWallet != null ? toWallet :
                fromWallet); // withdraw uses same wallet
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setTransactionRef("TXN-" +
                UUID.randomUUID().toString().toUpperCase()
                        .replace("-", "").substring(0, 12));
        transaction.setDescription(description);
        transaction.setCreatedAt(Instant.now());
        return transactionRepository.save(transaction);
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

    // Helper — get wallet by email
    private Wallet getWalletByEmail(String email) {
        return walletRepository.findByOwnerEmail(email)
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for user: " + email));
    }

    // Helper — get logged-in user email
    private String getLoggedInEmail() {
        return SecurityContextHolder.getContext()
                .getAuthentication().getName();
    }

    // Helper — build response
    private TransactionResponseDto buildResponse(
            Transaction transaction, BigDecimal balanceAfter) {
        return new TransactionResponseDto(
                transaction.getId(),
                transaction.getTransactionRef(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getAmount(),
                balanceAfter,
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }
}
