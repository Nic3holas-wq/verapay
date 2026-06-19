package com.nicko.verapay.transaction.service;

import com.nicko.verapay.dto.transactions.TransactionHistoryDto;
import com.nicko.verapay.dto.transactions.WalletProfileResponseDto;
import com.nicko.verapay.entity.Transaction;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.entity.Wallet;
import com.nicko.verapay.exception.WalletNotFoundException;
import com.nicko.verapay.repository.TransactionRepository;
import com.nicko.verapay.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private Authentication authentication;
    @Mock private SecurityContext securityContext;

    @InjectMocks
    private WalletService walletService;

    private Wallet wallet;
    private User user;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("user@e.com");

        user = new User();
        user.setFullName("John");
        user.setEmail("user@e.com");
        user.setPhoneNumber("0700000000");
        user.setIsActive(true);
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        wallet = new Wallet();
        wallet.setId(1L);
        wallet.setOwner(user);
        wallet.setBalance(new BigDecimal("1500.00"));
        wallet.setCurrency("KES");
        wallet.setIsActive(true);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── getProfile() ──────────────────────────────────────────

    @Test
    @DisplayName("getProfile: returns correct user and wallet details for logged-in user")
    void getProfile_Success_ReturnsCorrectDetails() {
        when(walletRepository.findByOwnerEmail("user@e.com"))
                .thenReturn(Optional.of(wallet));

        WalletProfileResponseDto result = walletService.getProfile();

        assertEquals("John", result.fullName());
        assertEquals("user@e.com", result.email());
        assertEquals("0700000000", result.phoneNumber());
        assertTrue(result.isAccountActive());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), result.memberSince());
        assertEquals(new BigDecimal("1500.00"), result.balance());
        assertEquals("KES", result.currency());
        assertTrue(result.isWalletActive());
    }

    @Test
    @DisplayName("getProfile: throws when wallet not found for logged-in user")
    void getProfile_WalletNotFound_Throws() {
        when(walletRepository.findByOwnerEmail("user@e.com"))
                .thenReturn(Optional.empty());

        WalletNotFoundException ex = assertThrows(WalletNotFoundException.class,
                () -> walletService.getProfile());

        assertTrue(ex.getMessage().contains("user@e.com"));
    }

    // ── getRecentTransactions() ───────────────────────────────

    @Test
    @DisplayName("getRecentTransactions: marks transaction as DEBIT when wallet is the sender")
    void getRecentTransactions_WalletIsSender_MarkedAsDebit() {
        when(walletRepository.findByOwnerEmail("user@e.com"))
                .thenReturn(Optional.of(wallet));

        Wallet otherWallet = new Wallet();
        otherWallet.setId(2L);

        Transaction outgoing = buildTransaction(
                wallet, otherWallet, "TXN-OUT-001", new BigDecimal("100.00"));

        when(transactionRepository.findRecentByWallet(
                eq(wallet), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(outgoing));

        List<TransactionHistoryDto> result = walletService.getRecentTransactions(10);

        assertEquals(1, result.size());
        assertEquals("DEBIT", result.get(0).direction());
        assertEquals("TXN-OUT-001", result.get(0).transactionRef());
    }

    @Test
    @DisplayName("getRecentTransactions: marks transaction as CREDIT when wallet is the recipient")
    void getRecentTransactions_WalletIsRecipient_MarkedAsCredit() {
        when(walletRepository.findByOwnerEmail("user@e.com"))
                .thenReturn(Optional.of(wallet));

        Wallet otherWallet = new Wallet();
        otherWallet.setId(2L);

        Transaction incoming = buildTransaction(
                otherWallet, wallet, "TXN-IN-001", new BigDecimal("250.00"));

        when(transactionRepository.findRecentByWallet(
                eq(wallet), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(incoming));

        List<TransactionHistoryDto> result = walletService.getRecentTransactions(10);

        assertEquals(1, result.size());
        assertEquals("CREDIT", result.get(0).direction());
        assertEquals("TXN-IN-001", result.get(0).transactionRef());
    }

    @Test
    @DisplayName("getRecentTransactions: marks transaction as CREDIT when fromWallet is null (deposit)")
    void getRecentTransactions_DepositWithNullFromWallet_MarkedAsCredit() {
        when(walletRepository.findByOwnerEmail("user@e.com"))
                .thenReturn(Optional.of(wallet));

        // Deposits have fromWallet = null (money comes from Mpesa, not another wallet)
        Transaction deposit = buildTransaction(
                null, wallet, "TXN-DEP-001", new BigDecimal("500.00"));

        when(transactionRepository.findRecentByWallet(
                eq(wallet), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(deposit));

        List<TransactionHistoryDto> result = walletService.getRecentTransactions(10);

        assertEquals(1, result.size());
        assertEquals("CREDIT", result.get(0).direction());
    }

    @Test
    @DisplayName("getRecentTransactions: passes the given limit through to PageRequest")
    void getRecentTransactions_RespectsLimitParameter() {
        when(walletRepository.findByOwnerEmail("user@e.com"))
                .thenReturn(Optional.of(wallet));
        when(transactionRepository.findRecentByWallet(
                eq(wallet), eq(PageRequest.of(0, 5))))
                .thenReturn(List.of());

        walletService.getRecentTransactions(5);

        verify(transactionRepository).findRecentByWallet(
                eq(wallet), eq(PageRequest.of(0, 5)));
    }

    @Test
    @DisplayName("getRecentTransactions: returns empty list when wallet has no transactions")
    void getRecentTransactions_NoTransactions_ReturnsEmptyList() {
        when(walletRepository.findByOwnerEmail("user@e.com"))
                .thenReturn(Optional.of(wallet));
        when(transactionRepository.findRecentByWallet(
                eq(wallet), any(PageRequest.class)))
                .thenReturn(List.of());

        List<TransactionHistoryDto> result = walletService.getRecentTransactions(10);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getRecentTransactions: throws when wallet not found for logged-in user")
    void getRecentTransactions_WalletNotFound_Throws() {
        when(walletRepository.findByOwnerEmail("user@e.com"))
                .thenReturn(Optional.empty());

        assertThrows(WalletNotFoundException.class,
                () -> walletService.getRecentTransactions(10));
    }

    // ── helper ────────────────────────────────────────────────

    private Transaction buildTransaction(
            Wallet fromWallet, Wallet toWallet,
            String transactionRef, BigDecimal amount) {

        Transaction t = new Transaction();
        t.setFromWallet(fromWallet);
        t.setToWallet(toWallet);
        t.setTransactionRef(transactionRef);
        t.setType("TRANSFER");
        t.setStatus("SUCCESS");
        t.setAmount(amount);
        t.setDescription("Test transaction");
        t.setCreatedAt(Instant.now());
        return t;
    }
}