package com.example.springai.context;

import com.example.springai.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户工具类 - 演示 ToolContext 的使用
 *
 * ToolContext 用于传递上下文信息(如租户ID、用户权限等)
 * 这些信息不由 AI 模型提供,而是由应用程序提供
 */
@Component
public class CustomerTools {

    private static final Logger log = LoggerFactory.getLogger(CustomerTools.class);

    // 模拟多租户客户数据库
    private final Map<String, Map<Long, Customer>> tenantCustomers = new ConcurrentHashMap<>();

    public CustomerTools() {
        // 初始化示例数据 - 租户 A
        Map<Long, Customer> tenantA = new ConcurrentHashMap<>();
        tenantA.put(1L, new Customer(1L, "张三", "zhangsan@tenant-a.com", "租户A"));
        tenantA.put(2L, new Customer(2L, "李四", "lisi@tenant-a.com", "租户A"));
        tenantCustomers.put("tenant-a", tenantA);

        // 初始化示例数据 - 租户 B
        Map<Long, Customer> tenantB = new ConcurrentHashMap<>();
        tenantB.put(1L, new Customer(1L, "王五", "wangwu@tenant-b.com", "租户B"));
        tenantB.put(2L, new Customer(2L, "赵六", "zhaoliu@tenant-b.com", "租户B"));
        tenantCustomers.put("tenant-b", tenantB);

        log.info("CustomerTools 初始化完成,已加载 {} 个租户的数据", tenantCustomers.size());
    }

    /**
     * 根据客户 ID 查询客户信息
     *
     * 使用 ToolContext 获取租户信息,确保数据隔离
     *
     * @param customerId 客户 ID
     * @param toolContext 工具上下文,包含租户 ID 等信息
     * @return 客户信息
     */
    @Tool(description = "根据客户 ID 查询客户的详细信息")
    public String getCustomerInfo(
            @ToolParam(description = "客户 ID") Long customerId,
            ToolContext toolContext) {

        log.info("工具被调用: getCustomerInfo, customerId={}", customerId);

        // 从上下文中获取租户 ID
        String tenantId = (String) toolContext.getContext().get("tenantId");
        String userId = (String) toolContext.getContext().get("userId");

        log.info("上下文信息: tenantId={}, userId={}", tenantId, userId);

        // 安全检查: 验证租户 ID
        if (tenantId == null || tenantId.isEmpty()) {
            log.warn("缺少租户信息");
            return "错误: 缺少租户信息,无法查询";
        }

        // 从租户数据中查询客户
        Map<Long, Customer> customers = tenantCustomers.get(tenantId);
        if (customers == null) {
            log.warn("租户不存在: {}", tenantId);
            return "错误: 租户不存在";
        }

        Customer customer = customers.get(customerId);
        if (customer == null) {
            log.info("客户不存在: customerId={}, tenantId={}", customerId, tenantId);
            return "未找到客户 ID: " + customerId;
        }

        String result = String.format("""
                客户信息:
                ID: %d
                姓名: %s
                邮箱: %s
                所属租户: %s
                查询用户: %s
                """,
                customer.id(),
                customer.name(),
                customer.email(),
                customer.tenantId(),
                userId != null ? userId : "未知");

        log.info("查询客户成功: customerId={}, tenantId={}", customerId, tenantId);
        return result;
    }

    /**
     * 列出当前租户的所有客户
     *
     * @param toolContext 工具上下文
     * @return 客户列表
     */
    @Tool(description = "列出当前租户的所有客户")
    public String listCustomers(ToolContext toolContext) {
        log.info("工具被调用: listCustomers");

        String tenantId = (String) toolContext.getContext().get("tenantId");

        if (tenantId == null || tenantId.isEmpty()) {
            return "错误: 缺少租户信息";
        }

        Map<Long, Customer> customers = tenantCustomers.get(tenantId);
        if (customers == null || customers.isEmpty()) {
            return "当前租户没有客户";
        }

        StringBuilder result = new StringBuilder();
        result.append("租户 ").append(tenantId).append(" 的客户列表:\n\n");

        customers.values().stream()
                .sorted(Comparator.comparing(Customer::id))
                .forEach(c -> result.append(String.format(
                        "ID: %d, 姓名: %s, 邮箱: %s\n",
                        c.id(), c.name(), c.email()
                )));

        log.info("列出客户成功: tenantId={}, 共 {} 个客户", tenantId, customers.size());
        return result.toString();
    }

    /**
     * 搜索客户
     *
     * @param name 客户姓名
     * @param toolContext 工具上下文
     * @return 匹配的客户列表
     */
    @Tool(description = "在当前租户中搜索客户")
    public String searchCustomers(
            @ToolParam(description = "客户姓名,支持模糊匹配") String name,
            ToolContext toolContext) {

        log.info("工具被调用: searchCustomers, name={}", name);

        String tenantId = (String) toolContext.getContext().get("tenantId");

        if (tenantId == null || tenantId.isEmpty()) {
            return "错误: 缺少租户信息";
        }

        Map<Long, Customer> customers = tenantCustomers.get(tenantId);
        if (customers == null) {
            return "租户不存在";
        }

        List<Customer> matched = customers.values().stream()
                .filter(c -> c.name().contains(name))
                .sorted(Comparator.comparing(Customer::id))
                .toList();

        if (matched.isEmpty()) {
            return "未找到匹配的客户";
        }

        StringBuilder result = new StringBuilder();
        result.append("找到 ").append(matched.size()).append(" 个匹配的客户:\n\n");

        matched.forEach(c -> result.append(String.format(
                "ID: %d, 姓名: %s, 邮箱: %s\n",
                c.id(), c.name(), c.email()
        )));

        log.info("搜索客户成功: tenantId={}, name={}, 找到 {} 个",
                tenantId, name, matched.size());
        return result.toString();
    }
}
