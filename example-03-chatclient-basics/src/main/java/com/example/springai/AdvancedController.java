package com.example.springai;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 高级用法演示：错误处理、完整响应和安全调试日志。
 */
@RestController
@RequestMapping("/advanced")
public class AdvancedController {

    private static final Logger log = LoggerFactory.getLogger(AdvancedController.class);

    private final ChatClient chatClient;

    private final ChatClient chatClientWithLogger;

    private final UsageTimingAdvisor metrics = new UsageTimingAdvisor();

    public AdvancedController(ChatClient.Builder builder) {
        this.chatClient = builder.clone().defaultAdvisors(this.metrics).build();
        this.chatClientWithLogger = builder.clone()
                .defaultAdvisors(this.metrics, ClientLoggers.summaries())
                .build();
    }

    @GetMapping("/metrics")
    public UsageTimingAdvisor.Snapshot metrics() {
        return this.metrics.snapshot();
    }

    @GetMapping("/with-error-handling")
    public ResponseEntity<Map<String, Object>> withErrorHandling(@RequestParam String message) {
        try {
            String response = ChatResponseSupport.requireContent(this.chatClient.prompt()
                    .user(DemoRequestValidation.requireText(message, "message"))
                    .call()
                    .content());
            return ResponseEntity.ok(Map.of("success", true, "response", response));
        }
        catch (InvalidDemoRequestException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", exception.getMessage()));
        }
        catch (Exception exception) {
            log.error("AI 调用失败，异常类型={}", exception.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "success", false,
                    "error", "AI 服务暂时不可用，请稍后重试"));
        }
    }

    @GetMapping("/with-logger")
    public String withLogger(@RequestParam String message) {
        String actualMessage = DemoRequestValidation.requireText(message, "message");
        log.info("开始处理请求，字符数={}", actualMessage.length());
        String response = ChatResponseSupport.requireContent(this.chatClientWithLogger.prompt()
                .user(actualMessage)
                .call()
                .content());
        log.info("请求处理完成");
        return response;
    }

    /**
     * 自定义日志只记录消息数量、响应长度和 Usage，不输出 Prompt/Completion 正文。
     */
    @GetMapping("/with-custom-logger")
    public String withCustomLogger(@RequestParam String message) {
        SimpleLoggerAdvisor customLogger = SimpleLoggerAdvisor.builder()
                .requestToString(AdvancedController::summarizeRequest)
                .responseToString(AdvancedController::summarizeResponse)
                .order(0)
                .build();

        return ChatResponseSupport.requireContent(this.chatClient.prompt()
                .advisors(customLogger)
                .user(DemoRequestValidation.requireText(message, "message"))
                .call()
                .content());
    }

    @GetMapping("/chat-response")
    public Map<String, Object> chatResponse(@RequestParam String message) {
        ChatResponse response = this.chatClient.prompt()
                .user(DemoRequestValidation.requireText(message, "message"))
                .call()
                .chatResponse();
        return ChatResponseSupport.responseSummary(response);
    }

    @GetMapping("/client-response")
    public Map<String, Object> clientResponse(@RequestParam String message) {
        ChatClientResponse clientResponse = this.chatClient.prompt()
                .user(DemoRequestValidation.requireText(message, "message"))
                .call()
                .chatClientResponse();

        Map<String, Object> result = ChatResponseSupport.responseSummary(
                ChatResponseSupport.requireResponse(clientResponse));
        result.put("contextKeys", clientResponse.context().keySet());
        return result;
    }

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of(
                "with-error-handling", "错误处理: /advanced/with-error-handling?message=xxx",
                "with-logger", "默认 SimpleLoggerAdvisor: /advanced/with-logger?message=xxx",
                "with-custom-logger", "脱敏自定义日志: /advanced/with-custom-logger?message=xxx",
                "client-response", "ChatClientResponse: /advanced/client-response?message=xxx");
    }

    private static String summarizeRequest(ChatClientRequest request) {
        if (request == null || request.prompt() == null) {
            return "request=null";
        }
        return "messageCount=" + request.prompt().getInstructions().size();
    }

    private static String summarizeResponse(ChatResponse response) {
        if (response == null) {
            return "response=null";
        }
        String content = response.getResult() != null
                ? response.getResult().getOutput().getText() : null;
        Object totalTokens = ChatResponseSupport.usageSummary(response).get("totalTokens");
        return "contentLength=" + (content != null ? content.length() : 0)
                + ", totalTokens=" + totalTokens;
    }

}
