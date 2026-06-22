package com.nicko.verapay.auth;

import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.dto.*;
import com.nicko.verapay.dto.authentication.LoginRequestDto;
import com.nicko.verapay.dto.authentication.LoginResponseDto;
import com.nicko.verapay.dto.authentication.RegisterRequestDto;
import com.nicko.verapay.dto.transactions.StepUpRequestDto;
import com.nicko.verapay.entity.RefreshToken;
import com.nicko.verapay.entity.Role;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.entity.Wallet;
import com.nicko.verapay.repository.RefreshTokenRepository;
import com.nicko.verapay.repository.RoleRepository;
import com.nicko.verapay.repository.UserRepository;
import com.nicko.verapay.repository.WalletRepository;
import com.nicko.verapay.security.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final StepUpService stepUpService;

// Update login to return both tokens
    @PostMapping(value = "/login/public", version = "1.0")
    public ResponseEntity<LoginResponseDto> apiLogin(
            @RequestBody LoginRequestDto request,
            HttpServletResponse response) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(), request.password()));

            User user = (User) auth.getPrincipal();
            String accessToken = jwtUtil.generateAccessToken(auth);

            // Generate refresh token
            RefreshToken refreshToken = jwtUtil.generateRefreshToken(user, null);

            // Send refresh token as HttpOnly cookie
            Cookie refreshCookie = new Cookie("refreshToken", refreshToken.getToken());
            refreshCookie.setHttpOnly(true);
            refreshCookie.setSecure(true);
            refreshCookie.setPath("/api/auth");
            refreshCookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
            response.addCookie(refreshCookie);

            UserDto userDto = new UserDto(user.getPublicId(), user.getFullName(),
                    user.getEmail(), user.getPhoneNumber(), user.getRole().getName());

            return ResponseEntity.ok(new LoginResponseDto("Login Successful", userDto, accessToken));

        } catch (BadCredentialsException ex) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        } catch (AuthenticationException ex) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication failed");
        } catch (Exception ex) {
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
    }

    // refresh endpoint
    @PostMapping(value = "/refresh/public", version = "1.0")
    public ResponseEntity<?> refresh(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Refresh token missing");
        }

        RefreshTokenService.TokenPair tokenPair = refreshTokenService.refresh(refreshToken);

        // Rotate cookie with new refresh token
        Cookie newCookie = new Cookie("refreshToken", tokenPair.refreshToken());
        newCookie.setHttpOnly(true);
        newCookie.setSecure(true);
        newCookie.setPath("/api/auth");
        newCookie.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(newCookie);

        return ResponseEntity.ok(Map.of("accessToken", tokenPair.accessToken()));
    }

    // Logout — revoke all tokens
    @Transactional
    @PostMapping(value = "/logout/public", version = "1.0")
    public ResponseEntity<?> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken != null) {
            refreshTokenRepository.findByToken(refreshToken)
                    .ifPresent(token -> {
                        refreshTokenRepository.revokeAllUserTokens(token.getUser());
                    });
        }

        // Clear the cookie
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setMaxAge(0);
        cookie.setPath("/api/auth");
        response.addCookie(cookie);

        return ResponseEntity.ok("Logged out successfully");
    }

    // V1 User Registration
    @PostMapping(value = "/register/public", version = "1.0")
    @Transactional
    public ResponseEntity<?> registerUser(@RequestBody @Valid RegisterRequestDto registerRequestDto) {

        User user = new User();
        BeanUtils.copyProperties(registerRequestDto, user, "password", "transactionPin");
        user.setPassword(passwordEncoder.encode(registerRequestDto.password()));
        user.setTransactionPin(passwordEncoder.encode(registerRequestDto.transactionPin()));

        Role role = roleRepository.findRoleByName(ApplicationConstants.ROLE_CUSTOMER)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " +
                        ApplicationConstants.ROLE_CUSTOMER));
        user.setRole(role);
        user.setIsActive(true);
        User savedUser = userRepository.save(user);

        Wallet wallet = new Wallet();
        wallet.setOwner(savedUser);
        wallet.setBalance(java.math.BigDecimal.ZERO);
        wallet.setCurrency("KES");
        wallet.setIsActive(true);
        walletRepository.save(wallet);

        return ResponseEntity.status(HttpStatus.CREATED).body("User Registration successful");
    }

    // Step-Up Authentication — verify PIN and get Step-Up Token
    @PostMapping(value = "/step-up")
    public ResponseEntity<?> stepUp(
            @RequestBody @Valid StepUpRequestDto request) {
        String stepUpToken = stepUpService.verifyPinAndIssueStepUpToken(request);
        return ResponseEntity.ok(Map.of(
                "stepUpToken", stepUpToken,
                "expiresIn", "5 minutes",
                "message", "Step-up authentication successful"
        ));
    }


    private ResponseEntity<LoginResponseDto> buildErrorResponse(HttpStatus status,
                                                                String message) {
        return ResponseEntity
                .status(status)
                .body(new LoginResponseDto(message, null, null));
    }
}