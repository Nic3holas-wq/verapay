package com.nicko.verapay.dto.admin;

import jakarta.validation.constraints.NotNull;

public record ToggleUserStatusDto(
        @NotNull(message = "Status is required")
        Boolean isActive
) {}