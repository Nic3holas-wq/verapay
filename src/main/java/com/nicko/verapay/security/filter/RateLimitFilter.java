package com.nicko.verapay.security.filter;

import io.github.bucket4j.ConsumptionProbe;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicko.verapay.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String ipAddress = getClientIpAddress(request);
        ConsumptionProbe probe = null;

        if (path.contains("/auth/login")) {
            probe = rateLimitService.tryConsumeLogin(ipAddress);
            log.debug("Login rate limit check for IP: {}", ipAddress);

        } else if (path.contains("/auth/register")) {
            probe = rateLimitService.tryConsumeRegister(ipAddress);
            log.debug("Register rate limit check for IP: {}", ipAddress);

        } else if (path.contains("/auth/refresh")) {
            probe = rateLimitService.tryConsumeRefresh(ipAddress);
            log.debug("Refresh rate limit check for IP: {}", ipAddress);

        } else if (path.contains("/auth/step-up")) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser"))
                    ? auth.getName()
                    : ipAddress;
            probe = rateLimitService.tryConsumeStepUp(email);
            log.debug("Step-up rate limit check for: {}", email);
        }

        if (probe != null) {
            if (probe.isConsumed()) {
                response.addHeader("X-Rate-Limit-Remaining",
                        String.valueOf(probe.getRemainingTokens()));
                filterChain.doFilter(request, response);
            } else {
                long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(
                        probe.getNanosToWaitForRefill());
                log.warn("Rate limit exceeded for IP: {} on path: {}",
                        ipAddress, path);
                buildRateLimitResponse(response, waitSeconds);
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private void buildRateLimitResponse(HttpServletResponse response,
                                        long waitSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> errorBody = Map.of(
                "errorCode", "429 TOO_MANY_REQUESTS",
                "errorMessage", "Too many requests. Please try again in "
                        + waitSeconds + " seconds.",
                "retryAfterSeconds", waitSeconds,
                "errorTime", LocalDateTime.now().toString()
        );
        response.getWriter().write(objectMapper.writeValueAsString(errorBody));
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}