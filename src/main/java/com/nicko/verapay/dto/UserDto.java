package com.nicko.verapay.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserDto(
        Long id,

        @NotBlank(message = "Full name is required")
        @Size(max = 100)
        String fullName,

        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Phone number is required")
        @Size(max = 15)
        String phoneNumber,

        @NotBlank(message = "Role is required")
        @Size(max = 20)
        String role

        // We typically omit the password from outgoing DTOs for security
) {}