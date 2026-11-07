package com.example.springai.function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * 时间服务 - 使用 Lambda 表达式的 Function 示例
 *
 * 演示如何使用 Function<Void, String> 实现无参数工具
 */
public class TimeService {

    private static final Logger log = LoggerFactory.getLogger(TimeService.class);

    /**
     * 获取当前时间的 Function
     *
     * 使用 Supplier 模式,但通过 Function<Void, String> 实现
     */
    public static Function<Void, String> getCurrentTime() {
        return (Void input) -> {
            log.info("TimeService.getCurrentTime 被调用");

            String time = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            log.info("返回当前时间: {}", time);
            return time;
        };
    }

    /**
     * 获取格式化时间的 Function
     *
     * 使用 Function<String, String> 接收格式化参数
     */
    public static Function<String, String> getFormattedTime() {
        return (String pattern) -> {
            log.info("TimeService.getFormattedTime 被调用: pattern={}", pattern);

            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                String time = LocalDateTime.now().format(formatter);

                log.info("返回格式化时间: {}", time);
                return time;

            } catch (Exception e) {
                String error = "时间格式错误: " + e.getMessage();
                log.error(error, e);
                return error;
            }
        };
    }
}
