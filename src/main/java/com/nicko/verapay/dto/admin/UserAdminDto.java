package com.nicko.verapay.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record UserAdminDto(
        UUID publicId,
        String fullName,
        String email,
        String phoneNumber,
        String role,
        Boolean isActive,
        Instant memberSince
) {}