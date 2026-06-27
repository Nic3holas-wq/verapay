package com.nicko.verapay.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicko.verapay.entity.Transaction;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.entity.Wallet;
import com.nicko.verapay.entity.fraud.KnownBadIp;
import com.nicko.verapay.entity.fraud.TransactionRiskLog;
import com.nicko.verapay.entity.fraud.UserLimit;
import com.nicko.verapay.repository.TransactionRepository;
import com.nicko.verapay.repository.WalletRepository;
import com.nicko.verapay.repository.fraud.KnownBadIpRepository;
import com.nicko.verapay.repository.fraud.TransactionRiskLogRepository;
import com.nicko.verapay.repository.fraud.UserLimitRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudPreventionService {

    private final KnownBadIpRepository knownBadIpRepository;
    private final TransactionRepository transactionRepository;
    private final UserLimitRepository userLimitRepository;
    private final TransactionRiskLogRepository transactionRiskLogRepository;
    private final WalletRepository walletRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public static class FraudCheckResult {
        public final boolean block;
        public final boolean suspicious;
        public final List<String> rulesTriggered;
        public final String clientIp;
        public final String deviceFingerprint;
        public final String locationGeo;
        public final String actionTaken;

        public FraudCheckResult(boolean block, boolean suspicious, List<String> rulesTriggered,
                                String clientIp, String deviceFingerprint, String locationGeo, String actionTaken) {
            this.block = block;
            this.suspicious = suspicious;
            this.rulesTriggered = rulesTriggered;
            this.clientIp = clientIp;
            this.deviceFingerprint = deviceFingerprint;
            this.locationGeo = locationGeo;
            this.actionTaken = actionTaken;
        }
    }

    public FraudCheckResult evaluate(User user, BigDecimal amount, String type) {
        List<String> rulesTriggered = new ArrayList<>();
        boolean block = false;
        boolean suspicious = false;
        String actionTaken = "ALLOW";

        // Get servlet request attributes
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = (attributes != null) ? attributes.getRequest() : null;

        String ipAddress = "127.0.0.1";
        String userAgent = "Unknown";
        String deviceFingerprint = "Unknown";
        String locationGeo = "Local Development, LAN";

        if (request != null) {
            ipAddress = resolveClientIp(request);
            userAgent = request.getHeader("User-Agent");
            if (userAgent == null) userAgent = "Unknown";
            deviceFingerprint = request.getHeader("X-Device-Fingerprint");
            if (deviceFingerprint == null || deviceFingerprint.trim().isEmpty()) {
                deviceFingerprint = UUID.nameUUIDFromBytes((userAgent + ipAddress).getBytes()).toString();
            }
            locationGeo = resolveLocationFromIp(ipAddress);
        }

        // Rule 3: Suspicious IP / Proxy Block
        if (knownBadIpRepository.existsByIpAddress(ipAddress)) {
            log.warn("Rule Triggered: SUSPICIOUS_IP_BLOCKED for IP: {}", ipAddress);
            rulesTriggered.add("SUSPICIOUS_IP_BLOCKED");
            block = true;
            actionTaken = "BLOCK";
            return new FraudCheckResult(block, suspicious, rulesTriggered, ipAddress, deviceFingerprint, locationGeo, actionTaken);
        }

        // Rule 1: Velocity Checks (attempts in last 5 minutes)
        Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
        long recentAttempts = transactionRepository.countAttemptsByIpOrDevice(ipAddress, deviceFingerprint, fiveMinutesAgo);
        if (recentAttempts >= 3) {
            log.warn("Rule Triggered: VELOCITY_LIMIT_EXCEEDED for IP: {} or Fingerprint: {}. Count: {}", ipAddress, deviceFingerprint, recentAttempts);
            rulesTriggered.add("VELOCITY_LIMIT_EXCEEDED");
            block = true;
            actionTaken = "BLOCK";
            return new FraudCheckResult(block, suspicious, rulesTriggered, ipAddress, deviceFingerprint, locationGeo, actionTaken);
        }

        // Rule 4: Limit Exhaustion (only for spending: transfers and withdrawals)
        if ("TRANSFER".equalsIgnoreCase(type) || "WITHDRAW".equalsIgnoreCase(type)) {
            UserLimit limit = userLimitRepository.findByUserId(user.getId())
                    .orElseGet(() -> {
                        UserLimit defaultLimit = new UserLimit();
                        defaultLimit.setUserId(user.getId());
                        return defaultLimit;
                    });

            Instant startOfToday = Instant.now().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant startOfThisMonth = Instant.now().atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();

            BigDecimal spendingToday = transactionRepository.sumSpendingSince(user.getId(), startOfToday);
            BigDecimal spendingThisMonth = transactionRepository.sumSpendingSince(user.getId(), startOfThisMonth);

            if (spendingToday.add(amount).compareTo(limit.getDailyLimit()) > 0) {
                log.warn("Rule Triggered: LIMIT_EXHAUSTION for User: {}. Today spending: {}, Limit: {}, Attempted: {}",
                        user.getId(), spendingToday, limit.getDailyLimit(), amount);
                rulesTriggered.add("LIMIT_EXHAUSTION");
                block = true;
                actionTaken = "DECLINE";
                return new FraudCheckResult(block, suspicious, rulesTriggered, ipAddress, deviceFingerprint, locationGeo, actionTaken);
            }

            if (spendingThisMonth.add(amount).compareTo(limit.getMonthlyLimit()) > 0) {
                log.warn("Rule Triggered: LIMIT_EXHAUSTION for User: {}. Month spending: {}, Limit: {}, Attempted: {}",
                        user.getId(), spendingThisMonth, limit.getMonthlyLimit(), amount);
                rulesTriggered.add("LIMIT_EXHAUSTION");
                block = true;
                actionTaken = "DECLINE";
                return new FraudCheckResult(block, suspicious, rulesTriggered, ipAddress, deviceFingerprint, locationGeo, actionTaken);
            }
        }

        // Rule 2: Geographical Velocity Anomaly
        List<Transaction> lastTxList = transactionRepository.findLastSuccessfulTransaction(user.getId(), PageRequest.of(0, 1));
        if (!lastTxList.isEmpty()) {
            Transaction lastTx = lastTxList.get(0);
            String previousLocation = lastTx.getLocationGeo();
            String currentCountry = extractCountry(locationGeo);
            String previousCountry = extractCountry(previousLocation);

            if (currentCountry != null && previousCountry != null && !currentCountry.equalsIgnoreCase(previousCountry)) {
                long minutesSinceLastTx = ChronoUnit.MINUTES.between(lastTx.getCreatedAt(), Instant.now());
                if (minutesSinceLastTx < 120) { // less than 2 hours
                    log.warn("Rule Triggered: GEOGRAPHICAL_VELOCITY_ANOMALY for User: {}. Prev country: {} ({}), Cur country: {} ({}) in {} minutes",
                            user.getId(), previousCountry, lastTx.getCreatedAt(), currentCountry, Instant.now(), minutesSinceLastTx);
                    rulesTriggered.add("GEOGRAPHICAL_VELOCITY_ANOMALY");
                    suspicious = true;
                    actionTaken = "FLAG_FOR_REVIEW";
                }
            }
        }

        return new FraudCheckResult(block, suspicious, rulesTriggered, ipAddress, deviceFingerprint, locationGeo, actionTaken);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logBlockedTransaction(User user, BigDecimal amount, String type, FraudCheckResult result) {
        Wallet wallet = walletRepository.findByOwnerEmail(user.getEmail()).orElse(null);

        Transaction transaction = new Transaction();
        transaction.setFromWallet(type.equals("DEPOSIT") ? null : wallet);
        transaction.setToWallet(wallet != null ? wallet : new Wallet());
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setStatus("FAILED");
        transaction.setIdempotencyKey("FAILED-" + UUID.randomUUID().toString());
        transaction.setTransactionRef("TXN-DECLINED-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        transaction.setTransactionCode("FAIL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        transaction.setIpAddress(result.clientIp);
        transaction.setDeviceFingerprint(result.deviceFingerprint);
        transaction.setLocationGeo(result.locationGeo);
        transaction.setIsSuspicious(true);
        transaction.setCreatedAt(Instant.now());
        transaction = transactionRepository.save(transaction);

        TransactionRiskLog riskLog = new TransactionRiskLog();
        riskLog.setTransactionId(transaction.getId());
        riskLog.setUserId(user.getId());
        riskLog.setRulesTriggerged(result.rulesTriggered.toArray(new String[0]));
        riskLog.setMlRiskScore(BigDecimal.ZERO);
        riskLog.setActionTaken(result.actionTaken);
        riskLog.setCreatedAt(LocalDateTime.now());
        transactionRiskLogRepository.save(riskLog);
    }

    public void logRiskLog(Transaction transaction, FraudCheckResult result) {
        if (result.rulesTriggered.isEmpty()) {
            return;
        }
        TransactionRiskLog riskLog = new TransactionRiskLog();
        riskLog.setTransactionId(transaction.getId());
        riskLog.setUserId(transaction.getFromWallet() != null ? transaction.getFromWallet().getOwner().getId() : transaction.getToWallet().getOwner().getId());
        riskLog.setRulesTriggerged(result.rulesTriggered.toArray(new String[0]));
        riskLog.setMlRiskScore(BigDecimal.ZERO);
        riskLog.setActionTaken(result.actionTaken);
        riskLog.setCreatedAt(LocalDateTime.now());
        transactionRiskLogRepository.save(riskLog);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    public String resolveLocationFromIp(String ipAddress) {
        if ("127.0.0.1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress)) {
            return "Local Development, LAN";
        }
        try {
            String apiUrl = "https://ipapi.co/" + ipAddress + "/json";
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set(org.springframework.http.HttpHeaders.USER_AGENT, "VeraPay-Backend-Service");
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    String.class
                );
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error") && root.get("error").asBoolean(false)) {
                return "Unknown Location";
            }
            String city = root.has("city") ? root.get("city").asText() : "";
            String country = root.has("country_name") ? root.get("country_name").asText() : "";
            if (city.isEmpty() && country.isEmpty()) {
                return "Unknown Location";
            }
            return city + ", " + country;
        } catch (Exception e) {
            log.warn("Failed to resolve geolocation for IP: {}", ipAddress);
            return "Unknown Location";
        }
    }

    private String extractCountry(String location) {
        if (location == null || location.isEmpty() || location.contains("Unknown") || location.contains("Local Development")) {
            return null;
        }
        int commaIndex = location.lastIndexOf(",");
        if (commaIndex != -1) {
            return location.substring(commaIndex + 1).trim();
        }
        return location.trim();
    }
}
