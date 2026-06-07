package com.nicko.verapay.auth;

import com.nicko.verapay.dto.StepUpRequestDto;
import com.nicko.verapay.entity.StepUpToken;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.exception.UnauthorizedException;
import com.nicko.verapay.repository.StepUpTokenRepository;
import com.nicko.verapay.repository.UserRepository;
import com.nicko.verapay.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class StepUpService {

    private final UserRepository userRepository;
    private final StepUpTokenRepository stepUpTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public String verifyPinAndIssueStepUpToken(StepUpRequestDto request) {

        // 1. Get currently logged-in user from Security Context
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // 2. Check user is active
        if (!user.getIsActive()) {
            throw new UnauthorizedException("Account is inactive");
        }

        // 3. Verify PIN
        if (!passwordEncoder.matches(request.transactionPin(),
                user.getTransactionPin())) {
            log.warn("Invalid transaction PIN attempt for user: {}", email);
            throw new UnauthorizedException("Invalid transaction PIN");
        }

        // 4. Invalidate any existing step-up tokens
        stepUpTokenRepository.invalidateAllUserStepUpTokens(user);

        // 5. Issue new step-up token (5 mins)
        StepUpToken stepUpToken = jwtUtil.generateStepUpToken(user);

        log.info("Step-up token issued for user: {}", email);
        return stepUpToken.getToken();
    }

    // Called by transaction endpoints to validate step-up token
    @Transactional
    public User validateStepUpToken(String stepUpTokenValue) {

        StepUpToken stepUpToken = stepUpTokenRepository
                .findByToken(stepUpTokenValue)
                .orElseThrow(() -> new UnauthorizedException("Invalid step-up token"));

        // Check if already used
        if (stepUpToken.isUsed()) {
            log.warn("Step-up token already used");
            throw new UnauthorizedException("Step-up token already used");
        }

        // Check expiry
        if (stepUpToken.getExpiresAt().isBefore(Instant.now())) {
            stepUpToken.setUsed(true);
            stepUpTokenRepository.save(stepUpToken);
            log.warn("Step-up token expired");
            throw new UnauthorizedException("Step-up token expired. Please verify PIN again.");
        }

        // Mark as used (one-time use)
        stepUpToken.setUsed(true);
        stepUpTokenRepository.save(stepUpToken);

        log.info("✅ Step-up token validated for user: {}",
                stepUpToken.getUser().getEmail());
        return stepUpToken.getUser();
    }
}