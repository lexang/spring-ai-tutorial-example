package com.example.springai;

import java.util.Map;

import com.example.springai.tool.CustomerOrderTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerOrderToolsTest {

    private final CustomerOrderTools tools = new CustomerOrderTools();

    @Test
    void authenticatedCustomerCanReadOwnOrder() {
        var result = this.tools.getOrderStatus("ord-1001",
                new ToolContext(Map.of(CustomerOrderTools.CUSTOMER_ID, "customer-a")));

        assertThat(result.status()).isEqualTo("已发货");
        assertThat(result.trackingNumber()).isEqualTo("SF123456789");
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() {
        var result = this.tools.getOrderStatus("ORD-1001",
                new ToolContext(Map.of(CustomerOrderTools.CUSTOMER_ID, "customer-b")));

        assertThat(result.status()).isEqualTo("未找到该客户的订单");
        assertThat(result.trackingNumber()).isNull();
    }

    @Test
    void missingTrustedContextFailsClosed() {
        assertThatThrownBy(() -> this.tools.getOrderStatus("ORD-1001", new ToolContext(Map.of())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("缺少已认证客户上下文");
    }

}
