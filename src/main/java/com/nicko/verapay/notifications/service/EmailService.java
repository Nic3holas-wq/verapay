package com.nicko.verapay.notifications.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    // Send email asynchronously — doesn't block transaction
    @Async
    public void sendDepositConfirmation(
            String toEmail,
            String fullName,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String transactionRef) {

        String subject = "Deposit Successful - " + transactionRef;
        String body = buildDepositEmail(
                fullName, amount, balanceAfter, transactionRef);
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendWithdrawalConfirmation(
            String toEmail,
            String fullName,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String transactionRef) {

        String subject = "Withdrawal Successful - " + transactionRef;
        String body = buildWithdrawalEmail(
                fullName, amount, balanceAfter, transactionRef);
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendTransferSenderConfirmation(
            String toEmail,
            String fullName,
            String recipientEmail,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String transactionRef) {

        String subject = "Confirmed Transfer Successful - " + transactionRef;
        String body = buildTransferSenderEmail(
                fullName, recipientEmail, amount, balanceAfter, transactionRef);
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendTransferRecipientConfirmation(
            String toEmail,
            String fullName,
            String senderEmail,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String transactionRef) {

        String subject = "You received money - " + transactionRef;
        String body = buildTransferRecipientEmail(
                fullName, senderEmail, amount, balanceAfter, transactionRef);
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendTransactionFailedEmail(
            String toEmail,
            String fullName,
            String transactionType,
            BigDecimal amount,
            String transactionRef,
            String reason) {

        String subject = "Transaction Failed - " + transactionRef;
        String body = buildFailedEmail(
                fullName, transactionType, amount, transactionRef, reason);
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendTransactionReversalEmail(
            String toEmail,
            String fullName,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String originalTransactionRef,
            String reversalTransactionRef,
            String reason) {

        String subject = "⚠️ Transaction Reversed - " + originalTransactionRef;
        String body = buildReversalEmail(
                fullName, amount, balanceAfter,
                originalTransactionRef, reversalTransactionRef, reason);
        sendEmail(toEmail, subject, body);
    }

    private String buildReversalEmail(
            String fullName,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String originalTransactionRef,
            String reversalTransactionRef,
            String reason) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background-color: #00B272; padding: 20px; text-align: center;">
                    <h1 style="color: white; margin: 0;">VeraPay</h1>
                </div>
                <div style="padding: 30px; background-color: #f9f9f9;">
                    <h2 style="color: #e67e22;">Transaction Reversed ⚠️</h2>
                    <p>Dear <strong>%s</strong>,</p>
                    <p>A transaction on your account has been reversed by VeraPay support.</p>
                    <div style="background-color: white; padding: 20px; border-radius: 8px; margin: 20px 0;">
                        <table width="100%%">
                            <tr>
                                <td style="color: #666;">Amount Reversed:</td>
                                <td style="text-align: right; font-weight: bold;">
                                    KES %s
                                </td>
                            </tr>
                            <tr>
                                <td style="color: #666;">New Balance:</td>
                                <td style="text-align: right; font-weight: bold;">
                                    KES %s
                                </td>
                            </tr>
                            <tr>
                                <td style="color: #666;">Original Transaction:</td>
                                <td style="text-align: right; color: #666;">%s</td>
                            </tr>
                            <tr>
                                <td style="color: #666;">Reversal Reference:</td>
                                <td style="text-align: right; color: #666;">%s</td>
                            </tr>
                            <tr>
                                <td style="color: #666;">Reason:</td>
                                <td style="text-align: right; color: #666;">%s</td>
                            </tr>
                            <tr>
                                <td style="color: #666;">Date:</td>
                                <td style="text-align: right; color: #666;">%s</td>
                            </tr>
                        </table>
                    </div>
                    <p style="color: #666; font-size: 12px;">
                        If you believe this is an error, please contact support.
                    </p>
                </div>
                <div style="background-color: #333; padding: 15px; text-align: center;">
                    <p style="color: #999; font-size: 12px; margin: 0;">
                        © 2026 VeraPay. All rights reserved.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(
                fullName,
                amount,
                balanceAfter,
                originalTransactionRef,
                reversalTransactionRef,
                reason,
                getCurrentDateTime()
        );
    }

    // Core send method
    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML
            helper.setFrom("noreply@verapay.com", "VeraPay");
            mailSender.send(message);
            log.info("Email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to: {} reason: {}",
                    to, e.getMessage());
        }
    }

    // Email templates
    private String buildDepositEmail(
            String fullName,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String transactionRef) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #00B272; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">VeraPay</h1>
                    </div>
                    <div style="padding: 30px; background-color: #f9f9f9;">
                        <h2 style="color: #333;">Deposit Successful</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Confirmed Your deposit has been processed successfully.</p>
                        <div style="background-color: white; padding: 20px; border-radius: 8px; margin: 20px 0;">
                            <table width="100%%">
                                <tr>
                                    <td style="color: #666;">Amount Deposited:</td>
                                    <td style="text-align: right; font-weight: bold; color: #00B272;">
                                        KES %s
                                    </td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">New Balance:</td>
                                    <td style="text-align: right; font-weight: bold;">
                                        KES %s
                                    </td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Transaction Ref:</td>
                                    <td style="text-align: right; color: #666;">%s</td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Date:</td>
                                    <td style="text-align: right; color: #666;">%s</td>
                                </tr>
                            </table>
                        </div>
                        <p style="color: #666; font-size: 12px;">
                            If you did not initiate this transaction, 
                            please contact support immediately.
                        </p>
                    </div>
                    <div style="background-color: #333; padding: 15px; text-align: center;">
                        <p style="color: #999; font-size: 12px; margin: 0;">
                            © 2026 VeraPay. All rights reserved.
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                fullName,
                amount,
                balanceAfter,
                transactionRef,
                getCurrentDateTime()
        );
    }

    private String buildWithdrawalEmail(
            String fullName,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String transactionRef) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #00B272; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">VeraPay</h1>
                    </div>
                    <div style="padding: 30px; background-color: #f9f9f9;">
                        <h2 style="color: #333;">Withdrawal Successful</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Confirmed Your withdrawal has been processed successfully.</p>
                        <div style="background-color: white; padding: 20px; border-radius: 8px; margin: 20px 0;">
                            <table width="100%%">
                                <tr>
                                    <td style="color: #666;">Amount Withdrawn:</td>
                                    <td style="text-align: right; font-weight: bold; color: #e74c3c;">
                                        KES %s
                                    </td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Remaining Balance:</td>
                                    <td style="text-align: right; font-weight: bold;">
                                        KES %s
                                    </td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Transaction Ref:</td>
                                    <td style="text-align: right; color: #666;">%s</td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Date:</td>
                                    <td style="text-align: right; color: #666;">%s</td>
                                </tr>
                            </table>
                        </div>
                        <p style="color: #666; font-size: 12px;">
                            If you did not initiate this transaction,
                            please contact support immediately.
                        </p>
                    </div>
                    <div style="background-color: #333; padding: 15px; text-align: center;">
                        <p style="color: #999; font-size: 12px; margin: 0;">
                            © 2026 VeraPay. All rights reserved.
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                fullName,
                amount,
                balanceAfter,
                transactionRef,
                getCurrentDateTime()
        );
    }

    private String buildTransferSenderEmail(
            String fullName,
            String recipientEmail,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String transactionRef) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #00B272; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">VeraPay</h1>
                    </div>
                    <div style="padding: 30px; background-color: #f9f9f9;">
                        <h2 style="color: #333;">Transfer Successful</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Your transfer has been processed successfully.</p>
                        <div style="background-color: white; padding: 20px; border-radius: 8px; margin: 20px 0;">
                            <table width="100%%">
                                <tr>
                                    <td style="color: #666;">Amount Sent:</td>
                                    <td style="text-align: right; font-weight: bold; color: #e74c3c;">
                                        KES %s
                                    </td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Sent To:</td>
                                    <td style="text-align: right; color: #666;">%s</td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Remaining Balance:</td>
                                    <td style="text-align: right; font-weight: bold;">
                                        KES %s
                                    </td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Transaction Ref:</td>
                                    <td style="text-align: right; color: #666;">%s</td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Date:</td>
                                    <td style="text-align: right; color: #666;">%s</td>
                                </tr>
                            </table>
                        </div>
                        <p style="color: #666; font-size: 12px;">
                            If you did not initiate this transaction,
                            please contact support immediately.
                        </p>
                    </div>
                    <div style="background-color: #333; padding: 15px; text-align: center;">
                        <p style="color: #999; font-size: 12px; margin: 0;">
                            © 2026 VeraPay. All rights reserved.
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                fullName,
                amount,
                recipientEmail,
                balanceAfter,
                transactionRef,
                getCurrentDateTime()
        );
    }

    private String buildTransferRecipientEmail(
            String fullName,
            String senderEmail,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String transactionRef) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #00B272; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">VeraPay</h1>
                    </div>
                    <div style="padding: 30px; background-color: #f9f9f9;">
                        <h2 style="color: #333;">Money Received</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Confirmed You have received money in your VeraPay wallet.</p>
                        <div style="background-color: white; padding: 20px; border-radius: 8px; margin: 20px 0;">
                            <table width="100%%">
                                <tr>
                                    <td style="color: #666;">Amount Received:</td>
                                    <td style="text-align: right; font-weight: bold; color: #00B272;">
                                        KES %s
                                    </td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Sent By:</td>
                                    <td style="text-align: right; color: #666;">%s</td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">New Balance:</td>
                                    <td style="text-align: right; font-weight: bold;">
                                        KES %s
                                    </td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Transaction Ref:</td>
                                    <td style="text-align: right; color: #666;">%s</td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Date:</td>
                                    <td style="text-align: right; color: #666;">%s</td>
                                </tr>
                            </table>
                        </div>
                    </div>
                    <div style="background-color: #333; padding: 15px; text-align: center;">
                        <p style="color: #999; font-size: 12px; margin: 0;">
                            © 2026 VeraPay. All rights reserved.
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                fullName,
                amount,
                senderEmail,
                balanceAfter,
                transactionRef,
                getCurrentDateTime()
        );
    }

    private String buildFailedEmail(
            String fullName,
            String transactionType,
            BigDecimal amount,
            String transactionRef,
            String reason) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: #00B272; padding: 20px; text-align: center;">
                        <h1 style="color: white; margin: 0;">VeraPay</h1>
                    </div>
                    <div style="padding: 30px; background-color: #f9f9f9;">
                        <h2 style="color: #e74c3c;">Transaction Failed</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Unfortunately your transaction could not be processed.</p>
                        <div style="background-color: white; padding: 20px; border-radius: 8px; margin: 20px 0;">
                            <table width="100%%">
                                <tr>
                                    <td style="color: #666;">Transaction Type:</td>
                                    <td style="text-align: right; color: #666;">%s</td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Amount:</td>
                                    <td style="text-align: right; font-weight: bold;">
                                        KES %s
                                    </td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Reason:</td>
                                    <td style="text-align: right; color: #e74c3c;">%s</td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Transaction Ref:</td>
                                    <td style="text-align: right; color: #666;">%s</td>
                                </tr>
                                <tr>
                                    <td style="color: #666;">Date:</td>
                                    <td style="text-align: right; color: #666;">%s</td>
                                </tr>
                            </table>
                        </div>
                        <p style="color: #666; font-size: 12px;">
                            Your account balance has been restored.
                            Please try again or contact support.
                        </p>
                    </div>
                    <div style="background-color: #333; padding: 15px; text-align: center;">
                        <p style="color: #999; font-size: 12px; margin: 0;">
                            © 2026 VeraPay. All rights reserved.
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                fullName,
                transactionType,
                amount,
                reason,
                transactionRef,
                getCurrentDateTime()
        );
    }

    private String getCurrentDateTime() {
        return DateTimeFormatter
                .ofPattern("dd MMM yyyy HH:mm:ss")
                .withZone(ZoneId.of("Africa/Nairobi"))
                .format(Instant.now());
    }
}
