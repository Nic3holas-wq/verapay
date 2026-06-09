package com.nicko.verapay.repository;

import com.nicko.verapay.entity.Transaction;
import com.nicko.verapay.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByTransactionRef(String transactionRef);

    List<Transaction> findByFromWalletOrToWalletOrderByCreatedAtDesc(
            Wallet fromWallet, Wallet toWallet);

    Optional<Transaction> findByCheckoutRequestId(String checkoutRequestId);
}