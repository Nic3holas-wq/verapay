package com.nicko.verapay.auth;

import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.entity.RefreshToken;
import com.nicko.verapay.entity.Role;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.exception.UnauthorizedException;
import com.nicko.verapay.repository.RefreshTokenRepository;
import com.nicko.verapay.repository.UserRepository;
import com.nicko.verapay.security.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User testUser;
    private RefreshToken activeToken;

    @BeforeEach
    void setUp() {
        Role role = new Role();
        role.setName(ApplicationConstants.ROLE_CUSTOMER);

        testUser = new User();
        testUser.setEmail("user@e.com");
        testUser.setRole(role);

        activeToken = new RefreshToken();
        activeToken.setToken("valid-token");
        activeToken.setUser(testUser);
        activeToken.setExpiresAt(Instant.now().plusSeconds(3600));
        activeToken.setRevoked(false);
    }

    // ── refresh() Success Path ───────────────────────────────

    @Test
    @DisplayName("refresh: rotates tokens successfully")
    void refresh_Success_RotatesTokens() {
        RefreshToken newToken = new RefreshToken();
        newToken.setToken("new-refresh-token");

        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(activeToken));
        when(jwtUtil.generateAccessToken(testUser, ApplicationConstants.ROLE_CUSTOMER)).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken(testUser, "valid-token")).thenReturn(newToken);

        RefreshTokenService.TokenPair result = refreshTokenService.refresh("valid-token");

        assertEquals("new-access-token", result.accessToken());
        assertEquals("new-refresh-token", result.refreshToken());
        assertTrue(activeToken.isRevoked());
        verify(refreshTokenRepository).save(activeToken);
    }

    // ── refresh() Error Paths ───────────────────────────────

    @Test
    @DisplayName("refresh: throws when token does not exist")
    void refresh_TokenNotFound_Throws() {
        when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> refreshTokenService.refresh("unknown"));
    }

    @Test
    @DisplayName("refresh: throws when token is expired")
    void refresh_ExpiredToken_Throws() {
        activeToken.setExpiresAt(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(activeToken));

        assertThrows(UnauthorizedException.class, () -> refreshTokenService.refresh("expired-token"));
        assertTrue(activeToken.isRevoked());
        verify(refreshTokenRepository).save(activeToken);
    }

    // ── refresh() Reuse/Grace Period Paths ──────────────────

    @Test
    @DisplayName("refresh: handles reuse within grace period (concurrency)")
    void refresh_ReuseGracePeriod_ReturnsLatest() {
        activeToken.setRevoked(true);
        activeToken.setRevokedAt(Instant.now().minusSeconds(5)); // Within 15s grace

        RefreshToken latestToken = new RefreshToken();
        latestToken.setToken("latest-token");
        latestToken.setUser(testUser);

        when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(activeToken));
        when(refreshTokenRepository.findByPreviousToken("old-token")).thenReturn(Optional.of(latestToken));
        when(jwtUtil.generateAccessToken(testUser, ApplicationConstants.ROLE_CUSTOMER)).thenReturn("access-token");

        RefreshTokenService.TokenPair result = refreshTokenService.refresh("old-token");

        assertEquals("latest-token", result.refreshToken());
        verify(jwtUtil, never()).generateRefreshToken(any(), any());
    }

    @Test
    @DisplayName("refresh: throws when token reused outside grace period")
    void refresh_ReuseOutsideGracePeriod_RevokesAllAndThrows() {
        activeToken.setRevoked(true);
        activeToken.setRevokedAt(Instant.now().minusSeconds(30)); // Outside 15s grace

        when(refreshTokenRepository.findByToken("stolen-token")).thenReturn(Optional.of(activeToken));

        assertThrows(UnauthorizedException.class, () -> refreshTokenService.refresh("stolen-token"));
        verify(refreshTokenRepository).revokeAllUserTokens(testUser);
    }
}