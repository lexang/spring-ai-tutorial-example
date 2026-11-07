package com.example.springai.method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * MethodToolCallback 工厂服务 普通方法即可
 *
 * 演示如何使用 MethodToolCallback 程序化地创建工具
 * 无需修改原有类，就可以将任何方法转换为工具
 */
@Service
public class MethodToolCallbackFactory {

    private static final Logger log = LoggerFactory.getLogger(MethodToolCallbackFactory.class);

    /**
     * 创建时间工具的 ToolCallbacks
     *
     * 演示三种不同的创建方式
     */
    public List<ToolCallback> createTimeTools() {
        List<ToolCallback> tools = new ArrayList<>();
        TimeTools timeTools = new TimeTools();

        // 方式 1: 使用 ToolDefinitions.builder 根据方法签名生成完整 Schema
        Method getCurrentTimeMethod = ReflectionUtils.findMethod(TimeTools.class, "getCurrentTime");
        if (getCurrentTimeMethod == null) {
            throw new IllegalStateException("找不到时间工具方法: getCurrentTime");
        }
        ToolCallback getCurrentTime = MethodToolCallback.builder()
                .toolDefinition(ToolDefinitions.builder(getCurrentTimeMethod)
                        .description("获取当前日期和时间，格式为 yyyy-MM-dd HH:mm:ss")
                        .build())
                .toolMethod(getCurrentTimeMethod)
                .toolObject(timeTools)
                .build();
        tools.add(getCurrentTime);

        log.info("创建工具: getCurrentTime (根据方法签名生成 ToolDefinition)");

        // 方式 2: 使用 ToolDefinitions.builder - 自动生成
        Method getTimeInTimezoneMethod = ReflectionUtils.findMethod(
                TimeTools.class,
                "getTimeInTimezone",
                String.class
        );
        if (getTimeInTimezoneMethod == null) {
            throw new IllegalStateException("找不到时间工具方法: getTimeInTimezone");
        }
        ToolCallback getTimeInTimezone = MethodToolCallback.builder()
                .toolDefinition(ToolDefinitions.builder(getTimeInTimezoneMethod)
                        .description("获取指定时区的当前时间")
                        .build())
                .toolMethod(getTimeInTimezoneMethod)
                .toolObject(timeTools)
                .build();
        tools.add(getTimeInTimezone);

        log.info("创建工具: getTimeInTimezone (使用 ToolDefinitions.builder)");

        // 方式 3: 静态方法 - 不需要 toolObject
        Method daysBetweenMethod = ReflectionUtils.findMethod(
                TimeTools.class,
                "daysBetween",
                String.class,
                String.class
        );
        if (daysBetweenMethod == null) {
            throw new IllegalStateException("找不到时间工具方法: daysBetween");
        }
        ToolCallback daysBetween = MethodToolCallback.builder()
                .toolDefinition(ToolDefinitions.builder(daysBetweenMethod)
                        .description("计算两个日期之间相差的天数，日期格式为 yyyy-MM-dd")
                        .build())
                .toolMethod(daysBetweenMethod)
                // 静态方法不需要 toolObject
                .build();
        tools.add(daysBetween);

        log.info("创建工具: daysBetween (静态方法)");

        return tools;
    }

    /**
     * 动态创建工具 - 根据配置
     *
     * 演示如何根据配置动态创建工具
     */
    public ToolCallback createToolFromConfig(
            String className,
            String methodName,
            String toolName,
            String description) {

        try {
            Class<?> clazz = Class.forName(className);
            Method method = findMethodByName(clazz, methodName);

            if (method == null) {
                throw new IllegalArgumentException("找不到方法: " + methodName);
            }

            // 判断是否为静态方法
            boolean isStatic = java.lang.reflect.Modifier.isStatic(method.getModifiers());

            MethodToolCallback.Builder builder = MethodToolCallback.builder()
                    // ToolDefinition 必须包含 inputSchema；由方法签名自动生成最安全。
                    .toolDefinition(ToolDefinitions.builder(method)
                            .name(toolName)
                            .description(description)
                            .build())
                    .toolMethod(method);

            // 如果不是静态方法，需要提供实例
            if (!isStatic) {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                builder.toolObject(instance);
            }

            log.info("动态创建工具: {} (类={}, 方法={})", toolName, className, methodName);

            return builder.build();

        } catch (Exception e) {
            log.error("创建工具失败", e);
            throw new RuntimeException("创建工具失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据方法名查找方法（简单实现）
     */
    private Method findMethodByName(Class<?> clazz, String methodName) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }
}
