package com.example.springai.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 酒店预订响应
 */
public record BookingResponse(
        @JsonProperty("confirmationNumber")
        String confirmationNumber,

        @JsonProperty("status")
        String status,

        @JsonProperty("message")
        String message
) {
    @Override
    public String toString() {
        return String.format("预订确认号: %s\n状态: %s\n消息: %s",
                confirmationNumber, status, message);
    }
}
