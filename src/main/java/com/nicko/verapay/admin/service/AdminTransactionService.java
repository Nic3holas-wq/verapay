package com.nicko.verapay.admin.service;

import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.dto.admin.AdminTransactionDto;
import com.nicko.verapay.dto.admin.TransactionStatsDto;
import com.nicko.verapay.entity.Transaction;
import com.nicko.verapay.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminTransactionService {

    private final TransactionRepository transactionRepository;

    // View all transactions (paginated, optional status filter)
    public Page<AdminTransactionDto> getAllTransactions(String status,
                                                        int page, int size) {
        Page<Transaction> transactions = transactionRepository
                .findAllFiltered(status, PageRequest.of(page, size));

        return transactions.map(this::mapToAdminDto);
    }

    // Transaction stats
    public TransactionStatsDto getStats() {
        long total = transactionRepository.count();
        long pending = transactionRepository.countByStatus(
                ApplicationConstants.TRANSACTION_STATUS_PENDING);
        long success = transactionRepository.countByStatus(
                ApplicationConstants.TRANSACTION_STATUS_SUCCESS);
        long failed = transactionRepository.countByStatus(
                ApplicationConstants.TRANSACTION_STATUS_FAILED);

        var depositVolume = transactionRepository.sumAmountByTypeAndSuccess(
                ApplicationConstants.TRANSACTION_TYPE_DEPOSIT);
        var withdrawVolume = transactionRepository.sumAmountByTypeAndSuccess(
                ApplicationConstants.TRANSACTION_TYPE_WITHDRAW);
        var transferVolume = transactionRepository.sumAmountByTypeAndSuccess(
                ApplicationConstants.TRANSACTION_TYPE_TRANSFER);

        return new TransactionStatsDto(
                total, pending, success, failed,
                depositVolume, withdrawVolume, transferVolume
        );
    }

    private AdminTransactionDto mapToAdminDto(Transaction t) {
        String fromEmail = t.getFromWallet() != null
                ? t.getFromWallet().getOwner().getEmail()
                : null;
        String toEmail = t.getToWallet() != null
                ? t.getToWallet().getOwner().getEmail()
                : null;

        return new AdminTransactionDto(
                t.getId(),
                t.getTransactionRef(),
                t.getTransactionCode(),
                t.getType(),
                t.getStatus(),
                t.getAmount(),
                fromEmail,
                toEmail,
                t.getDescription(),
                t.getCreatedAt()
        );
    }
}
