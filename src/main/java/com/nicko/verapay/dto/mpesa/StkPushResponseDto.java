package com.nicko.verapay.dto.mpesa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StkPushResponseDto(
        @JsonProperty("MerchantRequestID")
        String merchantRequestID,

        @JsonProperty("CheckoutRequestID")
        String checkoutRequestID,

        @JsonProperty("ResponseCode")
        String responseCode,

        @JsonProperty("ResponseDescription")
        String responseDescription,

        @JsonProperty("CustomerMessage")
        String customerMessage
) {}