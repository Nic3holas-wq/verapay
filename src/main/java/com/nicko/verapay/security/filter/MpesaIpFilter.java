package com.nicko.verapay.security.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Set;

@Slf4j
public class MpesaIpFilter implements Filter {

    private final Set<String> allowedIps;

    public MpesaIpFilter(Set<String> allowedIps) {
        this.allowedIps = allowedIps;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        if (path.startsWith("/api/webhooks/mpesa")) {
            String clientIp = getClientIp(httpRequest);

            if (!allowedIps.contains(clientIp)) {
                log.warn("Blocked unauthorized webhook call from IP: {} to path: {}",
                        clientIp, path);
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.getWriter().write("Forbidden");
                return;
            }

            log.info("Verified Mpesa webhook call from IP: {}", clientIp);
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}