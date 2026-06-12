package com.nicko.verapay.transaction.service;

import com.nicko.verapay.dto.transactions.TransactionHistoryDto;
import com.nicko.verapay.dto.transactions.WalletProfileResponseDto;
import com.nicko.verapay.entity.Transaction;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.entity.Wallet;
import com.nicko.verapay.exception.WalletNotFoundException;
import com.nicko.verapay.repository.TransactionRepository;
import com.nicko.verapay.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public WalletProfileResponseDto getProfile() {
        Wallet wallet = getWalletForLoggedInUser();
        User user = wallet.getOwner();

        return new WalletProfileResponseDto(
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getIsActive(),
                user.getCreatedAt(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getIsActive()
        );
    }

    public List<TransactionHistoryDto> getRecentTransactions(int limit) {
        Wallet wallet = getWalletForLoggedInUser();

        List<Transaction> transactions = transactionRepository
                .findRecentByWallet(wallet, PageRequest.of(0, limit));

        return transactions.stream()
                .map(t -> mapToHistoryDto(t, wallet))
                .toList();
    }

    private TransactionHistoryDto mapToHistoryDto(Transaction t, Wallet wallet) {
        String direction;
        if (t.getFromWallet() != null && t.getFromWallet().getId().equals(wallet.getId())) {
            direction = "DEBIT";
        } else {
            direction = "CREDIT";
        }

        return new TransactionHistoryDto(
                t.getId(),
                t.getTransactionRef(),
                t.getType(),
                t.getStatus(),
                t.getAmount(),
                t.getDescription(),
                t.getCreatedAt(),
                direction
        );
    }

    private Wallet getWalletForLoggedInUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return walletRepository.findByOwnerEmail(email)
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for user: " + email));
    }
}