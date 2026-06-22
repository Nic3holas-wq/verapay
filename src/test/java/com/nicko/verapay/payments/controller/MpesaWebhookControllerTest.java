package com.nicko.verapay.payments.controller;

import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.dto.mpesa.B2CResultDto;
import com.nicko.verapay.dto.mpesa.StkCallbackDto;
import com.nicko.verapay.entity.LedgerEntry;
import com.nicko.verapay.entity.Transaction;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.entity.Wallet;
import com.nicko.verapay.notifications.service.EmailService;
import com.nicko.verapay.payments.security.WebhookTokenValidator;
import com.nicko.verapay.repository.LedgerEntryRepository;
import com.nicko.verapay.repository.TransactionRepository;
import com.nicko.verapay.repository.WalletRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MpesaWebhookControllerTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private EmailService emailService;
    @Mock private WebhookTokenValidator webhookTokenValidator;
    @Mock private HttpServletRequest httpServletRequest;

    @InjectMocks
    private MpesaWebhookController mpesaWebhookController;

    private User user;
    private Wallet wallet;
    private Transaction pendingTransaction;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("user@e.com");
        user.setFullName("Test User");

        wallet = new Wallet();
        wallet.setId(1L);
        wallet.setBalance(new BigDecimal("1000.00"));
        wallet.setCurrency("KES");
        wallet.setOwner(user);

        pendingTransaction = new Transaction();
        pendingTransaction.setStatus(ApplicationConstants.TRANSACTION_STATUS_PENDING);
        pendingTransaction.setAmount(new BigDecimal("200.00"));
        pendingTransaction.setTransactionRef("tx-ref-001");
        pendingTransaction.setType(ApplicationConstants.TRANSACTION_TYPE_DEPOSIT);
        pendingTransaction.setToWallet(wallet);
        pendingTransaction.setFromWallet(wallet);
    }

    // ── Helpers ───────────────────────────────────────────────

    private void givenValidWebhookToken() {
        when(webhookTokenValidator.isValid(httpServletRequest)).thenReturn(true);
    }

    private void givenInvalidWebhookToken() {
        when(webhookTokenValidator.isValid(httpServletRequest)).thenReturn(false);
    }

    private StkCallbackDto buildStkCallback(String checkoutRequestId, int resultCode) {
        var stkCallback = mock(StkCallbackDto.StkCallback.class);
        lenient().when(stkCallback.checkoutRequestID()).thenReturn(checkoutRequestId);
        lenient().when(stkCallback.resultCode()).thenReturn(resultCode);

        var body = mock(StkCallbackDto.Body.class);
        lenient().when(body.stkCallback()).thenReturn(stkCallback);

        var dto = mock(StkCallbackDto.class);
        lenient().when(dto.body()).thenReturn(body);
        return dto;
    }

    private B2CResultDto buildB2CResult(String originatorConversationId, int resultCode, String resultDesc) {
        var result = mock(B2CResultDto.Result.class);
        lenient().when(result.originatorConversationID()).thenReturn(originatorConversationId);
        lenient().when(result.resultCode()).thenReturn(resultCode);
        lenient().when(result.resultDesc()).thenReturn(resultDesc);

        var dto = mock(B2CResultDto.class);
        lenient().when(dto.result()).thenReturn(result);
        return dto;
    }

    // ── handleStkCallback() ───────────────────────────────────

    @Test
    @DisplayName("STK callback: returns 403 when webhook token is invalid")
    void stkCallback_InvalidToken_Returns403() {
        givenInvalidWebhookToken();
        StkCallbackDto callback = buildStkCallback("checkout-123", 0);

        ResponseEntity<?> response = mpesaWebhookController.handleStkCallback(callback, httpServletRequest);

        assertEquals(403, response.getStatusCode().value());
        verify(transactionRepository, never()).findByCheckoutRequestId(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("STK callback: credits wallet, saves transaction as SUCCESS, creates CREDIT ledger entry and sends email on resultCode 0")
    void stkCallback_Success_CreditsWalletAndSendsEmail() {
        givenValidWebhookToken();
        StkCallbackDto callback = buildStkCallback("checkout-123", 0);

        when(transactionRepository.findByCheckoutRequestId("checkout-123"))
                .thenReturn(Optional.of(pendingTransaction));

        ResponseEntity<?> response = mpesaWebhookController.handleStkCallback(callback, httpServletRequest);

        assertEquals(200, response.getStatusCode().value());

        // Balance credited: 1000 + 200 = 1200
        assertEquals(new BigDecimal("1200.00"), wallet.getBalance());
        verify(walletRepository).save(wallet);

        // Transaction marked SUCCESS
        assertEquals(ApplicationConstants.TRANSACTION_STATUS_SUCCESS, pendingTransaction.getStatus());
        verify(transactionRepository).save(pendingTransaction);

        // CREDIT ledger entry created with correct balanceAfter
        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(ledgerCaptor.capture());
        LedgerEntry entry = ledgerCaptor.getValue();
        assertEquals(ApplicationConstants.LEDGER_CREDIT, entry.getEntryType());
        assertEquals(new BigDecimal("1200.00"), entry.getBalanceAfter());
        assertEquals(new BigDecimal("200.00"), entry.getAmount());

        // Deposit confirmation email sent
        verify(emailService).sendDepositConfirmation(
                eq("user@e.com"),
                eq("Test User"),
                eq(new BigDecimal("200.00")),
                eq(new BigDecimal("1200.00")),
                eq("tx-ref-001"));
    }

    @Test
    @DisplayName("STK callback: marks transaction FAILED and does NOT credit wallet on non-zero resultCode")
    void stkCallback_Failed_MarksTransactionFailedWithoutCreditingWallet() {
        givenValidWebhookToken();
        StkCallbackDto callback = buildStkCallback("checkout-123", 1032); // user cancelled

        when(transactionRepository.findByCheckoutRequestId("checkout-123"))
                .thenReturn(Optional.of(pendingTransaction));

        ResponseEntity<?> response = mpesaWebhookController.handleStkCallback(callback, httpServletRequest);

        assertEquals(200, response.getStatusCode().value());

        // Balance must be unchanged
        assertEquals(new BigDecimal("1000.00"), wallet.getBalance());
        verify(walletRepository, never()).save(any());

        // Transaction marked FAILED
        assertEquals(ApplicationConstants.TRANSACTION_STATUS_FAILED, pendingTransaction.getStatus());
        verify(transactionRepository).save(pendingTransaction);

        // No ledger entry, no email
        verify(ledgerEntryRepository, never()).save(any());
        verify(emailService, never()).sendDepositConfirmation(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("STK callback: skips processing and returns 200 when transaction already processed")
    void stkCallback_AlreadyProcessed_SkipsWithoutSideEffects() {
        givenValidWebhookToken();
        StkCallbackDto callback = buildStkCallback("checkout-123", 0);

        pendingTransaction.setStatus(ApplicationConstants.TRANSACTION_STATUS_SUCCESS);
        when(transactionRepository.findByCheckoutRequestId("checkout-123"))
                .thenReturn(Optional.of(pendingTransaction));

        ResponseEntity<?> response = mpesaWebhookController.handleStkCallback(callback, httpServletRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
        verify(emailService, never()).sendDepositConfirmation(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("STK callback: throws RuntimeException when transaction not found by checkoutRequestId")
    void stkCallback_TransactionNotFound_Throws() {
        givenValidWebhookToken();
        StkCallbackDto callback = buildStkCallback("unknown-checkout", 0);

        when(transactionRepository.findByCheckoutRequestId("unknown-checkout"))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> mpesaWebhookController.handleStkCallback(callback, httpServletRequest));

        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    // ── handleB2CResult() ─────────────────────────────────────

    @Test
    @DisplayName("B2C result: returns 403 when webhook token is invalid")
    void b2cResult_InvalidToken_Returns403() {
        givenInvalidWebhookToken();
        B2CResultDto result = buildB2CResult("tx-ref-001", 0, "Success");

        ResponseEntity<?> response = mpesaWebhookController.handleB2CResult(result, httpServletRequest);

        assertEquals(403, response.getStatusCode().value());
        verify(transactionRepository, never()).findByTransactionRef(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("B2C result: marks transaction SUCCESS and sends confirmation email on resultCode 0")
    void b2cResult_Success_MarksSuccessAndSendsEmail() {
        givenValidWebhookToken();
        pendingTransaction.setType(ApplicationConstants.TRANSACTION_TYPE_WITHDRAW);
        B2CResultDto result = buildB2CResult("tx-ref-001", 0, "Success");

        when(transactionRepository.findByTransactionRef("tx-ref-001"))
                .thenReturn(Optional.of(pendingTransaction));

        ResponseEntity<?> response = mpesaWebhookController.handleB2CResult(result, httpServletRequest);

        assertEquals(200, response.getStatusCode().value());

        // Balance must be UNCHANGED — debit already applied at withdrawal initiation
        assertEquals(new BigDecimal("1000.00"), wallet.getBalance());
        verify(walletRepository, never()).save(any());

        // Transaction marked SUCCESS
        assertEquals(ApplicationConstants.TRANSACTION_STATUS_SUCCESS, pendingTransaction.getStatus());
        verify(transactionRepository).save(pendingTransaction);

        // No compensating ledger entry on success
        verify(ledgerEntryRepository, never()).save(any());

        // Withdrawal confirmation email sent
        verify(emailService).sendWithdrawalConfirmation(
                eq("user@e.com"),
                eq("Test User"),
                eq(new BigDecimal("200.00")),
                eq(new BigDecimal("1000.00")),
                eq("tx-ref-001"));
    }

    @Test
    @DisplayName("B2C result: reverts debit, creates compensating CREDIT ledger entry and sends failure email on non-zero resultCode")
    void b2cResult_Failed_RevertsDebitAndSendsFailureEmail() {
        givenValidWebhookToken();
        pendingTransaction.setType(ApplicationConstants.TRANSACTION_TYPE_WITHDRAW);
        B2CResultDto result = buildB2CResult("tx-ref-001", 2001, "Insufficient funds at M-Pesa");

        when(transactionRepository.findByTransactionRef("tx-ref-001"))
                .thenReturn(Optional.of(pendingTransaction));

        ResponseEntity<?> response = mpesaWebhookController.handleB2CResult(result, httpServletRequest);

        assertEquals(200, response.getStatusCode().value());

        // Balance must be reverted: 1000 + 200 = 1200
        assertEquals(new BigDecimal("1200.00"), wallet.getBalance());
        verify(walletRepository).save(wallet);

        // Transaction marked FAILED
        assertEquals(ApplicationConstants.TRANSACTION_STATUS_FAILED, pendingTransaction.getStatus());
        verify(transactionRepository).save(pendingTransaction);

        // Compensating CREDIT ledger entry saved
        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(ledgerCaptor.capture());
        LedgerEntry entry = ledgerCaptor.getValue();
        assertEquals(ApplicationConstants.LEDGER_CREDIT, entry.getEntryType());
        assertEquals(new BigDecimal("1200.00"), entry.getBalanceAfter());
        assertEquals(new BigDecimal("200.00"), entry.getAmount());

        // Failure email sent with correct reason
        verify(emailService).sendTransactionFailedEmail(
                eq("user@e.com"),
                eq("Test User"),
                eq(ApplicationConstants.TRANSACTION_TYPE_WITHDRAW),
                eq(new BigDecimal("200.00")),
                eq("tx-ref-001"),
                eq("Insufficient funds at M-Pesa"));
    }

    @Test
    @DisplayName("B2C result: skips processing and returns 200 when transaction already processed")
    void b2cResult_AlreadyProcessed_SkipsWithoutSideEffects() {
        givenValidWebhookToken();
        pendingTransaction.setStatus(ApplicationConstants.TRANSACTION_STATUS_SUCCESS);
        B2CResultDto result = buildB2CResult("tx-ref-001", 0, "Success");

        when(transactionRepository.findByTransactionRef("tx-ref-001"))
                .thenReturn(Optional.of(pendingTransaction));

        ResponseEntity<?> response = mpesaWebhookController.handleB2CResult(result, httpServletRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
        verify(emailService, never()).sendWithdrawalConfirmation(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("B2C result: throws RuntimeException when transaction not found by transactionRef")
    void b2cResult_TransactionNotFound_Throws() {
        givenValidWebhookToken();
        B2CResultDto result = buildB2CResult("unknown-ref", 0, "Success");

        when(transactionRepository.findByTransactionRef("unknown-ref"))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> mpesaWebhookController.handleB2CResult(result, httpServletRequest));

        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    // ── handleB2CTimeout() ────────────────────────────────────

    @Test
    @DisplayName("B2C timeout: returns 403 when webhook token is invalid")
    void b2cTimeout_InvalidToken_Returns403() {
        givenInvalidWebhookToken();
        B2CResultDto result = buildB2CResult("tx-ref-001", 0, "Timeout");

        ResponseEntity<?> response = mpesaWebhookController.handleB2CTimeout(result, httpServletRequest);

        assertEquals(403, response.getStatusCode().value());
        verify(transactionRepository, never()).findByTransactionRef(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("B2C timeout: marks FAILED, reverts debit, creates compensating CREDIT ledger entry and sends failure email")
    void b2cTimeout_Success_RevertsDebitAndSendsFailureEmail() {
        givenValidWebhookToken();
        pendingTransaction.setType(ApplicationConstants.TRANSACTION_TYPE_WITHDRAW);
        B2CResultDto result = buildB2CResult("tx-ref-001", 0, "Timeout");

        when(transactionRepository.findByTransactionRef("tx-ref-001"))
                .thenReturn(Optional.of(pendingTransaction));

        ResponseEntity<?> response = mpesaWebhookController.handleB2CTimeout(result, httpServletRequest);

        assertEquals(200, response.getStatusCode().value());

        // Balance reverted: 1000 + 200 = 1200
        assertEquals(new BigDecimal("1200.00"), wallet.getBalance());
        verify(walletRepository).save(wallet);

        // Transaction marked FAILED
        assertEquals(ApplicationConstants.TRANSACTION_STATUS_FAILED, pendingTransaction.getStatus());
        verify(transactionRepository).save(pendingTransaction);

        // Compensating CREDIT ledger entry saved
        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(ledgerCaptor.capture());
        LedgerEntry entry = ledgerCaptor.getValue();
        assertEquals(ApplicationConstants.LEDGER_CREDIT, entry.getEntryType());
        assertEquals(new BigDecimal("1200.00"), entry.getBalanceAfter());
        assertEquals(new BigDecimal("200.00"), entry.getAmount());

        // Failure email sent with "Transaction timed out" reason
        verify(emailService).sendTransactionFailedEmail(
                eq("user@e.com"),
                eq("Test User"),
                eq(ApplicationConstants.TRANSACTION_TYPE_WITHDRAW),
                eq(new BigDecimal("200.00")),
                eq("tx-ref-001"),
                eq("Transaction timed out"));
    }

    @Test
    @DisplayName("B2C timeout: skips processing and returns 200 when transaction already processed")
    void b2cTimeout_AlreadyProcessed_SkipsWithoutSideEffects() {
        givenValidWebhookToken();
        pendingTransaction.setStatus(ApplicationConstants.TRANSACTION_STATUS_FAILED);
        B2CResultDto result = buildB2CResult("tx-ref-001", 0, "Timeout");

        when(transactionRepository.findByTransactionRef("tx-ref-001"))
                .thenReturn(Optional.of(pendingTransaction));

        ResponseEntity<?> response = mpesaWebhookController.handleB2CTimeout(result, httpServletRequest);

        assertEquals(200, response.getStatusCode().value());
        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
        verify(emailService, never()).sendTransactionFailedEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("B2C timeout: throws RuntimeException when transaction not found by transactionRef")
    void b2cTimeout_TransactionNotFound_Throws() {
        givenValidWebhookToken();
        B2CResultDto result = buildB2CResult("unknown-ref", 0, "Timeout");

        when(transactionRepository.findByTransactionRef("unknown-ref"))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> mpesaWebhookController.handleB2CTimeout(result, httpServletRequest));

        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }
}