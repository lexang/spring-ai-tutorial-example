package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenControllerTest {

    @Test
    void localEstimateDoesNotCallModelAndRejectsBlankInput() {
        ChatModel model = prompt -> { throw new AssertionError("本地估算不应调用模型"); };
        TokenController controller = new TokenController(ChatClient.builder(model));
        assertThat((Integer) controller.estimateTokens("你好，Spring AI").get("estimatedTokens")).isPositive();
        assertThatThrownBy(() -> controller.estimateTokens(" ")).hasMessage("text 不能为空");
    }

    @Test
    void emptyUsageDoesNotBecomeZeroCost() {
        ChatModel model = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("回答"))));
        TokenController controller = new TokenController(ChatClient.builder(model));
        assertThatThrownBy(() -> controller.getTokenUsage("问题"))
                .isInstanceOf(IllegalStateException.class).hasMessage("模型响应未提供 Token 使用量");
    }

    @Test
    void calculatesDemoCostWithInputAndOutputRates() {
        assertThat(TokenController.calculateCost(10, 500))
                .isCloseTo(0.0003015, org.assertj.core.data.Offset.offset(1.0e-12));
    }

    @Test
    void rejectsNegativeTokenCounts() {
        assertThatThrownBy(() -> TokenController.calculateCost(-1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token 数不能为负数");
    }
}
