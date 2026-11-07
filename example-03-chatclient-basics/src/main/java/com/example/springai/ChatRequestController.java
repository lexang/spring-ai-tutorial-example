package com.example.springai;

import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 博客主示例：一个 POST 请求结构，选择客户端默认值并读取同步/SSE 完整响应。
 */
@RestController
@RequestMapping("/api/chat")
public final class ChatRequestController {

    private final ChatClient technicalClient;

    private final ChatClient conciseClient;

    public ChatRequestController(ChatClient.Builder builder) {
        this.technicalClient = builder.clone()
                .defaultSystem("你是 Java 技术顾问，请用中文给出准确解释；不能确认的信息请明确说明。")
                .build();
        this.conciseClient = this.technicalClient.mutate()
                .defaultSystem("你是 Java 技术顾问，请用不超过三句话的中文回答；不能确认的信息请明确说明。")
                .build();
    }

    @PostMapping
    public ChatReply chat(@RequestBody ChatRequest request) {
        ValidRequest valid = validate(request);
        ChatClientResponse result = buildRequest(valid).call().chatClientResponse();
        ChatResponse response = ChatResponseSupport.requireResponse(result);
        return reply(result, response, ChatResponseSupport.requireContent(response));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatReply> stream(@RequestBody ChatRequest request) {
        ValidRequest valid = validate(request);
        // 每次订阅都创建请求与新 Advisor 链，避免复用已消费的队列。
        return Flux.defer(() -> buildRequest(valid).stream().chatClientResponse())
                .map(result -> {
                    ChatResponse response = ChatResponseSupport.requireResponse(result);
                    String text = response.getResult() == null ? "" : response.getResult().getOutput().getText();
                    // 只有 Usage、没有 Generation 的块也属于合法流式元数据。
                    return reply(result, response, text == null ? "" : text);
                })
                .switchIfEmpty(Flux.error(new EmptyChatResponseException("模型返回空响应流")));
    }

    private ChatClient.ChatClientRequestSpec buildRequest(ValidRequest request) {
        ChatClient client = request.style() == ReplyStyle.CONCISE ? this.conciseClient : this.technicalClient;
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().maxTokens(request.maxTokens());
        if (request.temperature() != null) {
            options.temperature(request.temperature());
        }
        return client.prompt().user(request.message()).options(options)
                .advisors(spec -> spec.param("requestId", UUID.randomUUID().toString()));
    }

    private static ValidRequest validate(ChatRequest request) {
        if (request == null) {
            throw new InvalidDemoRequestException("请求体不能为空");
        }
        String message = DemoRequestValidation.requireText(request.message(), "message");
        if (message.length() > 8000) {
            throw new InvalidDemoRequestException("message 不能超过 8000 个字符");
        }
        int maxTokens = request.maxTokens() == null ? 500
                : DemoRequestValidation.requirePositiveTokenLimit(request.maxTokens());
        if (maxTokens > 4096) {
            throw new InvalidDemoRequestException("本例 maxTokens 不能超过 4096");
        }
        Double temperature = request.temperature() == null ? null
                : DemoRequestValidation.requireTemperature(request.temperature());
        return new ValidRequest(message, maxTokens, temperature,
                request.style() == null ? ReplyStyle.TECHNICAL : request.style());
    }

    private static ChatReply reply(ChatClientResponse result, ChatResponse response, String text) {
        Usage usage = response.getMetadata().getUsage();
        boolean reported = usage != null && !(usage instanceof EmptyUsage);
        UsageView usageView = new UsageView(reported,
                reported ? usage.getPromptTokens() : null,
                reported ? usage.getCompletionTokens() : null,
                reported ? usage.getTotalTokens() : null);
        String finishReason = response.getResult() == null ? null
                : response.getResult().getMetadata().getFinishReason();
        return new ChatReply(text, response.getMetadata().getId(), response.getMetadata().getModel(),
                finishReason, usageView, String.valueOf(result.context().getOrDefault("requestId", "")));
    }

    public enum ReplyStyle { TECHNICAL, CONCISE }

    public record ChatRequest(String message, Integer maxTokens, Double temperature, ReplyStyle style) { }

    private record ValidRequest(String message, int maxTokens, Double temperature, ReplyStyle style) { }

    public record UsageView(boolean reported, Integer promptTokens, Integer completionTokens, Integer totalTokens) { }

    public record ChatReply(String content, String responseId, String model, String finishReason,
            UsageView usage, String requestId) { }

}
