package com.example.springai;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCallLimitsTest {

    @Test
    void defaultsHaveFinitePerToolAndTotalLimits() {
        assertThat(DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL).isEqualTo(40);
        assertThat(DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS).isEqualTo(150);
    }

    @Test
    void aBreachedLimitDoesNotExecuteTheExtraCall() {
        AtomicInteger executed = new AtomicInteger();
        var callback = FunctionToolCallback.builder("count",
                (CountRequest request) -> executed.incrementAndGet())
                .description("计数测试工具")
                .inputType(CountRequest.class)
                .build();
        Prompt prompt = new Prompt("执行计数",
                ToolCallingChatOptions.builder().toolCallbacks(callback).build());
        ChatResponse response = new ChatResponse(List.of(new Generation(
                AssistantMessage.builder().content("").toolCalls(List.of(
                        new AssistantMessage.ToolCall("call-1", "function", "count", "{\"label\":\"一\"}"),
                        new AssistantMessage.ToolCall("call-2", "function", "count", "{\"label\":\"二\"}")))
                        .build())));
        var manager = DefaultToolCallingManager.builder().maxCallsPerTool(1).build();

        assertThatThrownBy(() -> manager.executeToolCalls(prompt, response))
                .isInstanceOf(ToolCallLimitExceededException.class);
        assertThat(executed).hasValue(1);
    }

    record CountRequest(String label) {
    }
}
