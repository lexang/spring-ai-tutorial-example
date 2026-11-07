package com.example.springai.converter;

import com.example.springai.converter.CustomResultConverter;
import com.example.springai.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 使用自定义 ResultConverter 的工具示例
 */
@Component
public class CustomerToolsWithConverter {

    private static final Logger log = LoggerFactory.getLogger(CustomerToolsWithConverter.class);

    private final Map<Long, Customer> customers = new ConcurrentHashMap<>();

    public CustomerToolsWithConverter() {
        // 初始化示例数据
        customers.put(1L, new Customer(1L, "张三", "zhangsan@example.com", "租户A"));
        customers.put(2L, new Customer(2L, "李四", "lisi@example.com", "租户B"));
        customers.put(3L, new Customer(3L, "王五", "wangwu@example.com", "租户A"));
    }

    /**
     * 查询客户 - 使用自定义 ResultConverter
     *
     * resultConverter 属性指定使用自定义的转换器
     * 返回的 Customer 对象会被格式化为更友好的文本
     */
    @Tool(
            description = "根据 ID 查询客户信息,返回格式化的客户详情",
            resultConverter = CustomResultConverter.class  // 使用自定义转换器
    )
    public Customer getCustomerWithConverter(
            @ToolParam(description = "客户 ID")
            Long id) {

        log.info("工具被调用: getCustomerWithConverter (使用自定义转换器), id={}", id);

        Customer customer = customers.get(id);

        if (customer == null) {
            log.info("客户不存在: id={}", id);
            return null;  // 将被转换为 "无结果"
        }

        log.info("查询客户成功,将使用 CustomResultConverter 格式化");
        return customer;  // 将被 CustomResultConverter 格式化
    }

    /**
     * 查询客户 - 默认转换器
     *
     * 不指定 resultConverter,使用默认的 JSON 序列化
     */
    @Tool(description = "根据 ID 查询客户信息,返回 JSON 格式")
    public Customer getCustomerDefault(
            @ToolParam(description = "客户 ID")
            Long id) {

        log.info("工具被调用: getCustomerDefault (默认转换器), id={}", id);

        Customer customer = customers.get(id);

        if (customer == null) {
            log.info("客户不存在: id={}", id);
            return null;
        }

        log.info("查询客户成功,将使用默认 JSON 序列化");
        return customer;  // 将被序列化为 JSON
    }

    /**
     * 检查客户是否存在 - 演示 Boolean 转换
     */
    @Tool(
            description = "检查指定 ID 的客户是否存在",
            resultConverter = CustomResultConverter.class
    )
    public Boolean customerExists(
            @ToolParam(description = "客户 ID")
            Long id) {

        log.info("工具被调用: customerExists, id={}", id);

        boolean exists = customers.containsKey(id);

        log.info("检查结果: id={}, exists={}", id, exists);
        return exists;  // 将被转换为 "✅ 是" 或 "❌ 否"
    }

    /**
     * 统计客户数量 - 演示 Number 转换
     */
    @Tool(
            description = "统计客户总数",
            resultConverter = CustomResultConverter.class
    )
    public Integer countCustomers() {
        log.info("工具被调用: countCustomers");

        int count = customers.size();

        log.info("客户总数: {}", count);
        return count;  // 将被转换为 "数值结果: X"
    }
}
