package com.nicko.verapay.repository;

import com.nicko.verapay.entity.Wallet;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    @Query("SELECT w FROM Wallet w WHERE w.owner.email = :email")
    Optional<Wallet> findByOwnerEmail(@Param("email") String email);

    Optional<Wallet> findByPublicId(UUID publicId);

    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithLock(@Param("id") Long id);
}
