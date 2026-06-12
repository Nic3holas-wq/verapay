package com.nicko.verapay.dto.admin;

import java.time.Instant;

public record UserAdminDto(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        String role,
        Boolean isActive,
        Instant memberSince
) {}