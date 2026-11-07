package com.example.springai.method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间工具类
 *
 * 使用 @Tool 注解定义工具；需要程序化包装普通方法时，可使用 MethodToolCallback。
 */
public class TimeTools {

    private static final Logger log = LoggerFactory.getLogger(TimeTools.class);

    /**
     * 获取当前时间
     */
    @Tool(description = "获取当前日期和时间，格式为 yyyy-MM-dd HH:mm:ss")
    public String getCurrentTime() {
        log.info("getCurrentTime 被调用");
        LocalDateTime now = LocalDateTime.now();
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 获取指定时区的当前时间
     */
    @Tool(description = "获取指定时区的当前时间")
    public String getTimeInTimezone(
            @ToolParam(description = "时区ID,例如: Asia/Shanghai, America/New_York, Europe/London")
            String timezone) {

        log.info("getTimeInTimezone 被调用, timezone={}", timezone);

        try {
            ZonedDateTime zonedTime = ZonedDateTime.now(ZoneId.of(timezone));
            return String.format("%s 时区的当前时间: %s",
                    timezone,
                    zonedTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")));
        } catch (Exception e) {
            return "无效的时区ID: " + timezone;
        }
    }

    /**
     * 计算两个日期之间的天数差
     */
    @Tool(description = "计算两个日期之间相差的天数，日期格式为 yyyy-MM-dd")
    public static int daysBetween(
            @ToolParam(description = "开始日期，格式 yyyy-MM-dd")
            String startDate,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd")
            String endDate) {

        log.info("daysBetween 被调用: {} -> {}", startDate, endDate);

        try {
            LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
            LocalDateTime end = LocalDateTime.parse(endDate + "T00:00:00");
            long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);
            return (int) days;
        } catch (Exception e) {
            return -1;
        }
    }
}
