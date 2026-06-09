package com.nicko.verapay.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicko.verapay.security.filter.RateLimitFilter;
import com.nicko.verapay.service.RateLimitService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimitService rateLimitService) {
        return new RateLimitFilter(rateLimitService);
    }

    // ← This tells Spring Boot NOT to auto-register RateLimitFilter as a Servlet filter
    // It will only be used by Spring Security's filter chain
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false); // ← disable auto-registration
        return registration;
    }
}