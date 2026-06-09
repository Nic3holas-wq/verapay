package com.nicko.verapay.dto.mpesa;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record StkCallbackDto(
        @JsonProperty("Body")
        Body body
) {
    public record Body(
            @JsonProperty("stkCallback")
            StkCallback stkCallback
    ) {}

    public record StkCallback(
            @JsonProperty("MerchantRequestID")
            String merchantRequestID,

            @JsonProperty("CheckoutRequestID")
            String checkoutRequestID,

            @JsonProperty("ResultCode")
            int resultCode,

            @JsonProperty("ResultDesc")
            String resultDesc,

            @JsonProperty("CallbackMetadata")
            CallbackMetadata callbackMetadata
    ) {}

    public record CallbackMetadata(
            @JsonProperty("Item")
            List<CallbackItem> items
    ) {}

    public record CallbackItem(
            @JsonProperty("Name")
            String name,

            @JsonProperty("Value")
            Object value
    ) {}
}