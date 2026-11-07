package com.example.springai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 酒店预订请求 - 使用 @Schema 注解示例
 *
 * 演示如何使用 Swagger 的 @Schema 注解提供详细的参数信息
 */
public record BookingRequest(
        @Schema(
                description = "用户邮箱地址",
                example = "user@example.com",
                pattern = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String email,

        @Schema(
                description = "入住日期,ISO格式",
                type = "string",
                format = "date",
                example = "2024-01-15",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        LocalDate checkInDate,

        @Schema(
                description = "退房日期,ISO格式",
                type = "string",
                format = "date",
                example = "2024-01-20",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        LocalDate checkOutDate,

        @Schema(
                description = "房间类型",
                allowableValues = {"standard", "deluxe", "suite"},
                defaultValue = "standard",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String roomType,

        @Schema(
                description = "客人数量",
                minimum = "1",
                maximum = "10",
                defaultValue = "2",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        Integer guestCount,

        @Schema(
                description = "特殊要求列表",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        List<String> specialRequests
) {
    /**
     * 创建标准房间预订
     */
    public static BookingRequest standard(String email, LocalDate checkIn, LocalDate checkOut) {
        return new BookingRequest(email, checkIn, checkOut, "standard", 2, List.of());
    }

    /**
     * 创建豪华房间预订
     */
    public static BookingRequest deluxe(String email, LocalDate checkIn, LocalDate checkOut, int guestCount) {
        return new BookingRequest(email, checkIn, checkOut, "deluxe", guestCount, List.of());
    }
}
