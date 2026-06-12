package com.nicko.verapay.repository;

import com.nicko.verapay.entity.Transaction;
import com.nicko.verapay.entity.Wallet;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @Query("SELECT t FROM Transaction t " +
            "WHERE t.fromWallet = :wallet OR t.toWallet = :wallet " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findRecentByWallet(@Param("wallet") Wallet wallet,
                                         Pageable pageable);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByTransactionRef(String transactionRef);

    List<Transaction> findByFromWalletOrToWalletOrderByCreatedAtDesc(
            Wallet fromWallet, Wallet toWallet);

    Optional<Transaction> findByCheckoutRequestId(String checkoutRequestId);
}