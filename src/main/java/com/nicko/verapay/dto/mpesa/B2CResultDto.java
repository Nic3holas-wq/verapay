package com.nicko.verapay.dto.mpesa;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record B2CResultDto(
        @JsonProperty("Result")
        Result result
) {
    public record Result(
            @JsonProperty("ResultType")
            int resultType,

            @JsonProperty("ResultCode")
            int resultCode,

            @JsonProperty("ResultDesc")
            String resultDesc,

            @JsonProperty("OriginatorConversationID")
            String originatorConversationID,

            @JsonProperty("ConversationID")
            String conversationID,

            @JsonProperty("TransactionID")
            String transactionID,

            @JsonProperty("ResultParameters")
            ResultParameters resultParameters
    ) {}

    public record ResultParameters(
            @JsonProperty("ResultParameter")
            List<ResultParameter> resultParameter
    ) {}

    public record ResultParameter(
            @JsonProperty("Key")
            String key,

            @JsonProperty("Value")
            Object value
    ) {}
}