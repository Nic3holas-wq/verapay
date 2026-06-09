package com.nicko.verapay.repository;

import com.nicko.verapay.entity.LedgerEntry;
import com.nicko.verapay.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByWalletOrderByCreatedAtDesc(Wallet wallet);
}