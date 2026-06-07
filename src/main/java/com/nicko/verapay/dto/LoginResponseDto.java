package com.nicko.verapay.dto;

public record LoginResponseDto(String message, UserDto user, String jwtToken) {
}