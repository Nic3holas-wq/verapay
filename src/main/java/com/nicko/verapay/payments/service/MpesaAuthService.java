package com.nicko.verapay.payments.service;

import com.nicko.verapay.config.MpesaConfig;
import com.nicko.verapay.dto.mpesa.MpesaAuthResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class MpesaAuthService {

    private final MpesaConfig mpesaConfig;
    private final RestClient restClient;

    public String getAccessToken() {
        // 1. Encode consumer key and secret
        String credentials = mpesaConfig.getConsumerKey()
                + ":" + mpesaConfig.getConsumerSecret();
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes());

        // 2. Call Mpesa OAuth endpoint
        MpesaAuthResponseDto response = restClient.get()
                .uri(mpesaConfig.getBaseUrl() +
                        "/oauth/v1/generate?grant_type=client_credentials")
                .header("Authorization", "Basic " + encodedCredentials)
                .retrieve()
                .body(MpesaAuthResponseDto.class);

        if (response == null || response.accessToken() == null) {
            throw new RuntimeException("Failed to get Mpesa access token");
        }

        log.info("Mpesa access token obtained successfully");
        return response.accessToken();
    }
}
