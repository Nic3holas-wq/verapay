package com.nicko.verapay.dto.mpesa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StkPushRequestDto(
        @JsonProperty("BusinessShortCode")
        String businessShortCode,

        @JsonProperty("Password")
        String password,

        @JsonProperty("Timestamp")
        String timestamp,

        @JsonProperty("TransactionType")
        String transactionType,

        @JsonProperty("Amount")
        String amount,

        @JsonProperty("PartyA")
        String partyA,

        @JsonProperty("PartyB")
        String partyB,

        @JsonProperty("PhoneNumber")
        String phoneNumber,

        @JsonProperty("CallBackURL")
        String callBackURL,

        @JsonProperty("AccountReference")
        String accountReference,

        @JsonProperty("TransactionDesc")
        String transactionDesc
) {}