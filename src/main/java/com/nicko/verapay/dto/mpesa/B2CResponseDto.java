package com.nicko.verapay.dto.mpesa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record B2CResponseDto(
        @JsonProperty("OriginatorConversationID")
        String originatorConversationID,

        @JsonProperty("ConversationID")
        String conversationID,

        @JsonProperty("ResponseCode")
        String responseCode,

        @JsonProperty("ResponseDescription")
        String responseDescription
) {}