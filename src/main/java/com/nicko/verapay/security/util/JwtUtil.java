package com.nicko.verapay.security.util;

import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.entity.User;
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@PropertySource(value = "classpath:jwt.properties")
public class JwtUtil {

    private final Environment env;

    @Value("${jwt.issuer:Vera Pay}")
    private String jwtIssuer;

    @Value("${jwt.subject:JWT Token}")
    private String jwtSubject;

    @Value("${jwt.expiration.hours:1}")
    private int jwtExpirationHours;

    @Value("${jwt.prod.expiration.hours:1}")
    private int jwtProdExpirationHours;

    public String generateJwtToken(Authentication authentication){
        String jwtToken;
        int expirationHours = jwtExpirationHours;
        // String ttlTime = env.getProperty("cache.jobs.ttl-minutes","5");
        List<String> profiles = Arrays.asList(env.getActiveProfiles());
        if (profiles.contains("prod")) {
            expirationHours = jwtProdExpirationHours;
        }
        String secret = env.getProperty(ApplicationConstants.JWT_SECRET_KEY,
                ApplicationConstants.JWT_SECRET_DEFAULT_VALUE);
        SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        var fetchedUser = (User) authentication.getPrincipal();
        jwtToken = Jwts.builder().issuer(jwtIssuer).subject(jwtSubject)
                .claim("name", fetchedUser.getFullName())
                .claim("email", fetchedUser.getEmail())
                .claim("phoneNumber", fetchedUser.getPhoneNumber())
                .claim("roles", authentication.getAuthorities().stream().map(
                        GrantedAuthority::getAuthority).collect(Collectors.joining(",")))
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date((new java.util.Date()).getTime() + expirationHours * 60 * 60 * 1000))
                .signWith(secretKey).compact();
        return jwtToken;
    }
}
