package com.nicko.verapay.transaction.service;


import com.nicko.verapay.auth.StepUpService;
import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.dto.mpesa.StkPushResponseDto;
import com.nicko.verapay.dto.transactions.*;
import com.nicko.verapay.entity.*;
import com.nicko.verapay.exception.InsufficientFundsException;
import com.nicko.verapay.exception.WalletNotFoundException;
import com.nicko.verapay.exception.FraudDetectionException;
import com.nicko.verapay.notifications.service.EmailService;
import com.nicko.verapay.payments.service.MpesaB2CService;
import com.nicko.verapay.payments.service.MpesaC2BService;
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
    private final MpesaC2BService mpesaC2BService;
    private final MpesaB2CService mpesaB2CService;
    private final EmailService emailService;
    private final TransactionCodeGenerator transactionCodeGenerator;
    private final FraudPreventionService fraudPreventionService;

    // DEPOSIT
    @Transactional
    public TransactionResponseDto deposit(DepositRequestDto request) {
        String email = getLoggedInEmail();
        Wallet wallet = getWalletByEmail(email);
        User user = wallet.getOwner();

        FraudPreventionService.FraudCheckResult fraudResult = fraudPreventionService.evaluate(user, request.amount(), "DEPOSIT");
        if (fraudResult.block) {
            fraudPreventionService.logBlockedTransaction(user, request.amount(), "DEPOSIT", fraudResult);
            throw new FraudDetectionException("Transaction rejected due to fraud checks: " + fraudResult.rulesTriggered);
        }

        if (transactionRepository.findByIdempotencyKey(request.idempotencyKey()).isPresent()) {
            throw new IllegalArgumentException("Duplicate transaction request");
        }

        // 1. Create PENDING transaction record (Store checkoutRequestId here!)
        StkPushResponseDto stkResponse = mpesaC2BService.initiateStkPush(
                request.phoneNumber(), request.amount(), request.idempotencyKey());

        Transaction transaction = createTransaction(
                null, wallet, request.amount(),
                ApplicationConstants.TRANSACTION_TYPE_DEPOSIT,
                ApplicationConstants.TRANSACTION_STATUS_PENDING,
                request.idempotencyKey(), request.description(),
                fraudResult
        );

        fraudPreventionService.logRiskLog(transaction, fraudResult);

        // Store the checkoutRequestId so the Webhook can find this transaction later
        transaction.setCheckoutRequestId(stkResponse.checkoutRequestID());
        transactionRepository.save(transaction);

        // 2. DO NOT update balance or ledger here.
        // This happens ONLY in MpesaWebhookController upon success.

        return buildResponse(transaction, wallet.getBalance());
    }

    // WITHDRAW
    @Transactional
    public TransactionResponseDto withdraw(WithdrawRequestDto request) {
        stepUpService.validateStepUpToken(request.stepUpToken());
        String email = getLoggedInEmail();
        Wallet wallet = getWalletByEmail(email);
        User user = wallet.getOwner();

        FraudPreventionService.FraudCheckResult fraudResult = fraudPreventionService.evaluate(user, request.amount(), "WITHDRAW");
        if (fraudResult.block) {
            fraudPreventionService.logBlockedTransaction(user, request.amount(), "WITHDRAW", fraudResult);
            throw new FraudDetectionException("Transaction rejected due to fraud checks: " + fraudResult.rulesTriggered);
        }

        if (wallet.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }

        // 1. Create PENDING transaction record
        Transaction transaction = createTransaction(
                wallet, null, request.amount(),
                ApplicationConstants.TRANSACTION_TYPE_WITHDRAW,
                ApplicationConstants.TRANSACTION_STATUS_PENDING,
                request.idempotencyKey(), request.description(),
                fraudResult
        );

        fraudPreventionService.logRiskLog(transaction, fraudResult);

        // 2. Initiate API
        mpesaB2CService.initiateB2C(
                request.phoneNumber(), request.amount(), transaction.getTransactionRef());

        // 3. DO NOT update balance or ledger here.
        return buildResponse(transaction, wallet.getBalance());
    }

    // TRANSFER
    @Transactional
    public TransactionResponseDto transfer(TransferRequestDto request) {

        // 1. Validate step-up token
        stepUpService.validateStepUpToken(request.stepUpToken());

        // 2. Get sender wallet
        String senderEmail = getLoggedInEmail();
        Wallet senderWallet = getWalletByEmail(senderEmail);
        User user = senderWallet.getOwner();

        FraudPreventionService.FraudCheckResult fraudResult = fraudPreventionService.evaluate(user, request.amount(), "TRANSFER");
        if (fraudResult.block) {
            fraudPreventionService.logBlockedTransaction(user, request.amount(), "TRANSFER", fraudResult);
            throw new FraudDetectionException("Transaction rejected due to fraud checks: " + fraudResult.rulesTriggered);
        }

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
                request.description(),
                fraudResult
        );

        fraudPreventionService.logRiskLog(transaction, fraudResult);

        // 10. Create ledger entries for both wallets
        createLedgerEntry(senderWallet, transaction, request.amount(),
                senderBalanceAfter, ApplicationConstants.LEDGER_DEBIT);

        createLedgerEntry(recipientWallet, transaction, request.amount(),
                recipientBalanceAfter, ApplicationConstants.LEDGER_CREDIT);

        // Send email to sender
        emailService.sendTransferSenderConfirmation(
                senderWallet.getOwner().getEmail(),
                senderWallet.getOwner().getFullName(),
                request.recipientEmail(),
                request.amount(),
                senderBalanceAfter,
                transaction.getTransactionRef()
        );

// Send email to recipient
        emailService.sendTransferRecipientConfirmation(
                recipientWallet.getOwner().getEmail(),
                recipientWallet.getOwner().getFullName(),
                senderEmail,
                request.amount(),
                recipientBalanceAfter,
                transaction.getTransactionRef()
        );

        log.info("Confirmed Transfer of {} KES from {} to {} successful",
                request.amount(), senderEmail, request.recipientEmail());

        return buildResponse(transaction, senderBalanceAfter);
    }

    // Helper — create transaction
    private Transaction createTransaction(
            Wallet fromWallet, Wallet toWallet,
            BigDecimal amount, String type,
            String status, String idempotencyKey,
            String description, FraudPreventionService.FraudCheckResult fraudResult) {

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
        transaction.setTransactionCode(transactionCodeGenerator.generateCode());
        transaction.setDescription(description);

        if (fraudResult != null) {
            transaction.setIpAddress(fraudResult.clientIp);
            transaction.setDeviceFingerprint(fraudResult.deviceFingerprint);
            transaction.setLocationGeo(fraudResult.locationGeo);
            transaction.setIsSuspicious(fraudResult.suspicious);
        } else {
            transaction.setIsSuspicious(false);
        }

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
                transaction.getTransactionCode(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getAmount(),
                balanceAfter,
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }
}
