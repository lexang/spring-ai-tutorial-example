package com.example.springai.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * 只用于离线契约测试的确定性模型。
 *
 * <p>它识别工作流中的内部标记，模拟一个会返回分类、计划和评估 JSON 的模型。
 * 真实 profile 会切换为供应商 ChatModel；工作流编排代码本身不依赖这个模拟分支。</p>
 */
public final class OfflineAgentChatModel implements ChatModel {

    private static final String MODEL = "offline-agent-model";

    private final ObservationRegistry observationRegistry;

    private final TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();

    public OfflineAgentChatModel(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
            .prompt(prompt)
            .provider("offline")
            .build();
        return ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
            .observation(null, new DefaultChatModelObservationConvention(), () -> observationContext,
                this.observationRegistry)
            .observe(() -> {
                ChatResponse response = response(prompt.getUserMessage().getText());
                observationContext.setResponse(response);
                return response;
            });
    }

    @Override
    public ChatOptions getOptions() {
        return ChatOptions.builder().model(MODEL).build();
    }

    private ChatResponse response(String promptText) {
        String lower = promptText.toLowerCase(Locale.ROOT);
        String content;
        if (lower.contains("[plan]")) {
            content = "{\"subtasks\":[\"提取关键约束\",\"生成面向用户的说明\"]}";
        }
        else if (lower.contains("[classify]")) {
            content = classify(lower);
        }
        else if (lower.contains("[evaluate]")) {
            content = lower.contains("改进稿")
                    ? "{\"pass\":true,\"feedback\":\"改进稿满足验收条件\"}"
                    : "{\"pass\":false,\"feedback\":\"请补充边界条件和错误处理\"}";
        }
        else if (lower.contains("[refine]")) {
            content = "改进稿：补充边界条件、错误处理和可观测性说明。";
        }
        else if (lower.contains("[generate]")) {
            content = "初稿：实现一个清晰、可测试的工作流。";
        }
        else if (lower.contains("[worker]")) {
            content = "Worker 结果：已完成分配的子任务。";
        }
        else if (lower.contains("[aggregate]")) {
            content = "并行汇总：多个独立视角已合并，并保留可追踪的步骤。";
        }
        else if (lower.contains("[chain]")) {
            content = "链式步骤结果：" + promptText.substring(Math.max(0, promptText.length() - 24));
        }
        else {
            content = "离线 Agent 响应：" + promptText;
        }

        int promptTokens = Math.max(1, this.tokenCountEstimator.estimate(promptText));
        int completionTokens = Math.max(1, Math.min(32, content.length() / 4));
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
            .id("offline-agent-response")
            .model(MODEL)
            .usage(new DefaultUsage(promptTokens, completionTokens, null,
                Map.of("provider", "offline", "workflow", "agentic-patterns")))
            .build();
        Generation generation = new Generation(new AssistantMessage(content),
            ChatGenerationMetadata.builder().finishReason("STOP").build());
        return new ChatResponse(List.of(generation), metadata);
    }

    private String classify(String prompt) {
        // 分类说明本身含有全部标签，只检查最后一行用户输入。
        prompt = prompt.substring(prompt.lastIndexOf('\n') + 1);
        if (prompt.contains("账单") || prompt.contains("billing") || prompt.contains("扣费")) {
            return "billing";
        }
        if (prompt.contains("错误") || prompt.contains("technical") || prompt.contains("故障")) {
            return "technical";
        }
        return "general";
    }

}
