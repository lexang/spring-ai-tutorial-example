package com.example.springai.tool;

import java.util.Locale;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class CustomerOrderTools {

    public static final String CUSTOMER_ID = "customerId";

    private final Map<String, Order> orders = Map.of(
            "ORD-1001", new Order("ORD-1001", "customer-a", "已发货", "SF123456789", "预计明天送达"),
            "ORD-2002", new Order("ORD-2002", "customer-b", "待付款", null, "付款后安排发货"));

    @Tool(description = "查询当前已认证客户自己的订单状态和物流信息")
    public OrderView getOrderStatus(
            @ToolParam(description = "订单号，例如 ORD-1001") String orderId,
            ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            throw new IllegalStateException("缺少已认证客户上下文");
        }
        Object customerValue = toolContext.getContext().get(CUSTOMER_ID);
        if (!(customerValue instanceof String customerId) || customerId.isBlank()) {
            throw new IllegalStateException("缺少已认证客户上下文");
        }
        String normalized = orderId == null ? "" : orderId.trim().toUpperCase(Locale.ROOT);
        Order order = this.orders.get(normalized);
        if (order == null || !order.customerId().equals(customerId)) {
            return new OrderView(normalized, "未找到该客户的订单", null, null);
        }
        return new OrderView(order.orderId(), order.status(), order.trackingNumber(), order.note());
    }

    public record OrderView(String orderId, String status, String trackingNumber, String note) {
    }

    private record Order(String orderId, String customerId, String status, String trackingNumber, String note) {
    }

}
