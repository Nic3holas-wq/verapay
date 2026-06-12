package com.nicko.verapay.dto.authentication;

import com.nicko.verapay.dto.UserDto;

public record LoginResponseDto(String message, UserDto user, String jwtToken) {
}