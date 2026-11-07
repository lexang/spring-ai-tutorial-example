package com.example.springai.schema;

import com.example.springai.model.BookingRequest;
import com.example.springai.model.BookingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 酒店预订工具 - 演示使用 @Schema 注解
 *
 * 这个类展示了如何使用 Swagger 的 @Schema 注解
 * 提供详细的参数信息，帮助 AI 更好地理解如何使用工具
 */
@Component
public class BookingTools {

    private static final Logger log = LoggerFactory.getLogger(BookingTools.class);

    /**
     * 预订酒店房间
     *
     * 使用 BookingRequest，该类中的所有字段都使用了 @Schema 注解
     * 提供了详细的验证规则和描述
     */
    @Tool(description = "预订酒店房间，支持选择房型和指定入住/退房日期")
    public BookingResponse bookRoom(BookingRequest request) {
        log.info("收到酒店预订请求: {}", request);

        if (request == null) {
            return failed("预订请求不能为空");
        }
        if (request.email() == null || !request.email().matches(
                "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            return failed("邮箱地址格式无效");
        }
        if (request.checkInDate() == null || request.checkOutDate() == null) {
            return failed("入住日期和退房日期不能为空");
        }

        // 验证日期
        if (request.checkOutDate().isBefore(request.checkInDate())) {
            return failed("退房日期不能早于入住日期");
        }

        // 计算住宿天数
        long days = ChronoUnit.DAYS.between(request.checkInDate(), request.checkOutDate());
        if (days < 1) {
            return failed("至少需要预订1晚");
        }

        // 验证房型
        String roomType = request.roomType() != null ? request.roomType() : "standard";
        if (!roomType.matches("standard|deluxe|suite")) {
            return failed("无效的房间类型: " + roomType);
        }

        // 验证客人数量
        int guestCount = request.guestCount() != null ? request.guestCount() : 2;
        if (guestCount < 1 || guestCount > 10) {
            return failed("客人数量必须在 1-10 之间");
        }

        // 生成确认号
        String confirmationNumber = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 构建成功消息
        StringBuilder message = new StringBuilder();
        message.append(String.format("成功预订 %s 房间\n", getRoomTypeName(roomType)));
        message.append(String.format("入住日期: %s\n", request.checkInDate()));
        message.append(String.format("退房日期: %s\n", request.checkOutDate()));
        message.append(String.format("住宿天数: %d 晚\n", days));
        message.append(String.format("客人数量: %d 人\n", guestCount));
        message.append(String.format("联系邮箱: %s\n", request.email()));

        if (request.specialRequests() != null && !request.specialRequests().isEmpty()) {
            message.append("特殊要求:\n");
            request.specialRequests().forEach(req ->
                    message.append("  - ").append(req).append("\n")
            );
        }

        log.info("预订成功，确认号: {}", confirmationNumber);

        return new BookingResponse(
                confirmationNumber,
                "CONFIRMED",
                message.toString()
        );
    }

    /**
     * 查询预订信息
     */
    @Tool(description = "根据确认号查询酒店预订信息")
    public String queryBooking(String confirmationNumber) {
        log.info("查询预订: {}", confirmationNumber);

        // 模拟查询
        if (confirmationNumber == null || !confirmationNumber.startsWith("BK-")) {
            return "无效的确认号格式";
        }

        return String.format("""
                预订信息:
                确认号: %s
                状态: CONFIRMED
                房型: 豪华大床房
                入住日期: 2024-01-15
                退房日期: 2024-01-20
                客人数量: 2 人
                """, confirmationNumber);
    }

    private String getRoomTypeName(String roomType) {
        return switch (roomType) {
            case "standard" -> "标准房";
            case "deluxe" -> "豪华房";
            case "suite" -> "套房";
            default -> roomType;
        };
    }

    private BookingResponse failed(String message) {
        return new BookingResponse("ERROR", "FAILED", message);
    }
}
