package com.nicko.verapay.auth;

import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.dto.*;
import com.nicko.verapay.entity.Role;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.entity.Wallet;
import com.nicko.verapay.repository.RoleRepository;
import com.nicko.verapay.repository.UserRepository;
import com.nicko.verapay.repository.WalletRepository;
import com.nicko.verapay.security.util.JwtUtil;
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

    // V1: Standard Login
    @PostMapping(value = "/login/public", version = "1.0")
    public ResponseEntity<LoginResponseDto> apiLogin(@RequestBody LoginRequestDto request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            String token = jwtUtil.generateJwtToken(auth);
            User user = (User) auth.getPrincipal();

            UserDto userDto = new UserDto(user.getId(), user.getFullName(), user.getEmail(), user.getPhoneNumber(), null);
            return ResponseEntity.ok(new LoginResponseDto("Login Successful", userDto, token));
        }catch (BadCredentialsException ex) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED,
                    "Invalid username or password");
        } catch (AuthenticationException ex) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED,
                    "Authentication failed");
        } catch (Exception ex) {
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred");
        }
    }

    // V1 User Registration
    @PostMapping(value = "/register/public", version = "1.0")
    @Transactional
    public ResponseEntity<String> registerUser(@RequestBody @Valid RegisterRequestDto registerRequestDto) {
        User user = new User();
        BeanUtils.copyProperties(registerRequestDto, user);
        user.setPassword(passwordEncoder.encode(registerRequestDto.password()));
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

    private ResponseEntity<LoginResponseDto> buildErrorResponse(HttpStatus status,
                                                                String message) {
        return ResponseEntity
                .status(status)
                .body(new LoginResponseDto(message, null, null));
    }
}