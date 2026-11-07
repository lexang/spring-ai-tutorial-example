package com.example.springai.model;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/** 仅用于离线演示，确定性触发订单工具并读取 RAG/Memory 上下文。 */
public final class OfflineCustomerChatModel implements ChatModel {

    private static final Pattern ORDER_ID = Pattern.compile("ORD-[0-9]+", Pattern.CASE_INSENSITIVE);

    private static final String CONTEXT_SEPARATOR = "---------------------";

    private final AtomicInteger callCount = new AtomicInteger();

    @Override
    public ChatResponse call(Prompt prompt) {
        this.callCount.incrementAndGet();
        ToolResponseMessage toolResponse = lastToolResponse(prompt.getInstructions());
        if (toolResponse != null) {
            String result = toolResponse.getResponses().stream()
                .map(ToolResponseMessage.ToolResponse::responseData)
                .findFirst()
                .orElse("工具没有返回结果");
            return response("订单查询结果：" + result);
        }

        String userText = prompt.getUserMessage().getText();
        String question = extractQuestion(userText);
        Matcher matcher = ORDER_ID.matcher(question);
        if (matcher.find() && containsOrderIntent(question)) {
            String arguments = "{\"orderId\":\"" + matcher.group().toUpperCase() + "\"}";
            AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "offline-order-call", "function", "getOrderStatus", arguments)))
                .build();
            return new ChatResponse(List.of(new Generation(message)));
        }

        if (question.contains("上一个问题")) {
            String previous = previousQuestion(prompt.getInstructions(), userText);
            return response(previous == null ? "当前会话中没有更早的问题。" : "你上一个问题是：" + previous);
        }
        String context = extractRagContext(userText);
        return response(context.isBlank()
                ? "知识库中没有找到可确认的信息，请转人工客服。"
                : "根据客服知识库：" + abbreviate(context, 320));
    }

    @Override
    public ToolCallingChatOptions getOptions() {
        return DefaultToolCallingChatOptions.builder().build();
    }

    public int getCallCount() {
        return this.callCount.get();
    }

    private ToolResponseMessage lastToolResponse(List<Message> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index) instanceof ToolResponseMessage toolResponse) {
                return toolResponse;
            }
        }
        return null;
    }

    private String previousQuestion(List<Message> messages, String currentText) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            if (message instanceof UserMessage && !message.getText().equals(currentText)) {
                return extractQuestion(message.getText());
            }
        }
        return null;
    }

    private boolean containsOrderIntent(String value) {
        return value.contains("订单") || value.contains("物流") || value.contains("发货");
    }

    private String extractQuestion(String value) {
        int marker = value.indexOf("Context information is below.");
        return (marker >= 0 ? value.substring(0, marker) : value).trim();
    }

    private String extractRagContext(String value) {
        int descriptionMarker = value.indexOf(CONTEXT_SEPARATOR);
        if (descriptionMarker < 0) {
            return "";
        }
        int startMarker = value.indexOf(CONTEXT_SEPARATOR,
                descriptionMarker + CONTEXT_SEPARATOR.length());
        if (startMarker < 0) {
            return "";
        }
        int contentStart = startMarker + CONTEXT_SEPARATOR.length();
        int endMarker = value.indexOf(CONTEXT_SEPARATOR, contentStart);
        if (endMarker < 0) {
            return "";
        }
        return value.substring(contentStart, endMarker).trim();
    }

    private String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

}
