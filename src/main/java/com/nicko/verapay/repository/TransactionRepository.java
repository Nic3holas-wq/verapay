package com.nicko.verapay.repository;

import com.nicko.verapay.entity.Transaction;
import com.nicko.verapay.entity.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    Optional<Transaction> findByTransactionRef(String transactionRef);
    Optional<Transaction> findByCheckoutRequestId(String checkoutRequestId);

    @Query("SELECT t FROM Transaction t " +
            "WHERE t.fromWallet = :wallet OR t.toWallet = :wallet " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findRecentByWallet(@Param("wallet") Wallet wallet, Pageable pageable);

    // Admin — all transactions for a specific wallet
    @Query("SELECT t FROM Transaction t " +
            "WHERE t.fromWallet = :wallet OR t.toWallet = :wallet " +
            "ORDER BY t.createdAt DESC")
    Page<Transaction> findByWallet(@Param("wallet") Wallet wallet, Pageable pageable);

    // Admin — all transactions, optional status filter
    @Query("SELECT t FROM Transaction t " +
            "WHERE (:status IS NULL OR t.status = :status) " +
            "ORDER BY t.createdAt DESC")
    Page<Transaction> findAllFiltered(@Param("status") String status, Pageable pageable);

    // Admin — stats
    long countByStatus(String status);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.type = :type AND t.status = 'SUCCESS'")
    BigDecimal sumAmountByTypeAndSuccess(@Param("type") String type);

    boolean existsByReversedTransactionId(Long transactionId);
}