package com.nicko.verapay.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PathsConfig {

    @Bean(name = "publicPaths")
    public List<String> publicPaths() {
        return List.of(
                "/api/auth/login/public",
                "/api/auth/refresh/public",
                "/api/companies/public",
                "/api/auth/register/public",
                "/api/csrf-token/public",
                "/api/logging/public",
                "/api/swagger-ui.html",
                "/swagger-ui/**",
                "/api/v3/api-docs/**",
                "/swagger-resources/**",
                "/swagger-ui.html",
                "/webjars/**",
                "/verapay/actuator/**",
                "/api/webhooks/mpesa/**"
        );
    }

    @Bean(name = "securedPaths")
    public List<String> securedPaths() {
        return List.of(
                "/api/auth/step-up",
                "/api/**"
        );
    }

    @Bean(name = "customerPaths")
    public List<String> customerPaths() {
        return List.of(
                "/api/transactions/deposit",
                "/api/transactions/withdraw",
                "/api/transactions/transfer",
                "/api/wallet/profile",
                "/api/wallet/transactions"
        );
    }

    @Bean(name = "adminPaths")
    public List<String> adminPaths() {
        return List.of(
                "/api/admin/users",
                "/api/admin/users/**",
                "/api/admin/transactions",
                "/api/admin/transactions/**"
        );
    }

}
