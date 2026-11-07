package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 多模型控制器
 * <p>
 * 演示如何使用不同的模型处理不同类型的任务
 *
 * @author Spring AI Tutorial
 */
@RestController
@RequestMapping("/api")
public class MultiModelController {

    private static final Logger log = LoggerFactory.getLogger(MultiModelController.class);

    private final ChatClient fastClient;
    private final ChatClient standardClient;
    private final ChatClient creativeClient;
    private final ChatClient analyticalClient;

    // 使用 Map 存储所有模型，方便动态选择
    private final Map<String, ChatClient> modelMap;
    private final String openAiModelName;
    private final String miniMaxModelName;

    public MultiModelController(
            @Qualifier("fast") ChatClient fastClient,
            @Qualifier("standard") ChatClient standardClient,
            @Qualifier("creative") ChatClient creativeClient,
            @Qualifier("analytical") ChatClient analyticalClient,
            @Qualifier("minimax") ChatClient miniMaxClient,
            @Value("${app.models.openai.model}") String openAiModelName,
            @Value("${app.models.minimax.model}") String miniMaxModelName) {
        this.openAiModelName = openAiModelName;
        this.miniMaxModelName = miniMaxModelName;

        this.fastClient = fastClient;
        this.standardClient = standardClient;
        this.creativeClient = creativeClient;
        this.analyticalClient = analyticalClient;

        // 初始化模型映射
        this.modelMap = new LinkedHashMap<>();
        modelMap.put("fast", fastClient);
        modelMap.put("standard", standardClient);
        modelMap.put("creative", creativeClient);
        modelMap.put("analytical", analyticalClient);
        modelMap.put("minimax", miniMaxClient);
    }

    /**
     * 使用快速模型 - 简单问答
     * <p>
     * GET /api/fast?question=什么是Java
     */
    @GetMapping("/fast")
    public ResponseEntity<String> fastQuery(@RequestParam String question) {
        if (isBlank(question)) {
            return badRequest("question 不能为空");
        }
        log.info("使用快速模型处理问题，字符数: {}", question.length());

        String response = call(fastClient, question);

        return ResponseEntity.ok(response);
    }

    /**
     * 使用标准模型 - 一般性任务
     * <p>
     * GET /api/standard?question=如何学习Spring Boot
     */
    @GetMapping("/standard")
    public ResponseEntity<String> standardQuery(@RequestParam String question) {
        if (isBlank(question)) {
            return badRequest("question 不能为空");
        }
        log.info("使用标准模型处理问题，字符数: {}", question.length());

        String response = call(standardClient, question);

        return ResponseEntity.ok(response);
    }

    /**
     * 使用创意模型 - 创意写作
     * <p>
     * GET /api/creative?prompt=写一首关于春天的诗
     */
    @GetMapping("/creative")
    public ResponseEntity<String> creativeQuery(@RequestParam String prompt) {
        if (isBlank(prompt)) {
            return badRequest("prompt 不能为空");
        }
        log.info("使用创意模型处理提示，字符数: {}", prompt.length());

        String response = call(creativeClient, prompt);

        return ResponseEntity.ok(response);
    }

    /**
     * 使用分析模型 - 逻辑分析
     * <p>
     * GET /api/analytical?question=分析微服务架构的优缺点
     */
    @GetMapping("/analytical")
    public ResponseEntity<String> analyticalQuery(@RequestParam String question) {
        if (isBlank(question)) {
            return badRequest("question 不能为空");
        }
        log.info("使用分析模型处理问题，字符数: {}", question.length());

        String response = call(analyticalClient, question);

        return ResponseEntity.ok(response);
    }

    /**
     * 动态模型选择
     * <p>
     * POST /api/chat
     * <p>
     * Body: {
     * "message": "你的问题",
     * "model": "fast"  // 可选: fast, standard, creative, analytical
     * }
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request == null || isBlank(request.message())) {
            return ResponseEntity.badRequest().body(new ApiError("message 不能为空"));
        }
        String modelName = request.model() != null ? request.model() : "standard";

        log.info("使用模型 [{}] 处理消息，字符数: {}", modelName, request.message().length());

        ChatClient client = modelMap.get(modelName);
        if (client == null) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("不支持的模型: " + modelName + "。支持的模型: " + modelMap.keySet()));
        }

        String response = call(client, request.message());

        return ResponseEntity.ok(new ChatResponse(response, modelName));
    }

    /**
     * 对比不同模型的响应
     * <p>
     * GET /api/compare?question=什么是人工智能
     */
    @GetMapping("/compare")
    public ResponseEntity<?> compareModels(@RequestParam String question) {
        if (isBlank(question)) {
            return ResponseEntity.badRequest().body(new ApiError("question 不能为空"));
        }
        log.info("对比所有模型的响应，问题字符数: {}", question.length());

        Map<String, String> responses = new LinkedHashMap<>();

        // 依次调用所有模型
        for (Map.Entry<String, ChatClient> entry : modelMap.entrySet()) {
            String modelName = entry.getKey();
            ChatClient client = entry.getValue();

            try {
                String response = call(client, question);

                responses.put(modelName, response);
            } catch (Exception e) {
                log.error("模型 {} 调用失败，异常类型={}", modelName, e.getClass().getSimpleName());
                responses.put(modelName, "模型调用失败，请查看服务端日志");
            }
        }

        return ResponseEntity.ok(responses);
    }

    /**
     * 获取可用的模型列表
     * <p>
     * GET /api/models
     */
    @GetMapping("/models")
    public ResponseEntity<ModelsInfo> getModels() {
        List<ModelInfo> models = Arrays.asList(
                new ModelInfo("fast", "简洁客户端", "较低输出上限", this.openAiModelName, 0.3),
                new ModelInfo("standard", "通用客户端", "默认任务配置", this.openAiModelName, 0.7),
                new ModelInfo("creative", "写作客户端", "写作提示与选项", this.openAiModelName, 1.2),
                new ModelInfo("analytical", "分析客户端", "分析任务提示", this.openAiModelName, 0.2),
                new ModelInfo("minimax", "MiniMax 客户端", "独立 Anthropic 兼容连接", this.miniMaxModelName, null)
        );

        return ResponseEntity.ok(new ModelsInfo(models));
    }

    /**
     * 健康检查和欢迎信息
     */
    @GetMapping("/")
    public String home() {
        return """
                Spring AI 多模型示例正在运行!

                可用接口:
                - GET  /api/fast?question=xxx           - 快速模型（简单问答）
                - GET  /api/standard?question=xxx       - 标准模型（一般任务）
                - GET  /api/creative?prompt=xxx         - 创意模型（创意写作）
                - GET  /api/analytical?question=xxx     - 分析模型（逻辑分析）
                - POST /api/chat                        - 动态选择模型
                - GET  /api/compare?question=xxx        - 对比所有模型
                - GET  /api/models                      - 查看可用模型列表
                - POST /api/chat (model=minimax)        - 使用 MiniMax 模型

                示例:
                curl "http://localhost:8080/api/fast?question=什么是Java"
                curl "http://localhost:8080/api/creative?prompt=写一首诗"
                curl "http://localhost:8080/api/compare?question=什么是AI"
                """;
    }

    private String call(ChatClient client, String prompt) {
        String response;
        try {
            response = client.prompt().user(prompt).call().content();
        } catch (RuntimeException exception) {
            throw new ModelCallFailureException("模型调用失败", exception);
        }
        if (response == null || response.isBlank()) {
            throw new ModelCallFailureException("模型未返回文本内容", null);
        }
        return response;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseEntity<String> badRequest(String message) {
        return ResponseEntity.badRequest().body(message);
    }
}

// 请求和响应 record
record ChatRequest(String message, String model) {}
record ChatResponse(String response, String usedModel) {}
record ModelInfo(String id, String name, String description, String underlyingModel, Double temperature) {}
record ModelsInfo(List<ModelInfo> models) {}
record ApiError(String message) {}
