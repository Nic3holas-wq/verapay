package com.nicko.verapay.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@Getter
public class MpesaConfig {

    @Value("${mpesa.consumer.key}")
    private String consumerKey;

    @Value("${mpesa.consumer.secret}")
    private String consumerSecret;

    @Value("${mpesa.business.shortcode}")
    private String businessShortcode;

    @Value("${mpesa.passkey}")
    private String passkey;

    @Value("${mpesa.initiator.name}")
    private String initiatorName;

    @Value("${mpesa.security.credential}")
    private String securityCredential;

    @Value("${mpesa.b2c.shortcode}")
    private String b2cShortcode;

    @Value("${mpesa.base.url}")
    private String baseUrl;

    @Value("${mpesa.callback.base.url}")
    private String callbackBaseUrl;

    @Value("${mpesa.allowed.ips}")
    private String allowedIpsRaw;

    @Value("${mpesa.webhook.token}")
    private String webhookToken;

    public Set<String> getAllowedIps() {
        return Arrays.stream(allowedIpsRaw.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }
}