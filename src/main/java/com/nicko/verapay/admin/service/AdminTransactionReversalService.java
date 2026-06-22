package com.nicko.verapay.admin.service;

import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.dto.transactions.TransactionResponseDto;
import com.nicko.verapay.entity.LedgerEntry;
import com.nicko.verapay.entity.Transaction;
import com.nicko.verapay.entity.Wallet;
import com.nicko.verapay.exception.InvalidReversalException;
import com.nicko.verapay.exception.TransactionNotFoundException;
import com.nicko.verapay.notifications.service.EmailService;
import com.nicko.verapay.repository.LedgerEntryRepository;
import com.nicko.verapay.repository.TransactionRepository;
import com.nicko.verapay.repository.WalletRepository;
import com.nicko.verapay.dto.transactions.TransactionCodeGenerator;
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
public class AdminTransactionReversalService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final EmailService emailService;
    private final TransactionCodeGenerator transactionCodeGenerator;

    @Transactional
    public TransactionResponseDto reverseTransaction(Long transactionId, String reason) {

        // 1. Find the original transaction
        Transaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found with id: " + transactionId));

        // 2. Only TRANSFER transactions can be reversed
        if (!original.getType().equals(ApplicationConstants.TRANSACTION_TYPE_TRANSFER)) {
            throw new InvalidReversalException(
                    "Only TRANSFER transactions can be reversed. This is a "
                            + original.getType() + " transaction.");
        }

        // 3. Only SUCCESS transactions can be reversed
        if (!original.getStatus().equals(ApplicationConstants.TRANSACTION_STATUS_SUCCESS)) {
            throw new InvalidReversalException(
                    "Only SUCCESS transactions can be reversed. Current status: "
                            + original.getStatus());
        }

        // 4. Prevent double reversal
        if (transactionRepository.existsByReversedTransactionId(original.getId())) {
            throw new InvalidReversalException(
                    "Transaction " + original.getTransactionRef()
                            + " has already been reversed");
        }

        // 5. Get original sender (from) and recipient (to) wallets
        Wallet originalSender = original.getFromWallet();
        Wallet originalRecipient = original.getToWallet();

        // 6. Check recipient has sufficient balance to reverse
        if (originalRecipient.getBalance().compareTo(original.getAmount()) < 0) {
            throw new InvalidReversalException(
                    "Cannot reverse: recipient wallet has insufficient balance ("
                            + originalRecipient.getBalance() + " KES) to refund "
                            + original.getAmount() + " KES");
        }

        // 7. Reverse the money flow
        //    Original: sender -> recipient
        //    Reversal: recipient -> sender
        BigDecimal recipientBalanceAfter = originalRecipient.getBalance()
                .subtract(original.getAmount());
        originalRecipient.setBalance(recipientBalanceAfter);
        walletRepository.save(originalRecipient);

        BigDecimal senderBalanceAfter = originalSender.getBalance()
                .add(original.getAmount());
        originalSender.setBalance(senderBalanceAfter);
        walletRepository.save(originalSender);

        // 8. Create the reversal transaction record
        Transaction reversal = new Transaction();
        reversal.setFromWallet(originalRecipient);  // money flows OUT of recipient
        reversal.setToWallet(originalSender);       // back TO original sender
        reversal.setAmount(original.getAmount());
        reversal.setType(ApplicationConstants.TRANSACTION_TYPE_REVERSAL);
        reversal.setStatus(ApplicationConstants.TRANSACTION_STATUS_SUCCESS);
        reversal.setIdempotencyKey("REV-" + original.getTransactionRef());
        reversal.setTransactionRef("TXN-" +
                UUID.randomUUID().toString().toUpperCase()
                        .replace("-", "").substring(0, 12));
        reversal.setTransactionCode(transactionCodeGenerator.generateCode());
        reversal.setDescription("Reversal of " + original.getTransactionRef()
                + ": " + reason);
        reversal.setReversedTransactionId(original.getId());
        reversal.setReversalReason(reason);
        reversal.setCreatedAt(Instant.now());
        transactionRepository.save(reversal);

        // 9. Create ledger entries for the reversal
        createLedgerEntry(originalRecipient, reversal, original.getAmount(),
                recipientBalanceAfter, ApplicationConstants.LEDGER_DEBIT);

        createLedgerEntry(originalSender, reversal, original.getAmount(),
                senderBalanceAfter, ApplicationConstants.LEDGER_CREDIT);

        // 10. Mark the original transaction as REVERSED
        original.setStatus(ApplicationConstants.TRANSACTION_STATUS_REVERSED);
        transactionRepository.save(original);

        // 11. Notify both parties
        String adminEmail = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        emailService.sendTransactionReversalEmail(
                originalSender.getOwner().getEmail(),
                originalSender.getOwner().getFullName(),
                original.getAmount(),
                senderBalanceAfter,
                original.getTransactionRef(),
                reversal.getTransactionRef(),
                reason
        );

        emailService.sendTransactionReversalEmail(
                originalRecipient.getOwner().getEmail(),
                originalRecipient.getOwner().getFullName(),
                original.getAmount(),
                recipientBalanceAfter,
                original.getTransactionRef(),
                reversal.getTransactionRef(),
                reason
        );

        log.warn("🔄 Admin {} reversed transaction {} (reversal: {}) reason: {}",
                adminEmail, original.getTransactionRef(),
                reversal.getTransactionRef(), reason);

        return new TransactionResponseDto(
                reversal.getId(),
                reversal.getTransactionRef(),
                reversal.getTransactionCode(),
                reversal.getType(),
                reversal.getStatus(),
                reversal.getAmount(),
                null,
                reversal.getDescription(),
                reversal.getCreatedAt()
        );
    }

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