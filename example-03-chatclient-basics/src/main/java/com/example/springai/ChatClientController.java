package com.example.springai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

/**
 * ChatClient 基础用法演示。
 */
@RestController
@RequestMapping("/chatclient")
public class ChatClientController {

    private final ChatClient chatClient;

    private final ChatClient technicalClient;

    private final ChatClient creativeClient;

    public ChatClientController(ChatClient.Builder builder) {
        // 注入自动配置的 prototype Builder，并克隆后分别创建客户端。
        this.chatClient = builder.clone().build();

        this.technicalClient = builder.clone()
                .defaultSystem("你是一个技术专家，回答要专业准确")
                .defaultOptions(OpenAiChatOptions.builder().temperature(0.2))
                .build();

        this.creativeClient = builder.clone()
                .defaultSystem("你是一个创意作家，回答要富有想象力")
                .defaultOptions(OpenAiChatOptions.builder().temperature(0.8))
                .build();
    }

    /**
     * 基础同步调用。
     */
    @GetMapping("/basic")
    public String basic(@RequestParam String message) {
        String actualMessage = DemoRequestValidation.requireText(message, "message");
        return ChatResponseSupport.requireContent(this.chatClient.prompt()
                .user(actualMessage)
                .call()
                .content());
    }

    /**
     * 带请求级系统消息。
     */
    @GetMapping("/with-system")
    public String withSystem(@RequestParam String role, @RequestParam String message) {
        String actualRole = DemoRequestValidation.requireText(role, "role");
        String actualMessage = DemoRequestValidation.requireText(message, "message");
        return ChatResponseSupport.requireContent(this.chatClient.prompt()
                .system("你是一个" + actualRole)
                .user(actualMessage)
                .call()
                .content());
    }

    /**
     * 获取完整响应，并安全提取结果、完成原因和 Usage。
     */
    @GetMapping("/full-response")
    public Map<String, Object> fullResponse(@RequestParam String message) {
        ChatResponse response = this.chatClient.prompt()
                .user(DemoRequestValidation.requireText(message, "message"))
                .call()
                .chatResponse();
        return ChatResponseSupport.responseSummary(response);
    }

    /**
     * 流式文本。每个元素是供应商返回并由 Spring AI 转换的数据块，不保证逐字。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String message) {
        return this.chatClient.prompt()
                .user(DemoRequestValidation.requireText(message, "message"))
                .stream()
                .content();
    }

    /**
     * 流式 ChatClientResponse，可同时观察响应块和 Advisor context。
     */
    @GetMapping(value = "/stream-response", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> streamResponse(@RequestParam String message) {
        return this.chatClient.prompt()
                .user(DemoRequestValidation.requireText(message, "message"))
                .stream()
                .chatClientResponse()
                .map(clientResponse -> {
                    Map<String, Object> chunk = new LinkedHashMap<>();
                    ChatResponse response = clientResponse.chatResponse();
                    String content = "";
                    if (response != null && response.getResult() != null
                            && response.getResult().getOutput().getText() != null) {
                        content = response.getResult().getOutput().getText();
                    }
                    chunk.put("content", content);
                    chunk.put("usage", response != null
                            ? ChatResponseSupport.usageSummary(response) : Map.of());
                    chunk.put("contextKeys", List.copyOf(clientResponse.context().keySet()));
                    return chunk;
                });
    }

    /**
     * 请求级 OpenAI Options。2.0 API 接收 Builder，而不是已经 build() 的 Options。
     */
    @GetMapping("/with-options")
    public String withOptions(@RequestParam String message,
            @RequestParam(defaultValue = "0.7") Double temperature,
            @RequestParam(defaultValue = "500") Integer maxTokens,
            @RequestParam(defaultValue = "false") boolean useMaxCompletionTokens) {

        String actualMessage = DemoRequestValidation.requireText(message, "message");
        int actualTokenLimit = DemoRequestValidation.requirePositiveTokenLimit(maxTokens);
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();

        if (useMaxCompletionTokens) {
            // 推理模型通常使用 max_completion_tokens；是否支持其他参数仍以模型文档为准。
            options.maxCompletionTokens(actualTokenLimit);
        }
        else {
            options.temperature(DemoRequestValidation.requireTemperature(temperature));
            options.maxTokens(actualTokenLimit);
        }

        return ChatResponseSupport.requireContent(this.chatClient.prompt()
                .user(actualMessage)
                .options(options)
                .call()
                .content());
    }

    @GetMapping("/technical")
    public String technical(@RequestParam String question) {
        return ChatResponseSupport.requireContent(this.technicalClient.prompt()
                .user(DemoRequestValidation.requireText(question, "question"))
                .call()
                .content());
    }

    @GetMapping("/creative")
    public String creative(@RequestParam String topic) {
        String actualTopic = DemoRequestValidation.requireText(topic, "topic");
        return ChatResponseSupport.requireContent(this.creativeClient.prompt()
                .user("写一段关于" + actualTopic + "的创意文字")
                .call()
                .content());
    }

    /**
     * 从已有 ChatClient 复制默认配置，再覆盖系统提示。
     */
    @GetMapping("/mutated")
    public String mutated(@RequestParam String message) {
        ChatClient conciseClient = this.chatClient.mutate()
                .defaultSystem("请用不超过三句话的中文回答")
                .build();
        return ChatResponseSupport.requireContent(conciseClient.prompt()
                .user(DemoRequestValidation.requireText(message, "message"))
                .call()
                .content());
    }

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.ofEntries(
                Map.entry("basic", "基础调用: /chatclient/basic?message=xxx"),
                Map.entry("with-system", "带系统消息: /chatclient/with-system?role=xxx&message=xxx"),
                Map.entry("full-response", "完整响应: /chatclient/full-response?message=xxx"),
                Map.entry("stream", "流式文本: /chatclient/stream?message=xxx"),
                Map.entry("stream-response", "流式完整响应: /chatclient/stream-response?message=xxx"),
                Map.entry("with-options", "请求级选项: /chatclient/with-options?message=xxx&maxTokens=500"),
                Map.entry("technical", "技术专家: /chatclient/technical?question=xxx"),
                Map.entry("creative", "创意作家: /chatclient/creative?topic=xxx"),
                Map.entry("mutated", "复制客户端配置: /chatclient/mutated?message=xxx"),
                Map.entry("entity", "结构化输出: /chatclient/entity"),
                Map.entry("entity-validated", "供应商结构化输出+校验: /chatclient/entity-validated"),
                Map.entry("response-entity", "响应+实体: /chatclient/response-entity"),
                Map.entry("with-metadata", "消息元数据: /chatclient/with-metadata?message=xxx")
        );
    }

    @GetMapping("/entity")
    public Map<String, Object> entity() {
        PersonInfo person = this.chatClient.prompt()
                .user("提取人物信息：李明，28 岁，软件工程师，工作在北京")
                .call()
                .entity(PersonInfo.class);
        return personToMap(requireEntity(person));
    }

    /**
     * 2.0 的 EntityParamSpec：使用供应商原生结构化输出，并在失败时校验重试。
     */
    @GetMapping("/entity-validated")
    public Map<String, Object> entityValidated() {
        PersonInfo person = this.chatClient.prompt()
                .user("提取人物信息：李明，28 岁，软件工程师，工作在北京")
                .call()
                .entity(PersonInfo.class,
                        spec -> spec.useProviderStructuredOutput().validateSchema());
        return personToMap(requireEntity(person));
    }

    @GetMapping("/response-entity")
    public Map<String, Object> responseEntity() {
        ResponseEntity<ChatResponse, Analysis> responseEntity = this.chatClient.prompt()
                .user("分析这段文本的情感：今天天气真好，心情很愉快！")
                .call()
                .responseEntity(Analysis.class);

        ChatResponse chatResponse = ChatResponseSupport.requireResponse(responseEntity.getResponse());
        Analysis analysis = requireEntity(responseEntity.getEntity());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sentiment", analysis.sentiment());
        result.put("confidence", analysis.confidence());
        result.put("keywords", analysis.keywords());
        result.put("model", chatResponse.getMetadata().getModel());
        result.put("usage", ChatResponseSupport.usageSummary(chatResponse));
        return result;
    }

    /**
     * 消息 metadata 属于请求消息；它不会自动复制到 ChatResponse metadata。
     */
    @GetMapping("/with-metadata")
    public Map<String, Object> withMetadata(@RequestParam String message) {
        Map<String, Object> systemMetadata = Map.of(
                "version", "2.0",
                "assistantName", "教程助手");
        Map<String, Object> userMetadata = Map.of(
                "messageId", UUID.randomUUID().toString(),
                "userId", "demo-user",
                "timestamp", System.currentTimeMillis(),
                "source", "web-api");

        SystemMessage systemMessage = SystemMessage.builder()
                .text("你是一个乐于助人的中文助手。")
                .metadata(systemMetadata)
                .build();
        UserMessage userMessage = UserMessage.builder()
                .text(DemoRequestValidation.requireText(message, "message"))
                .metadata(userMetadata)
                .build();

        ChatClientResponse clientResponse = this.chatClient.prompt()
                .messages(systemMessage, userMessage)
                .call()
                .chatClientResponse();
        ChatResponse chatResponse = ChatResponseSupport.requireResponse(clientResponse);

        Map<String, Object> requestMetadata = new LinkedHashMap<>();
        requestMetadata.put("system", systemMessage.getMetadata());
        requestMetadata.put("user", userMessage.getMetadata());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", ChatResponseSupport.requireContent(chatResponse));
        result.put("requestMessageMetadata", requestMetadata);
        result.put("responseMetadata", Map.of(
                "model", chatResponse.getMetadata().getModel(),
                "usage", ChatResponseSupport.usageSummary(chatResponse)));
        result.put("advisorContextKeys", List.copyOf(clientResponse.context().keySet()));
        return result;
    }

    private static <T> T requireEntity(T entity) {
        if (entity == null) {
            throw new EmptyChatResponseException("模型未返回可反序列化的结构化结果");
        }
        return entity;
    }

    private static Map<String, Object> personToMap(PersonInfo person) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", person.name());
        result.put("age", person.age());
        result.put("occupation", person.occupation());
        result.put("city", person.city());
        return result;
    }

    public record PersonInfo(String name, int age, String occupation, String city) {
    }

    public record Analysis(String sentiment, double confidence, List<String> keywords) {
    }

}
