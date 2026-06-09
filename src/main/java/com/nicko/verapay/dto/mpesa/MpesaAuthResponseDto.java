package com.nicko.verapay.dto.mpesa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MpesaAuthResponseDto(
        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("expires_in")
        String expiresIn
) {}