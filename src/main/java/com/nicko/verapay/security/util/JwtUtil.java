package com.nicko.verapay.security.util;

import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.entity.RefreshToken;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.repository.RefreshTokenRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@PropertySource(value = "classpath:jwt.properties")
public class JwtUtil {

    private final Environment env;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.issuer:Vera Pay}")
    private String jwtIssuer;

    @Value("${jwt.subject:JWT Token}")
    private String jwtSubject;

    @Value("${jwt.expiration.minutes:15}")      // ← changed to 15 minutes
    private int jwtExpirationMinutes;

    @Value("${jwt.refresh.expiration.days:7}")  // ← 7 days for refresh
    private int refreshExpirationDays;

    // ✅ Generate short-lived Access Token (15 mins)
    public String generateAccessToken(Authentication authentication) {
        return generateAccessToken((User) authentication.getPrincipal(),
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(",")));
    }

    public String generateAccessToken(User user, String roles) {
        SecretKey secretKey = getSecretKey();
        int expirationMinutes = jwtExpirationMinutes;

        List<String> profiles = Arrays.asList(env.getActiveProfiles());
        if (profiles.contains("prod")) {
            expirationMinutes = 15; // enforce 15 mins in prod
        }

        return Jwts.builder()
                .issuer(jwtIssuer)
                .subject(jwtSubject)
                .claim("name", user.getFullName())
                .claim("email", user.getEmail())
                .claim("phoneNumber", user.getPhoneNumber())
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() +
                        (long) expirationMinutes * 60 * 1000))
                .signWith(secretKey)
                .compact();
    }

    // ✅ Generate long-lived Refresh Token (7 days) — stored in DB
    public RefreshToken generateRefreshToken(User user, String previousToken) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString()); // random, not JWT
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plusSeconds(
                (long) refreshExpirationDays * 24 * 60 * 60));
        refreshToken.setRevoked(false);
        refreshToken.setPreviousToken(previousToken); // for reuse detection
        return refreshTokenRepository.save(refreshToken);
    }

    // Keep old method name for backward compatibility
    public String generateJwtToken(Authentication authentication) {
        return generateAccessToken(authentication);
    }

    private SecretKey getSecretKey() {
        String secret = env.getProperty(ApplicationConstants.JWT_SECRET_KEY,
                ApplicationConstants.JWT_SECRET_DEFAULT_VALUE);
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}