package com.nicko.verapay.payments.service;

import com.nicko.verapay.config.MpesaConfig;
import com.nicko.verapay.dto.mpesa.B2CRequestDto;
import com.nicko.verapay.dto.mpesa.B2CResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class MpesaB2CService {

    private final MpesaConfig mpesaConfig;
    private final MpesaAuthService mpesaAuthService;
    private final RestClient restClient;

    public B2CResponseDto initiateB2C(
            String phoneNumber,
            BigDecimal amount,
            String transactionRef) {

        // 1. Format phone number
        String formattedPhone = formatPhoneNumber(phoneNumber);

        // 2. Build B2C request
        B2CRequestDto request = new B2CRequestDto(
                transactionRef,
                mpesaConfig.getInitiatorName(),
                mpesaConfig.getSecurityCredential(),
                "BusinessPayment",
                String.valueOf(amount.intValue()),
                mpesaConfig.getB2cShortcode(),
                formattedPhone,
                "VeraPay Withdrawal",
                mpesaConfig.getCallbackBaseUrl()
                        + "/api/webhooks/mpesa/b2c/timeout?token="
                        + mpesaConfig.getWebhookToken(),
                mpesaConfig.getCallbackBaseUrl()
                        + "/api/webhooks/mpesa/b2c/result?token="
                        + mpesaConfig.getWebhookToken(),
                "Withdrawal"
        );

        // 3. Get access token
        String accessToken = mpesaAuthService.getAccessToken();

        // 4. Send B2C request
        B2CResponseDto response = restClient.post()
                .uri(mpesaConfig.getBaseUrl()
                        + "/mpesa/b2c/v3/paymentrequest")
                .header("Authorization", "Bearer " + accessToken)
                .body(request)
                .retrieve()
                .body(B2CResponseDto.class);

        log.info("B2C Response: ResponseCode={} ResponseDescription={} ConversationID={} OriginatorConversationID={}",
                response.responseCode(),
                response.responseDescription(),
                response.conversationID(),
                response.originatorConversationID());
        
        log.info("B2C initiated for phone: {} amount: {}",
                formattedPhone, amount);
        return response;
    }

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
