package com.nicko.verapay.transaction.service;

import com.nicko.verapay.auth.StepUpService;
import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.dto.mpesa.StkPushResponseDto;
import com.nicko.verapay.dto.transactions.*;
import com.nicko.verapay.entity.*;
import com.nicko.verapay.exception.InsufficientFundsException;
import com.nicko.verapay.exception.WalletNotFoundException;
import com.nicko.verapay.notifications.service.EmailService;
import com.nicko.verapay.transaction.service.FraudPreventionService.FraudCheckResult;
import com.nicko.verapay.payments.service.MpesaB2CService;
import com.nicko.verapay.payments.service.MpesaC2BService;
import com.nicko.verapay.repository.LedgerEntryRepository;
import com.nicko.verapay.repository.TransactionRepository;
import com.nicko.verapay.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import java.util.Collections;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private StepUpService stepUpService;
    @Mock private MpesaC2BService mpesaC2BService;
    @Mock private MpesaB2CService mpesaB2CService;
    @Mock private EmailService emailService;
    @Mock private TransactionCodeGenerator transactionCodeGenerator;
    @Mock private FraudPreventionService fraudPreventionService;
    @Mock private Authentication authentication;
    @Mock private SecurityContext securityContext;

    @InjectMocks
    private TransactionService transactionService;

    private Wallet senderWallet;
    private Wallet recipientWallet;
    private User senderUser;
    private User recipientUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
        lenient().when(transactionCodeGenerator.generateCode()).thenReturn("VP-20260622-000001");

        // Default: fraud checks pass (ALLOW)
        FraudCheckResult allowResult = new FraudCheckResult(
                false, false, Collections.emptyList(),
                "127.0.0.1", "test-fingerprint", "Local Development, LAN", "ALLOW");
        lenient().when(fraudPreventionService.evaluate(any(), any(), any())).thenReturn(allowResult);

        senderUser = new User();
        senderUser.setEmail("sender@e.com");
        senderUser.setFullName("Sender");

        recipientUser = new User();
        recipientUser.setEmail("recipient@e.com");
        recipientUser.setFullName("Recipient");

        senderWallet = new Wallet();
        senderWallet.setId(1L);
        senderWallet.setBalance(new BigDecimal("1000.00"));
        senderWallet.setCurrency("KES");
        senderWallet.setOwner(senderUser);

        recipientWallet = new Wallet();
        recipientWallet.setId(2L);
        recipientWallet.setBalance(new BigDecimal("500.00"));
        recipientWallet.setCurrency("KES");
        recipientWallet.setOwner(recipientUser);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void givenLoggedInAs(String email) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(email);
    }

    // ── deposit() ─────────────────────────────────────────────

    @Test
    @DisplayName("deposit: creates PENDING transaction and initiates STK push on success")
    void deposit_Success_CreatesPendingTransactionAndInitiatesStkPush() {
        givenLoggedInAs("sender@e.com");

        DepositRequestDto request = new DepositRequestDto(
                new BigDecimal("100.00"),
                "0700000000",
                "Deposit",
                "dep-001");

        StkPushResponseDto stkResponse = new StkPushResponseDto(
                "merchant-123",
                "ws_CO_checkout_123",
                "0",
                "Success",
                "Check phone");

        when(walletRepository.findByOwnerEmail("sender@e.com"))
                .thenReturn(Optional.of(senderWallet));
        when(transactionRepository.findByIdempotencyKey("dep-001"))
                .thenReturn(Optional.empty());
        when(mpesaC2BService.initiateStkPush(
                "0700000000",
                new BigDecimal("100.00"),
                "dep-001"))
                .thenReturn(stkResponse);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        TransactionResponseDto result = transactionService.deposit(request);

        assertNotNull(result);
        assertEquals(ApplicationConstants.TRANSACTION_STATUS_PENDING, result.status());
        assertEquals(ApplicationConstants.TRANSACTION_TYPE_DEPOSIT, result.type());
        assertEquals(new BigDecimal("100.00"), result.amount());

        verify(mpesaC2BService).initiateStkPush(
                "0700000000",
                new BigDecimal("100.00"),
                "dep-001");

        // Confirm checkoutRequestId was persisted on the transaction
        ArgumentCaptor<Transaction> txCaptor =
                ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeastOnce()).save(txCaptor.capture());
        Transaction savedWithCheckout = txCaptor.getAllValues().stream()
                .filter(t -> t.getCheckoutRequestId() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No transaction saved with checkoutRequestId"));
        assertEquals("ws_CO_checkout_123", savedWithCheckout.getCheckoutRequestId());

        // Balance must NOT change at deposit time — only on webhook
        assertEquals(new BigDecimal("1000.00"), senderWallet.getBalance());
    }

    @Test
    @DisplayName("deposit: throws when idempotency key already exists")
    void deposit_DuplicateIdempotencyKey_Throws() {
        givenLoggedInAs("sender@e.com");

        DepositRequestDto request = new DepositRequestDto(
                new BigDecimal("100.00"),
                "0700000000",
                "Deposit",
                "dep-001");

        when(walletRepository.findByOwnerEmail("sender@e.com"))
                .thenReturn(Optional.of(senderWallet));
        when(transactionRepository.findByIdempotencyKey("dep-001"))
                .thenReturn(Optional.of(new Transaction()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.deposit(request));

        assertEquals("Duplicate transaction request", ex.getMessage());
        verify(mpesaC2BService, never()).initiateStkPush(any(), any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("deposit: throws when wallet not found for logged-in user")
    void deposit_WalletNotFound_Throws() {
        givenLoggedInAs("sender@e.com");

        DepositRequestDto request = new DepositRequestDto(
                new BigDecimal("100.00"),
                "0700000000",
                "Deposit",
                "dep-001");

        when(walletRepository.findByOwnerEmail("sender@e.com"))
                .thenReturn(Optional.empty());

        assertThrows(WalletNotFoundException.class,
                () -> transactionService.deposit(request));

        verify(mpesaC2BService, never()).initiateStkPush(any(), any(), any());
        verify(transactionRepository, never()).save(any());
    }

    // ── withdraw() ────────────────────────────────────────────

    @Test
    @DisplayName("withdraw: creates PENDING transaction and initiates B2C on success")
    void withdraw_Success_CreatesPendingTransactionAndInitiatesB2C() {
        givenLoggedInAs("sender@e.com");

        WithdrawRequestDto request = new WithdrawRequestDto(
                new BigDecimal("200.00"),
                "0700000000",
                "ATM",
                "wit-001",
                "step-up-token");

        when(walletRepository.findByOwnerEmail("sender@e.com"))
                .thenReturn(Optional.of(senderWallet));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        TransactionResponseDto result = transactionService.withdraw(request);

        assertNotNull(result);
        assertEquals(ApplicationConstants.TRANSACTION_STATUS_PENDING, result.status());
        assertEquals(ApplicationConstants.TRANSACTION_TYPE_WITHDRAW, result.type());
        assertEquals(new BigDecimal("200.00"), result.amount());

        // Step-up must be validated first
        verify(stepUpService).validateStepUpToken("step-up-token");

        // B2C must be called with correct phone and amount
        verify(mpesaB2CService).initiateB2C(
                eq("0700000000"),
                eq(new BigDecimal("200.00")),
                any(String.class));

        // Balance must NOT change — only on B2C webhook success
        assertEquals(new BigDecimal("1000.00"), senderWallet.getBalance());
    }

    @Test
    @DisplayName("withdraw: throws when balance is insufficient")
    void withdraw_InsufficientFunds_Throws() {
        givenLoggedInAs("sender@e.com");

        WithdrawRequestDto request = new WithdrawRequestDto(
                new BigDecimal("9999.00"),
                "0700000000",
                "ATM",
                "wit-001",
                "step-up-token");

        when(walletRepository.findByOwnerEmail("sender@e.com"))
                .thenReturn(Optional.of(senderWallet));

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> transactionService.withdraw(request));

        assertEquals("Insufficient funds", ex.getMessage());
        verify(mpesaB2CService, never()).initiateB2C(any(), any(), any());
        verify(transactionRepository, never()).save(any());

        // Balance must be unchanged
        assertEquals(new BigDecimal("1000.00"), senderWallet.getBalance());
    }

    @Test
    @DisplayName("withdraw: throws when wallet not found")
    void withdraw_WalletNotFound_Throws() {
        givenLoggedInAs("sender@e.com");

        WithdrawRequestDto request = new WithdrawRequestDto(
                new BigDecimal("200.00"),
                "0700000000",
                "ATM",
                "wit-001",
                "step-up-token");

        when(walletRepository.findByOwnerEmail("sender@e.com"))
                .thenReturn(Optional.empty());

        assertThrows(WalletNotFoundException.class,
                () -> transactionService.withdraw(request));

        verify(mpesaB2CService, never()).initiateB2C(any(), any(), any());
        verify(transactionRepository, never()).save(any());
    }

    // ── transfer() ────────────────────────────────────────────

    @Test
    @DisplayName("transfer: debits sender, credits recipient, creates ledger entries and sends emails")
    void transfer_Success_CorrectBalancesLedgerAndEmails() {
        givenLoggedInAs("sender@e.com");

        TransferRequestDto request = new TransferRequestDto(
                "recipient@e.com",
                new BigDecimal("300.00"),
                "Payment",
                "trf-001",
                "step-up-token");

        when(walletRepository.findByOwnerEmail("sender@e.com"))
                .thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByOwnerEmail("recipient@e.com"))
                .thenReturn(Optional.of(recipientWallet));
        when(transactionRepository.findByIdempotencyKey("trf-001"))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        TransactionResponseDto result = transactionService.transfer(request);

        assertNotNull(result);
        assertEquals(ApplicationConstants.TRANSACTION_STATUS_SUCCESS, result.status());
        assertEquals(ApplicationConstants.TRANSACTION_TYPE_TRANSFER, result.type());
        assertEquals(new BigDecimal("300.00"), result.amount());

        // Sender debited correctly: 1000 - 300 = 700
        assertEquals(new BigDecimal("700.00"), senderWallet.getBalance());

        // Recipient credited correctly: 500 + 300 = 800
        assertEquals(new BigDecimal("800.00"), recipientWallet.getBalance());

        // Both wallets saved
        verify(walletRepository).save(senderWallet);
        verify(walletRepository).save(recipientWallet);

        // Exactly two ledger entries created — one DEBIT, one CREDIT
        ArgumentCaptor<LedgerEntry> ledgerCaptor =
                ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(ledgerCaptor.capture());

        LedgerEntry debitEntry = ledgerCaptor.getAllValues().stream()
                .filter(e -> e.getEntryType().equals(ApplicationConstants.LEDGER_DEBIT))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No DEBIT ledger entry found"));

        LedgerEntry creditEntry = ledgerCaptor.getAllValues().stream()
                .filter(e -> e.getEntryType().equals(ApplicationConstants.LEDGER_CREDIT))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No CREDIT ledger entry found"));

        assertEquals(new BigDecimal("700.00"), debitEntry.getBalanceAfter());
        assertEquals(new BigDecimal("800.00"), creditEntry.getBalanceAfter());

        // Step-up validated before any money movement
        verify(stepUpService).validateStepUpToken("step-up-token");

        // Confirmation emails sent to both parties with correct values
        verify(emailService).sendTransferSenderConfirmation(
                eq("sender@e.com"),
                eq("Sender"),
                eq("recipient@e.com"),
                eq(new BigDecimal("300.00")),
                eq(new BigDecimal("700.00")),
                any(String.class));

        verify(emailService).sendTransferRecipientConfirmation(
                eq("recipient@e.com"),
                eq("Recipient"),
                eq("sender@e.com"),
                eq(new BigDecimal("300.00")),
                eq(new BigDecimal("800.00")),
                any(String.class));
    }

    @Test
    @DisplayName("transfer: throws when sender tries to transfer to own wallet")
    void transfer_SelfTransfer_Throws() {
        givenLoggedInAs("sender@e.com");

        TransferRequestDto request = new TransferRequestDto(
                "sender@e.com",
                new BigDecimal("100.00"),
                "Self",
                "trf-001",
                "step-up-token");

        // Both lookups return the same wallet object — same id = self transfer
        when(walletRepository.findByOwnerEmail("sender@e.com"))
                .thenReturn(Optional.of(senderWallet));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.transfer(request));

        assertEquals("Cannot transfer to your own wallet", ex.getMessage());
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(emailService, never()).sendTransferSenderConfirmation(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("transfer: throws when idempotency key already exists")
    void transfer_DuplicateIdempotencyKey_Throws() {
        givenLoggedInAs("sender@e.com");

        TransferRequestDto request = new TransferRequestDto(
                "recipient@e.com",
                new BigDecimal("100.00"),
                "Payment",
                "trf-001",
                "step-up-token");

        when(walletRepository.findByOwnerEmail("sender@e.com"))
                .thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByOwnerEmail("recipient@e.com"))
                .thenReturn(Optional.of(recipientWallet));
        when(transactionRepository.findByIdempotencyKey("trf-001"))
                .thenReturn(Optional.of(new Transaction()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.transfer(request));

        assertEquals("Duplicate transaction request", ex.getMessage());
        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
        verify(emailService, never()).sendTransferSenderConfirmation(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("transfer: throws when sender has insufficient funds")
    void transfer_InsufficientFunds_Throws() {
        givenLoggedInAs("sender@e.com");

        TransferRequestDto request = new TransferRequestDto(
                "recipient@e.com",
                new BigDecimal("9999.00"),
                "Payment",
                "trf-001",
                "step-up-token");

        when(walletRepository.findByOwnerEmail("sender@e.com"))
                .thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByOwnerEmail("recipient@e.com"))
                .thenReturn(Optional.of(recipientWallet));
        when(transactionRepository.findByIdempotencyKey("trf-001"))
                .thenReturn(Optional.empty());

        assertThrows(InsufficientFundsException.class,
                () -> transactionService.transfer(request));

        // No balance changes should have been made
        assertEquals(new BigDecimal("1000.00"), senderWallet.getBalance());
        assertEquals(new BigDecimal("500.00"), recipientWallet.getBalance());
        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
        verify(emailService, never()).sendTransferSenderConfirmation(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("transfer: throws when recipient wallet does not exist")
    void transfer_RecipientWalletNotFound_Throws() {
        givenLoggedInAs("sender@e.com");

        TransferRequestDto request = new TransferRequestDto(
                "ghost@e.com",
                new BigDecimal("100.00"),
                "Payment",
                "trf-001",
                "step-up-token");

        when(walletRepository.findByOwnerEmail("sender@e.com"))
                .thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByOwnerEmail("ghost@e.com"))
                .thenReturn(Optional.empty());

        WalletNotFoundException ex = assertThrows(WalletNotFoundException.class,
                () -> transactionService.transfer(request));

        assertTrue(ex.getMessage().contains("ghost@e.com"));
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(emailService, never()).sendTransferSenderConfirmation(
                any(), any(), any(), any(), any(), any());
    }
}