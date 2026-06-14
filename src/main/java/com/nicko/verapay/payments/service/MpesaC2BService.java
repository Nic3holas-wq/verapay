package com.nicko.verapay.payments.service;

import com.nicko.verapay.config.MpesaConfig;
import com.nicko.verapay.dto.mpesa.StkPushRequestDto;
import com.nicko.verapay.dto.mpesa.StkPushResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class MpesaC2BService {

    private final MpesaConfig mpesaConfig;
    private final MpesaAuthService mpesaAuthService;
    private final RestClient restClient;

    public StkPushResponseDto initiateStkPush(
            String phoneNumber,
            BigDecimal amount,
            String idempotencyKey) {

        // 1. Generate timestamp
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        // 2. Generate password
        // Password = Base64(ShortCode + Passkey + Timestamp)
        String rawPassword = mpesaConfig.getBusinessShortcode()
                + mpesaConfig.getPasskey()
                + timestamp;
        String password = Base64.getEncoder()
                .encodeToString(rawPassword.getBytes());

        // 3. Format phone number (254XXXXXXXXX)
        String formattedPhone = formatPhoneNumber(phoneNumber);

        // 4. Build STK Push request
        StkPushRequestDto request = new StkPushRequestDto(
                mpesaConfig.getBusinessShortcode(),
                password,
                timestamp,
                "CustomerPayBillOnline",
                String.valueOf(amount.intValue()),
                formattedPhone,
                mpesaConfig.getBusinessShortcode(),
                formattedPhone,
                mpesaConfig.getCallbackBaseUrl()
                        + "/api/webhooks/mpesa/stk/callback?token="
                        + mpesaConfig.getWebhookToken(),
                idempotencyKey,
                "VeraPay Deposit"
        );

        // 5. Get access token
        String accessToken = mpesaAuthService.getAccessToken();

        // 6. Send STK Push
        StkPushResponseDto response = restClient.post()
                .uri(mpesaConfig.getBaseUrl()
                        + "/mpesa/stkpush/v1/processrequest")
                .header("Authorization", "Bearer " + accessToken)
                .body(request)
                .retrieve()
                .body(StkPushResponseDto.class);

        log.info("STK Push initiated for phone: {} amount: {}",
                formattedPhone, amount);
        return response;
    }

    // Format phone number to 254XXXXXXXXX
    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber.startsWith("0")) {
            return "254" + phoneNumber.substring(1);
        }
        if (phoneNumber.startsWith("+")) {
            return phoneNumber.substring(1);
        }
        return phoneNumber;
    }
}
