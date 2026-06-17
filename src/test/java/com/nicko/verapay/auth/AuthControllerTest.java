package com.nicko.verapay.auth;

import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.dto.authentication.LoginRequestDto;
import com.nicko.verapay.dto.authentication.LoginResponseDto;
import com.nicko.verapay.dto.authentication.RegisterRequestDto;
import com.nicko.verapay.dto.transactions.StepUpRequestDto;
import com.nicko.verapay.entity.RefreshToken;
import com.nicko.verapay.entity.Role;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.repository.RefreshTokenRepository;
import com.nicko.verapay.repository.RoleRepository;
import com.nicko.verapay.repository.UserRepository;
import com.nicko.verapay.repository.WalletRepository;
import com.nicko.verapay.security.util.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserRepository userRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private StepUpService stepUpService;
    @Mock private HttpServletResponse response;

    @InjectMocks private AuthController authController;

    // ── apiLogin() ────────────────────────────────────────────

    @Test
    @DisplayName("apiLogin: returns 200 with access token and sets refresh cookie on success")
    void apiLogin_Success() {
        LoginRequestDto dto = new LoginRequestDto("user@e.com", "pass");
        Authentication auth = mock(Authentication.class);
        User user = new User();
        user.setId(1L);
        user.setFullName("John");
        user.setEmail("user@e.com");
        user.setPhoneNumber("0700000000");
        Role role = new Role();
        role.setName(ApplicationConstants.ROLE_CUSTOMER);
        user.setRole(role);

        RefreshToken mockRefreshToken = new RefreshToken();
        mockRefreshToken.setToken("fake-refresh-token");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(auth.getPrincipal()).thenReturn(user);
        when(jwtUtil.generateAccessToken(auth)).thenReturn("mock-access-token");
        when(jwtUtil.generateRefreshToken(user, null)).thenReturn(mockRefreshToken);

        ResponseEntity<LoginResponseDto> result = authController.apiLogin(dto, response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("mock-access-token", result.getBody().jwtToken());
        assertEquals("Login Successful", result.getBody().message());
        verify(response).addCookie(argThat(c ->
                c.getName().equals("refreshToken")
                        && c.getValue().equals("fake-refresh-token")
                        && c.getSecure()
                        && c.isHttpOnly()
                        && c.getPath().equals("/api/auth")
        ));
    }

    @Test
    @DisplayName("apiLogin: returns 401 on bad credentials without issuing tokens")
    void apiLogin_BadCredentials_Returns401() {
        LoginRequestDto dto = new LoginRequestDto("user@e.com", "wrongpass");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        ResponseEntity<LoginResponseDto> result = authController.apiLogin(dto, response);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertEquals("Invalid username or password", result.getBody().message());
        verify(jwtUtil, never()).generateAccessToken(any());
        verify(response, never()).addCookie(any());
    }

    @Test
    @DisplayName("apiLogin: returns 500 when an unexpected exception occurs")
    void apiLogin_UnexpectedException_Returns500() {
        LoginRequestDto dto = new LoginRequestDto("user@e.com", "pass");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        ResponseEntity<LoginResponseDto> result = authController.apiLogin(dto, response);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertEquals("An unexpected error occurred", result.getBody().message());
    }

    @Test
    @DisplayName("apiLogin: returns 401 with generic message for non-BadCredentials authentication failures")
    void apiLogin_OtherAuthenticationException_Returns401WithGenericMessage() {
        LoginRequestDto dto = new LoginRequestDto("user@e.com", "pass");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("Account is disabled"));

        ResponseEntity<LoginResponseDto> result = authController.apiLogin(dto, response);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertEquals("Authentication failed", result.getBody().message());
        verify(jwtUtil, never()).generateAccessToken(any());
        verify(response, never()).addCookie(any());
    }

    // ── refresh() ─────────────────────────────────────────────

    @Test
    @DisplayName("refresh: returns 401 when refresh token cookie is missing")
    void refresh_NoCookie_Returns401() {
        ResponseEntity<?> result = authController.refresh(null, response);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertEquals("Refresh token missing", result.getBody());
        verify(refreshTokenService, never()).refresh(any());
    }

    @Test
    @DisplayName("refresh: returns new access token and rotates cookie on success")
    void refresh_Success_RotatesCookie() {
        RefreshTokenService.TokenPair tokenPair =
                new RefreshTokenService.TokenPair("new-access-token", "new-refresh-token");

        when(refreshTokenService.refresh("old-refresh-token")).thenReturn(tokenPair);

        ResponseEntity<?> result = authController.refresh("old-refresh-token", response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(Map.of("accessToken", "new-access-token"), result.getBody());
        verify(response).addCookie(argThat(c ->
                c.getName().equals("refreshToken")
                        && c.getValue().equals("new-refresh-token")
                        && c.getPath().equals("/api/auth")
        ));
    }

    // ── logout() ──────────────────────────────────────────────

    @Test
    @DisplayName("logout: revokes tokens and clears cookie when valid refresh token present")
    void logout_ValidToken_RevokesAndClearsCookie() {
        User user = new User();
        RefreshToken storedToken = new RefreshToken();
        storedToken.setUser(user);

        when(refreshTokenRepository.findByToken("some-token"))
                .thenReturn(Optional.of(storedToken));

        ResponseEntity<?> result = authController.logout("some-token", response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("Logged out successfully", result.getBody());
        verify(refreshTokenRepository).revokeAllUserTokens(user);
        verify(response).addCookie(argThat(c -> c.getMaxAge() == 0));
    }

    @Test
    @DisplayName("logout: still clears cookie gracefully when no refresh token cookie present")
    void logout_NoCookie_StillClearsCookie() {
        ResponseEntity<?> result = authController.logout(null, response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(refreshTokenRepository, never()).findByToken(any());
        verify(refreshTokenRepository, never()).revokeAllUserTokens(any());
        verify(response).addCookie(argThat(c -> c.getMaxAge() == 0));
    }

    @Test
    @DisplayName("logout: still clears cookie gracefully when token not found in DB")
    void logout_TokenNotFound_StillClearsCookie() {
        when(refreshTokenRepository.findByToken("unknown-token"))
                .thenReturn(Optional.empty());

        ResponseEntity<?> result = authController.logout("unknown-token", response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(refreshTokenRepository, never()).revokeAllUserTokens(any());
        verify(response).addCookie(argThat(c -> c.getMaxAge() == 0));
    }

    // ── registerUser() ────────────────────────────────────────

    @Test
    @DisplayName("registerUser: creates user with CUSTOMER role and zero-balance KES wallet")
    void registerUser_Success() {
        RegisterRequestDto dto = new RegisterRequestDto(
                "John", "j@e.com", "0700000000", "pass123", "1111");
        Role role = new Role();
        role.setName(ApplicationConstants.ROLE_CUSTOMER);

        when(passwordEncoder.encode("pass123")).thenReturn("hashed-pass");
        when(passwordEncoder.encode("1111")).thenReturn("hashed-pin");
        when(roleRepository.findRoleByName(ApplicationConstants.ROLE_CUSTOMER))
                .thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        ResponseEntity<?> result = authController.registerUser(dto);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals("User Registration successful", result.getBody());

        verify(userRepository).save(argThat(savedUser ->
                savedUser.getPassword().equals("hashed-pass")
                        && savedUser.getTransactionPin().equals("hashed-pin")
                        && savedUser.getRole().getName().equals(ApplicationConstants.ROLE_CUSTOMER)
                        && savedUser.getIsActive()
        ));
        verify(walletRepository).save(argThat(wallet ->
                wallet.getBalance().compareTo(BigDecimal.ZERO) == 0
                        && wallet.getCurrency().equals("KES")
                        && wallet.getIsActive()
        ));
    }

    @Test
    @DisplayName("registerUser: throws when CUSTOMER role is missing from the database")
    void registerUser_RoleNotFound_ThrowsException() {
        RegisterRequestDto dto = new RegisterRequestDto(
                "John", "j@e.com", "0700000000", "pass123", "1111");

        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(roleRepository.findRoleByName(ApplicationConstants.ROLE_CUSTOMER))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> authController.registerUser(dto));

        verify(userRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerUser: propagates exception on duplicate email constraint violation")
    void registerUser_DuplicateEmail_PropagatesException() {
        RegisterRequestDto dto = new RegisterRequestDto(
                "John", "j@e.com", "0700000000", "pass123", "1111");
        Role role = new Role();
        role.setName(ApplicationConstants.ROLE_CUSTOMER);

        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(roleRepository.findRoleByName(ApplicationConstants.ROLE_CUSTOMER))
                .thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThrows(DataIntegrityViolationException.class,
                () -> authController.registerUser(dto));

        verify(walletRepository, never()).save(any());
    }

    // ── stepUp() ──────────────────────────────────────────────

    @Test
    @DisplayName("stepUp: returns step-up token on valid PIN")
    void stepUp_Success() {
        StepUpRequestDto dto = new StepUpRequestDto("1234");

        when(stepUpService.verifyPinAndIssueStepUpToken(dto)).thenReturn("step-up-token-value");

        ResponseEntity<?> result = authController.stepUp(dto);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(Map.of(
                "stepUpToken", "step-up-token-value",
                "expiresIn", "5 minutes",
                "message", "Step-up authentication successful"
        ), result.getBody());
    }

    @Test
    @DisplayName("stepUp: propagates exception on invalid PIN")
    void stepUp_InvalidPin_PropagatesException() {
        StepUpRequestDto dto = new StepUpRequestDto("9999");

        when(stepUpService.verifyPinAndIssueStepUpToken(dto))
                .thenThrow(new BadCredentialsException("Invalid transaction PIN"));

        assertThrows(BadCredentialsException.class,
                () -> authController.stepUp(dto));
    }
}