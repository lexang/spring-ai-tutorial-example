package com.example.springai.converter;

import com.example.springai.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;

/**
 * 自定义结果转换器
 *
 * 实现 ToolCallResultConverter 接口,自定义工具返回值的格式化方式
 * 默认情况下,Spring AI 使用 Jackson 将结果序列化为 JSON
 * 自定义转换器可以提供更友好的文本格式
 */
public class CustomResultConverter implements ToolCallResultConverter {

    private static final Logger log = LoggerFactory.getLogger(CustomResultConverter.class);

    /**
     * 转换工具返回结果为字符串
     *
     * @param result 工具方法的返回值
     * @param returnType 返回值类型
     * @return 格式化后的字符串
     */
    @Override
    public String convert(Object result, Type returnType) {
        log.debug("CustomResultConverter.convert 被调用: resultType={}",
                result != null ? result.getClass().getSimpleName() : "null");

        // 处理 null 结果
        if (result == null) {
            return "无结果";
        }

        // 自定义 Customer 对象的格式化
        if (result instanceof Customer customer) {
            String formatted = String.format("""
                    ╔══════════════════════════════════════╗
                    ║           客户详细信息                ║
                    ╠══════════════════════════════════════╣
                    ║ ID:      %-28s║
                    ║ 姓名:    %-28s║
                    ║ 邮箱:    %-28s║
                    ║ 租户:    %-28s║
                    ╚══════════════════════════════════════╝
                    """,
                    customer.id(),
                    customer.name(),
                    customer.email(),
                    customer.tenantId() != null ? customer.tenantId() : "未指定");

            log.debug("转换 Customer 对象成功");
            return formatted;
        }

        // 处理数字类型 - 添加格式化
        if (result instanceof Number number) {
            String formatted = "数值结果: " + number;
            log.debug("转换 Number 对象: {}", number);
            return formatted;
        }

        // 处理布尔类型
        if (result instanceof Boolean bool) {
            String formatted = bool ? "✅ 是" : "❌ 否";
            log.debug("转换 Boolean 对象: {}", bool);
            return formatted;
        }

        // 其他类型使用默认的 toString 方法
        log.debug("使用默认转换: {}", result.getClass().getSimpleName());
        return result.toString();
    }
}
