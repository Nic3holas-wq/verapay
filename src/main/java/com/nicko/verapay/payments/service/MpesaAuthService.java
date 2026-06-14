package com.nicko.verapay.payments.service;

import com.nicko.verapay.config.MpesaConfig;
import com.nicko.verapay.dto.mpesa.MpesaAuthResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class MpesaAuthService {

    private final MpesaConfig mpesaConfig;
    private final RestClient restClient;

    private static final long EXPIRY_BUFFER_SECONDS = 300;

    private volatile String cachedToken;
    private volatile Instant tokenExpiryTime;

    public synchronized String getAccessToken() {

        // Return cached token if still valid
        if (cachedToken != null && tokenExpiryTime != null
                && Instant.now().isBefore(tokenExpiryTime)) {
            log.debug("Using cached Mpesa access token (valid until {})", tokenExpiryTime);
            return cachedToken;
        }

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

        // 3. Cache token, subtracting a 5-minute buffer from its real TTL
        long expiresInSeconds = parseExpiresIn(response.expiresIn());
        long effectiveTtl = Math.max(expiresInSeconds - EXPIRY_BUFFER_SECONDS, 0);

        this.cachedToken = response.accessToken();
        this.tokenExpiryTime = Instant.now().plusSeconds(effectiveTtl);

        log.info("Mpesa access token obtained successfully (cached until {})", tokenExpiryTime);
        return cachedToken;
    }

    private long parseExpiresIn(String expiresIn) {
        try {
            return Long.parseLong(expiresIn);
        } catch (NumberFormatException | NullPointerException e) {
            log.warn("Could not parse expires_in '{}', defaulting to 3600s", expiresIn);
            return 3600;
        }
    }
}