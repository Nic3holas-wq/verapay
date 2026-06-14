package com.nicko.verapay.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicko.verapay.security.filter.MpesaIpFilter;
import com.nicko.verapay.security.filter.RateLimitFilter;
import com.nicko.verapay.service.RateLimitService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

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

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }

    @Bean
    public MpesaIpFilter mpesaIpFilter(MpesaConfig mpesaConfig) {
        return new MpesaIpFilter(mpesaConfig.getAllowedIps());
    }

    @Bean
    public FilterRegistrationBean<MpesaIpFilter> mpesaIpFilterRegistration(
            MpesaIpFilter mpesaIpFilter) {
        FilterRegistrationBean<MpesaIpFilter> registrationBean =
                new FilterRegistrationBean<>(mpesaIpFilter);
        registrationBean.setOrder(1);
        return registrationBean;
    }
}