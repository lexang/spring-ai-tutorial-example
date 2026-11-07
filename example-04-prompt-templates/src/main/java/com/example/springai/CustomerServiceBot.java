package com.example.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能客服服务
 * <p>
 * 演示：
 * - 上下文感知
 * - 客户信息集成
 * - 订单历史处理
 */
@Service
public class CustomerServiceBot {

    private final ChatClient chatClient;
    private final PromptTemplate csTemplate;

    public CustomerServiceBot(
            ChatClient.Builder builder,
            @Value("classpath:/prompts/customer-service.st") Resource template) {

        this.chatClient = builder.build();
        this.csTemplate = new PromptTemplate(template);
    }

    /**
     * 处理客户查询
     */
    public String handleQuery(CustomerQuery query) {
        if (query == null) {
            throw new InvalidTemplateRequestException("客户请求不能为空");
        }
        String customerName = TemplateRequestValidation.requireText(query.customerName(), "customerName");
        String customerLevel = TemplateRequestValidation.requireText(query.customerLevel(), "customerLevel");
        String queryType = TemplateRequestValidation.requireText(query.type(), "type");
        String message = TemplateRequestValidation.requireText(query.message(), "message");
        Prompt prompt = csTemplate.create(Map.of(
                "customerName", customerName,
                "customerLevel", customerLevel,
                "queryType", queryType,
                "query", message,
                "orderHistory", formatOrderHistory(query.orders()),
                "currentTime", LocalDateTime.now().toString()
        ));

        return TemplateResponseSupport.requireContent(chatClient.prompt(prompt).call().content());
    }

    /**
     * 格式化订单历史
     */
    private String formatOrderHistory(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return "暂无订单记录";
        }

        return orders.stream()
                .map(order -> {
                    if (order == null) {
                        throw new InvalidTemplateRequestException("orders 不能包含 null");
                    }
                    String id = TemplateRequestValidation.requireText(order.id(), "order.id");
                    String status = TemplateRequestValidation.requireText(order.status(), "order.status");
                    if (!Double.isFinite(order.amount()) || order.amount() < 0) {
                        throw new InvalidTemplateRequestException("order.amount 必须是非负有限数值");
                    }
                    return String.format("订单号: %s, 金额: %.2f元, 状态: %s",
                            id, order.amount(), status);
                })
                .collect(Collectors.joining("\n"));
    }
}

/**
 * 客户查询请求
 */
record CustomerQuery(
        String customerName,
        String customerLevel,
        String type,
        String message,
        List<Order> orders
) {
}

/**
 * 订单信息
 */
record Order(
        String id,
        double amount,
        String status
) {
}
