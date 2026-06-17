package com.nicko.verapay.auth;

import com.nicko.verapay.dto.transactions.StepUpRequestDto;
import com.nicko.verapay.entity.StepUpToken;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.exception.UnauthorizedException;
import com.nicko.verapay.repository.StepUpTokenRepository;
import com.nicko.verapay.repository.UserRepository;
import com.nicko.verapay.security.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepUpServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private StepUpTokenRepository stepUpTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    @Mock private Authentication authentication;
    @Mock private SecurityContext securityContext;

    @InjectMocks
    private StepUpService stepUpService;

    private User activeUser;

    @BeforeEach
    void setUpSecurityContext() {
        // Tell the (real, static) SecurityContextHolder to use our fake context
        SecurityContextHolder.setContext(securityContext);

        activeUser = new User();
        activeUser.setEmail("user@e.com");
        activeUser.setIsActive(true);
        activeUser.setTransactionPin("hashed-pin");
    }

    @AfterEach
    void clearSecurityContext() {
        // Prevent this test's fake context leaking into the next test
        SecurityContextHolder.clearContext();
    }

    // Helper used only by tests that need a logged-in user
    private void givenLoggedInUser() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("user@e.com");
    }

    // ── verifyPinAndIssueStepUpToken() ───────────────────────

    @Test
    @DisplayName("verifyPin: issues step-up token when PIN is correct and user is active")
    void verifyPin_Success_IssuesToken() {
        givenLoggedInUser();
        StepUpRequestDto request = new StepUpRequestDto("1234");
        StepUpToken issuedToken = new StepUpToken();
        issuedToken.setToken("step-up-token-value");

        when(userRepository.findByEmail("user@e.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);
        when(jwtUtil.generateStepUpToken(activeUser)).thenReturn(issuedToken);

        String result = stepUpService.verifyPinAndIssueStepUpToken(request);

        assertEquals("step-up-token-value", result);
        verify(stepUpTokenRepository).invalidateAllUserStepUpTokens(activeUser);
    }

    @Test
    @DisplayName("verifyPin: throws when logged-in user cannot be found")
    void verifyPin_UserNotFound_Throws() {
        givenLoggedInUser();
        StepUpRequestDto request = new StepUpRequestDto("1234");

        when(userRepository.findByEmail("user@e.com")).thenReturn(Optional.empty());

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> stepUpService.verifyPinAndIssueStepUpToken(request));

        assertEquals("User not found", ex.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtUtil, never()).generateStepUpToken(any());
    }

    @Test
    @DisplayName("verifyPin: throws when account is inactive")
    void verifyPin_InactiveAccount_Throws() {
        givenLoggedInUser();
        StepUpRequestDto request = new StepUpRequestDto("1234");
        activeUser.setIsActive(false);

        when(userRepository.findByEmail("user@e.com")).thenReturn(Optional.of(activeUser));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> stepUpService.verifyPinAndIssueStepUpToken(request));

        assertEquals("Account is inactive", ex.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("verifyPin: throws when PIN does not match")
    void verifyPin_WrongPin_Throws() {
        givenLoggedInUser();
        StepUpRequestDto request = new StepUpRequestDto("9999");

        when(userRepository.findByEmail("user@e.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("9999", "hashed-pin")).thenReturn(false);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> stepUpService.verifyPinAndIssueStepUpToken(request));

        assertEquals("Invalid transaction PIN", ex.getMessage());
        verify(jwtUtil, never()).generateStepUpToken(any());
        verify(stepUpTokenRepository, never()).invalidateAllUserStepUpTokens(any());
    }

    // ── validateStepUpToken() ─────────────────────────────────

    @Test
    @DisplayName("validateToken: returns user and marks token used when valid")
    void validateToken_Success_MarksUsedAndReturnsUser() {
        StepUpToken token = new StepUpToken();
        token.setUsed(false);
        token.setExpiresAt(Instant.now().plusSeconds(300)); // expires in the future
        token.setUser(activeUser);

        when(stepUpTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        User result = stepUpService.validateStepUpToken("valid-token");

        assertEquals(activeUser, result);
        assertTrue(token.isUsed());
        verify(stepUpTokenRepository).save(token);
    }

    @Test
    @DisplayName("validateToken: throws when token does not exist")
    void validateToken_NotFound_Throws() {
        when(stepUpTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> stepUpService.validateStepUpToken("unknown"));

        assertEquals("Invalid step-up token", ex.getMessage());
    }

    @Test
    @DisplayName("validateToken: throws when token already used")
    void validateToken_AlreadyUsed_Throws() {
        StepUpToken token = new StepUpToken();
        token.setUsed(true);

        when(stepUpTokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> stepUpService.validateStepUpToken("used-token"));

        assertEquals("Step-up token already used", ex.getMessage());
        verify(stepUpTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateToken: throws and marks token used when expired")
    void validateToken_Expired_MarksUsedAndThrows() {
        StepUpToken token = new StepUpToken();
        token.setUsed(false);
        token.setExpiresAt(Instant.now().minusSeconds(10)); // already expired

        when(stepUpTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> stepUpService.validateStepUpToken("expired-token"));

        assertEquals("Step-up token expired. Please verify PIN again.", ex.getMessage());
        assertTrue(token.isUsed()); // confirms the expired token also gets marked used
        verify(stepUpTokenRepository).save(token);
    }
}