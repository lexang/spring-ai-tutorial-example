package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 聊天控制器 - 演示 Spring AI ChatClient 的基本用法
 *
 * @author Spring AI Tutorial
 */
@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatClient chatClient;

    /**
     * 构造器注入 ChatClient.Builder
     * Spring Boot 自动配置会提供 ChatClient.Builder Bean
     *
     * @param chatClientBuilder 由 Spring Boot 自动配置提供
     */
    public ChatController(ChatClient.Builder chatClientBuilder) {
        // 构建 ChatClient 实例
        this.chatClient = chatClientBuilder
                .defaultSystem("你是一个友好的 AI 助手，用中文回答问题。回答要简洁明了。")
                .build();
    }

    /**
     * 简单的聊天接口 - 同步调用
     * <p>
     * 示例: http://localhost:8080/chat?message=你好
     *
     * @param message 用户输入的消息
     * @return AI 的响应
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "你好") String message) {
        requireMessage(message);
        log.info("收到聊天请求，消息长度：{}", message.length());

        // 使用 ChatClient 的 Fluent API
        try {
            String response = requireResponse(this.chatClient.prompt()
                    .user(message)
                    .call()
                    .content()); // content() 才触发同步模型调用。
            log.info("AI 响应长度：{} 字符", response.length());
            return response;
        } catch (AiServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiServiceException("AI 服务暂时不可用，请稍后重试", exception);
        }
    }

    /**
     * 流式聊天接口 - 流式响应
     * <p>
     * 使用 Server-Sent Events (SSE) 实现流式输出
     * 示例: curl -N http://localhost:8080/chat/stream?message=讲个故事
     *
     * @param message 用户输入的消息
     * @return 流式响应
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam(defaultValue = "讲个简短的故事") String message) {
        requireMessage(message);
        log.info("收到流式请求，消息长度：{}", message.length());
        return Flux.defer(() -> this.chatClient.prompt()
                        .user(message).stream().content())
                .filter(chunk -> !chunk.isEmpty())
                .switchIfEmpty(Flux.error(new AiServiceException("模型没有返回文本内容")))
                .onErrorMap(exception -> exception instanceof AiServiceException ? exception
                        : new AiServiceException("AI 流式服务暂时不可用，请稍后重试", exception));
    }

    /**
     * 带错误处理的聊天接口
     * <p>
     * 示例: http://localhost:8080/chat/safe?message=你好
     *
     * @param message 用户输入的消息
     * @return HTTP 响应，包含状态码和内容
     */
    @GetMapping("/chat/safe")
    public ResponseEntity<String> chatSafe(@RequestParam String message) {
        return ResponseEntity.ok(chat(message));
    }

    /**
     * 使用请求级 Options 调节回答的创造性。
     *
     * @param message 用户输入的消息
     * @param temperature 温度参数，范围为 0.0 到 2.0
     * @return AI 的响应
     */
    @GetMapping("/chat/creative")
    public String chatCreative(@RequestParam String message,
            @RequestParam(defaultValue = "0.7") double temperature) {
        requireMessage(message);
        if (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature 必须是 0.0 到 2.0 之间的有限数值");
        }

        try {
            return requireResponse(this.chatClient.prompt()
                    .user(message)
                    .options(OpenAiChatOptions.builder().temperature(temperature))
                    .call()
                    .content());
        } catch (AiServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiServiceException("AI 服务暂时不可用，请稍后重试", exception);
        }
    }

    private void requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
    }

    private String requireResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new AiServiceException("模型没有返回文本内容");
        }
        return response;
    }

    /**
     * 健康检查接口
     *
     * @return 应用状态
     */
    @GetMapping("/")
    public String home() {
        return "Spring AI Getting Started 示例正在运行!\n\n" +
                "可用接口:\n" +
                "- GET /chat?message=xxx - 简单对话\n" +
                "- GET /chat/stream?message=xxx - 流式对话\n" +
                "- GET /chat/safe?message=xxx - 带错误处理的对话\n" +
                "- GET /chat/creative?message=xxx&temperature=0.7 - 可调节创造性的对话\n";
    }
}
