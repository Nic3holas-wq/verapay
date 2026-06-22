package com.nicko.verapay.admin.service;

import com.nicko.verapay.dto.*;
import com.nicko.verapay.dto.admin.UserAdminDto;
import com.nicko.verapay.dto.admin.UserDetailAdminDto;
import com.nicko.verapay.dto.transactions.TransactionHistoryDto;
import com.nicko.verapay.entity.Transaction;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.entity.Wallet;
import com.nicko.verapay.exception.UserNotFoundException;
import com.nicko.verapay.exception.WalletNotFoundException;
import com.nicko.verapay.repository.TransactionRepository;
import com.nicko.verapay.repository.UserRepository;
import com.nicko.verapay.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    // View all users (paginated, with optional search/filter)
    public Page<UserAdminDto> getAllUsers(String email, Boolean isActive,
                                          int page, int size) {
        Page<User> users = userRepository.searchUsers(
                email, isActive, PageRequest.of(page, size));

        return users.map(this::mapToAdminDto);
    }

    // View a specific user's wallet + recent transactions
    public UserDetailAdminDto getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with id: " + userId));

        Wallet wallet = walletRepository.findByOwnerEmail(user.getEmail())
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for user: " + user.getEmail()));

        var recentTransactions = transactionRepository
                .findRecentByWallet(wallet, PageRequest.of(0, 10))
                .stream()
                .map(t -> mapToHistoryDto(t, wallet))
                .toList();

        return new UserDetailAdminDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole().getName(),
                user.getIsActive(),
                user.getCreatedAt(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getIsActive(),
                recentTransactions
        );
    }

    // Activate/Deactivate a user (and their wallet)
    @Transactional
    public UserAdminDto toggleUserStatus(Long userId, boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with id: " + userId));

        user.setIsActive(isActive);
        userRepository.save(user);

        // Also toggle wallet status to match
        walletRepository.findByOwnerEmail(user.getEmail())
                .ifPresent(wallet -> {
                    wallet.setIsActive(isActive);
                    walletRepository.save(wallet);
                });

        log.info("Admin {} user account: {}",
                isActive ? "activated" : "deactivated", user.getEmail());

        return mapToAdminDto(user);
    }

    private UserAdminDto mapToAdminDto(User user) {
        return new UserAdminDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole().getName(),
                user.getIsActive(),
                user.getCreatedAt()
        );
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
                t.getTransactionCode(),
                t.getType(),
                t.getStatus(),
                t.getAmount(),
                t.getDescription(),
                t.getCreatedAt(),
                direction
        );
    }
}
