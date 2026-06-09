package com.nicko.verapay.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final ProxyManager<String> proxyManager;

    // Login — 5 attempts per minute per IP
    private Supplier<BucketConfiguration> loginBucketConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillIntervally(5, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    // Step-Up PIN — 3 attempts per 5 minutes per user
    private Supplier<BucketConfiguration> stepUpBucketConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(3)
                        .refillIntervally(3, Duration.ofMinutes(5))
                        .build())
                .build();
    }

    // Register — 3 attempts per hour per IP
    private Supplier<BucketConfiguration> registerBucketConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(3)
                        .refillIntervally(3, Duration.ofHours(1))
                        .build())
                .build();
    }

    // Refresh Token — 10 attempts per minute per IP
    private Supplier<BucketConfiguration> refreshBucketConfig() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(10)
                        .refillIntervally(10, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    public ConsumptionProbe tryConsumeLogin(String ipAddress) {
        String key = "rate_limit:login:" + ipAddress;
        return proxyManager.builder()
                .build(key, loginBucketConfig())
                .tryConsumeAndReturnRemaining(1);
    }

    public ConsumptionProbe tryConsumeStepUp(String userEmail) {
        String key = "rate_limit:stepup:" + userEmail;
        return proxyManager.builder()
                .build(key, stepUpBucketConfig())
                .tryConsumeAndReturnRemaining(1);
    }

    public ConsumptionProbe tryConsumeRegister(String ipAddress) {
        String key = "rate_limit:register:" + ipAddress;
        return proxyManager.builder()
                .build(key, registerBucketConfig())
                .tryConsumeAndReturnRemaining(1);
    }

    public ConsumptionProbe tryConsumeRefresh(String ipAddress) {
        String key = "rate_limit:refresh:" + ipAddress;
        return proxyManager.builder()
                .build(key, refreshBucketConfig())
                .tryConsumeAndReturnRemaining(1);
    }
}