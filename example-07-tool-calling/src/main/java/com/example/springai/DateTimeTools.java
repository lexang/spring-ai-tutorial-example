package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具类
 *
 * 提供获取当前时间和设置闹钟的功能
 */
@Component
public class DateTimeTools {

    private static final Logger log = LoggerFactory.getLogger(DateTimeTools.class);

    /**
     * 获取当前日期和时间
     *
     * @return ISO-8601 格式的当前时间
     */
    @Tool(description = "获取当前日期和时间,使用用户的时区,返回 ISO-8601 格式")
    public String getCurrentDateTime() {
        log.info("工具被调用: getCurrentDateTime");

        ZoneId zoneId = LocaleContextHolder.getTimeZone().toZoneId();
        String result = LocalDateTime.now().atZone(zoneId).toString();

        log.info("返回当前时间: {}", result);
        return result;
    }

    /**
     * 设置闹钟
     *
     * @param time ISO-8601 格式的时间
     */
    @Tool(description = "为用户设置指定时间的闹钟,时间必须使用 ISO-8601 格式")
    public void setAlarm(
            @ToolParam(description = "ISO-8601 格式的时间,例如: 2024-01-15T14:30:00")
            String time) {

        log.info("工具被调用: setAlarm, 参数: time={}", time);

        try {
            LocalDateTime alarmTime = LocalDateTime.parse(time,
                    DateTimeFormatter.ISO_DATE_TIME);

            // 在实际应用中,这里会调用系统 API 设置真实的闹钟
            // 这里仅作演示,输出到控制台
            System.out.println("✅ 闹钟已设置: " + alarmTime);
            log.info("闹钟设置成功: {}", alarmTime);

        } catch (Exception e) {
            String error = "设置闹钟失败: " + e.getMessage();
            log.error(error, e);
            throw new IllegalArgumentException(error);
        }
    }

    /**
     * 获取当前星期几
     *
     * @return 星期几的中文表示
     */
    @Tool(description = "获取今天是星期几")
    public String getDayOfWeek() {
        log.info("工具被调用: getDayOfWeek");

        String[] weekDays = {"星期一", "星期二", "星期三", "星期四",
                "星期五", "星期六", "星期日"};

        int dayValue = LocalDateTime.now().getDayOfWeek().getValue();
        String result = weekDays[dayValue - 1];

        log.info("返回结果: {}", result);
        return result;
    }
}
