package com.nicko.verapay.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.nicko.verapay.entity.fraud.UserDevice;
import com.nicko.verapay.repository.RefreshTokenRepository;
import com.nicko.verapay.repository.RoleRepository;
import com.nicko.verapay.repository.UserRepository;
import com.nicko.verapay.repository.WalletRepository;
import com.nicko.verapay.repository.fraud.UserDeviceRepository;
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
import org.springframework.web.client.RestTemplate;

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
    private final UserDeviceRepository userDeviceRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/login/public", version = "2.0")
    public ResponseEntity<LoginResponseDto> apiLoginV2(
            @RequestBody LoginRequestDto request,
            jakarta.servlet.http.HttpServletRequest servletRequest,
            jakarta.servlet.http.HttpServletResponse response) {

        log.info("Executing V2 Login Endpoint...");

        try {
            // Authenticate principal
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(), request.password()));

            User user = (User) auth.getPrincipal();
            String accessToken = jwtUtil.generateAccessToken(auth);

            // Generate refresh token
            RefreshToken refreshToken = jwtUtil.generateRefreshToken(user, null);

            // V2 Telemetry / Enhanced Device Tracking
            String userAgent = servletRequest.getHeader("User-Agent");
            String ipAddress = resolveClientIp(servletRequest);
            String locationGeo = resolveLocationFromIp(ipAddress);

            String deviceFingerprint = servletRequest.getHeader("X-Device-Fingerprint");
            if (deviceFingerprint == null || deviceFingerprint.trim().isEmpty()) {
                deviceFingerprint = java.util.UUID.nameUUIDFromBytes((userAgent + ipAddress).getBytes()).toString();
            }

            // Register device in the newly created auditing table
            registerDeviceLogin(user.getId(), deviceFingerprint, ipAddress, userAgent, "Embu, Kenya");

            // Set cookie securely
            Cookie refreshCookie = new Cookie("refreshToken", refreshToken.getToken());
            refreshCookie.setHttpOnly(true);
            refreshCookie.setSecure(true);
            refreshCookie.setPath("/api/auth");
            refreshCookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
            response.addCookie(refreshCookie);

            UserDto userDto = new UserDto(user.getPublicId(), user.getFullName(),
                    user.getEmail(), user.getPhoneNumber(), user.getRole().getName());

            // You can wrap this in a specialized LoginResponseV2Dto if your V2 requires different fields
            return ResponseEntity.ok(new LoginResponseDto("V2 Login Successful", userDto, accessToken));

        } catch (BadCredentialsException ex) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        } catch (AuthenticationException ex) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication failed");
        } catch (Exception ex) {
            log.error("An unexpected error occurred during V2 login", ex);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
    }


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

    private void registerDeviceLogin(Long userId, String fingerprint, String ip, String userAgent, String location) {
        java.util.Optional<UserDevice> existingDevice =
                userDeviceRepository.findByUserIdAndDeviceFingerprint(userId, fingerprint);

        if (existingDevice.isPresent()) {
            UserDevice device = existingDevice.get();
            device.setIpAddress(ip);
            device.setLocationGeo(location);
            device.setLastLogin(java.time.LocalDateTime.now());
            userDeviceRepository.save(device);
        } else {
            UserDevice newDevice = new UserDevice();
            newDevice.setUserId(userId);
            newDevice.setDeviceFingerprint(fingerprint);
            newDevice.setIpAddress(ip);
            newDevice.setLocationGeo(location);
            newDevice.setUserAgent(userAgent);
            userDeviceRepository.save(newDevice);
        }
    }

    public String resolveClientIp(jakarta.servlet.http.HttpServletRequest request) {
        // Standard header used by proxies and load balancers
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can be a comma-separated list if it passed through multiple proxies;
            // the true client IP is the very first address.
            return xForwardedFor.split(",")[0].trim();
        }

        // Fallback headers
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // Direct connection fallback (e.g., local testing)
        return request.getRemoteAddr();
    }

    // conceptual integration using an IP Geolocation service
    public String resolveLocationFromIp(String ipAddress) {
        if ("127.0.0.1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress)) {
            return "Local Development, LAN";
        }

        try {
            String apiUrl = "https://ipapi.co/" + ipAddress + "/json";

            // Setting headers prevents the API from rejecting requests as un-user-agent-identified bots
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set(org.springframework.http.HttpHeaders.USER_AGENT, "VeraPay-Backend-Service");
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    String.class
            );

            // Safely parse the response string into a JsonNode tree
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("error") && root.get("error").asBoolean(false)) {
                return root.has("reason") ? root.get("reason").asText() : "IP Geolocation Error";
            }

            String city = root.has("city") ? root.get("city").asText() : "";
            String country = root.has("country_name") ? root.get("country_name").asText() : "";

            if (city.isEmpty() && country.isEmpty()) {
                return "Unknown Location";
            }

            return city + ", " + country;

        } catch (Exception e) {
            log.warn("Failed to resolve geolocation for IP: {}", ipAddress, e);
            return "Unknown Location";
        }
    }
}