package com.nicko.verapay.auth;

import com.nicko.verapay.entity.RefreshToken;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.exception.UnauthorizedException;
import com.nicko.verapay.repository.RefreshTokenRepository;
import com.nicko.verapay.repository.UserRepository;
import com.nicko.verapay.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public TokenPair refresh(String incomingToken) {

        RefreshToken stored = refreshTokenRepository.findByToken(incomingToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        // Reuse detection — token already used before
        if (stored.isRevoked()) {
            log.warn("Refresh token reuse detected for user: {}",
                    stored.getUser().getEmail());
            // Revoke ALL tokens for this user — force re-login
            refreshTokenRepository.revokeAllUserTokens(stored.getUser());
            throw new UnauthorizedException(
                    "Refresh token reuse detected. All sessions revoked. Please login again.");
        }

        // Check expiry
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new UnauthorizedException("Refresh token expired. Please login again.");
        }

        // ✅ Revoke old token (rotation)
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        // ✅ Issue new access token
        User user = stored.getUser();
        String roles = user.getRole().getName();
        String newAccessToken = jwtUtil.generateAccessToken(user, roles);

        // ✅ Issue new refresh token (rotation) — linked to old token
        RefreshToken newRefreshToken = jwtUtil.generateRefreshToken(user, incomingToken);

        log.info("✅ Token rotated for user: {}", user.getEmail());
        return new TokenPair(newAccessToken, newRefreshToken.getToken());
    }

    // Simple record to hold both tokens
    public record TokenPair(String accessToken, String refreshToken) {}
}