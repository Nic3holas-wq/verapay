package com.nicko.verapay.payments.security;

import com.nicko.verapay.config.MpesaConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebhookTokenValidator {

    private final MpesaConfig mpesaConfig;

    public boolean isValid(HttpServletRequest request) {
        String token = request.getParameter("token");
        return token != null && token.equals(mpesaConfig.getWebhookToken());
    }
}
