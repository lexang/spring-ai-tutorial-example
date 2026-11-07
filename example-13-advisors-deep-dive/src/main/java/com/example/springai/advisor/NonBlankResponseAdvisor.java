package com.example.springai.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.UsageAccumulator;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 同步递归示例：只对空白回答追加有限重试，累计每轮模型用量。
 * 不实现 StreamAdvisor；流式场景不会执行此校验，应使用明确的聚合校验方案。
 */
public final class NonBlankResponseAdvisor implements CallAdvisor {

    public static final String ATTEMPT = "nonBlankAttempt";

    private final int maxRetries;

    private final int order;

    public NonBlankResponseAdvisor(int maxRetries, int order) {
        if (maxRetries < 0 || maxRetries > 3) {
            throw new IllegalArgumentException("追加重试次数必须在 0 到 3 之间");
        }
        this.maxRetries = maxRetries;
        this.order = order;
    }

    @Override
    public String getName() {
        return "NonBlankResponseAdvisor";
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // 调用级状态只能放在方法内，不能由多个请求共享。
        UsageAccumulator usage = new UsageAccumulator();
        for (int attempt = 0; attempt <= this.maxRetries; attempt++) {
            Prompt prompt = attempt == 0 ? request.prompt()
                    : request.prompt().augmentUserMessage(user -> user.mutate()
                        .text(user.getText() + "\n上次回答为空，请给出非空文本回答。")
                        .build());
            ChatClientRequest roundRequest = request.mutate()
                .prompt(prompt)
                .context(ATTEMPT, attempt)
                .build();
            // 每轮都复制新的下游队列；模型异常直接传播，不作为空白回答重试。
            ChatClientResponse response = chain.copy(this).nextCall(roundRequest);
            ChatResponse chatResponse = response.chatResponse();
            usage.addRoundResponse(chatResponse);

            // 工具请求交还外层 ToolCallingAdvisor，不能重复触发同一工具回合。
            if (chatResponse != null && (chatResponse.hasToolCalls() || hasText(chatResponse))) {
                return usage.applyAccumulatedUsage(response);
            }
        }
        throw new IllegalStateException("模型在 " + (1 + this.maxRetries) + " 次调用后仍未返回非空回答");
    }

    private static boolean hasText(ChatResponse response) {
        return response.getResult() != null && response.getResult().getOutput() != null
                && response.getResult().getOutput().getText() != null
                && !response.getResult().getOutput().getText().isBlank();
    }

}
