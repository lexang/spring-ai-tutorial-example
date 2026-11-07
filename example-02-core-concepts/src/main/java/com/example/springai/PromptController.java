package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prompt 示例控制器
 * 演示不同类型的消息和 Prompt 构建
 *
 * @author Spring AI Tutorial
 */
@RestController
@RequestMapping("/prompt")
public class PromptController {

    private static final Logger log = LoggerFactory.getLogger(PromptController.class);

    private final ChatClient chatClient;

    public PromptController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 示例 1: 只有用户消息（最简单）
     * <p>
     * 示例: http://localhost:8080/prompt/user-only?message=你好
     */
    @GetMapping("/user-only")
    public Map<String, String> userMessageOnly(@RequestParam String message) {
        requireText(message, "message");
        log.info("仅用户消息 Prompt，消息长度：{}", message.length());

        String response = complete(chatClient.prompt().user(message));

        return Map.of(
                "type", "User Message Only",
                "user", message,
                "response", response
        );
    }

    /**
     * 示例 2: 系统消息 + 用户消息
     * 系统消息定义 AI 的角色和行为
     * <p>
     * 示例: http://localhost:8080/prompt/with-system?role=Java专家&message=什么是Spring
     */
    @GetMapping("/with-system")
    public Map<String, Object> withSystemMessage(
            @RequestParam String role,
            @RequestParam String message) {

        requireText(role, "role");
        requireText(message, "message");
        log.info("系统角色长度：{}，用户消息长度：{}", role.length(), message.length());

        String systemPrompt = "你是一个" + role + "，请用专业但易懂的语言回答问题。";

        String response = complete(chatClient.prompt()
                .system(systemPrompt)
                .user(message));

        return Map.of(
                "type", "With System Message",
                "system", systemPrompt,
                "user", message,
                "response", response
        );
    }

    /**
     * 示例 3: 多轮对话（包含历史消息）
     * 模拟对话上下文
     * <p>
     * 示例: http://localhost:8080/prompt/conversation?question=那么如何开始学习
     */
    @GetMapping("/conversation")
    public Map<String, Object> conversationExample(@RequestParam String question) {
        requireText(question, "question");
        log.info("多轮对话当前问题长度：{}", question.length());

        // 模拟对话历史
        List<Message> messages = new ArrayList<>();

        // 系统消息
        messages.add(new SystemMessage("你是一个编程学习助手"));

        // 第一轮对话
        messages.add(new UserMessage("我想学习 Java"));
        messages.add(new AssistantMessage("太好了！Java 是一门优秀的编程语言，" +
                "广泛应用于企业开发、Android 开发等领域。"));

        // 第二轮对话
        messages.add(new UserMessage("它难学吗？"));
        messages.add(new AssistantMessage("Java 有一定的学习曲线，但循序渐进地学习" +
                "并不困难。建议从基础语法开始，多写代码练习。"));

        // 当前问题
        messages.add(new UserMessage(question));

        // 创建 Prompt
        Prompt prompt = new Prompt(messages);
        String response = complete(chatClient.prompt(prompt));

        return Map.of(
                "type", "Multi-turn Conversation",
                "history", List.of(
                        "User: 我想学习 Java",
                        "Assistant: 太好了！Java 是...",
                        "User: 它难学吗？",
                        "Assistant: Java 有一定的学习曲线..."
                ),
                "currentQuestion", question,
                "response", response
        );
    }

    /**
     * 示例 4: 使用不同的系统角色
     * 演示系统消息如何影响输出风格
     * <p>
     * 示例: http://localhost:8080/prompt/different-roles?style=poet&topic=春天
     */
    @GetMapping("/different-roles")
    public Map<String, Object> differentRoles(
            @RequestParam String style,
            @RequestParam String topic) {
        requireText(style, "style");
        requireText(topic, "topic");

        String systemPrompt = switch (style) {
            case "poet" -> "你是一位浪漫的诗人，用诗意的语言描述事物。";
            case "scientist" -> "你是一位严谨的科学家，用科学的角度分析问题。";
            case "teacher" -> "你是一位耐心的老师，用简单易懂的方式解释概念。";
            case "comedian" -> "你是一位幽默的喜剧演员，用轻松风趣的方式表达。";
            default -> "你是一个友好的助手。";
        };

        String response = complete(chatClient.prompt()
                .system(systemPrompt)
                .user("请描述：" + topic));

        return Map.of(
                "style", style,
                "systemPrompt", systemPrompt,
                "topic", topic,
                "response", response
        );
    }

    /**
     * 示例 5: Prompt Engineering 技巧演示
     * 使用"分步思考"技巧
     * <p>
     * 示例: http://localhost:8080/prompt/step-by-step?problem=设计一个图书管理系统
     */
    @GetMapping("/step-by-step")
    public Map<String, Object> stepByStepThinking(@RequestParam String problem) {
        requireText(problem, "problem");
        log.info("分步提示问题长度：{}", problem.length());

        String enhancedPrompt = String.format("""
                深呼吸，让我们一步一步思考这个问题：

                问题：%s

                请按以下步骤分析：
                1. 首先，分析问题的核心需求
                2. 然后，列出主要功能模块
                3. 接着，设计系统架构
                4. 最后，给出具体的实现建议

                每一步都要详细说明。
                """, problem);

        String response = complete(chatClient.prompt().user(enhancedPrompt));

        return Map.of(
                "type", "Step-by-Step Thinking",
                "problem", problem,
                "technique", "深呼吸 + 分步引导",
                "response", response
        );
    }

    /**
     * 示例 6: Few-shot Learning（提供示例）
     * 通过示例教会 AI 输出格式
     * <p>
     * 示例: http://localhost:8080/prompt/few-shot?input=王五,28岁,产品经理
     */
    @GetMapping("/few-shot")
    public Map<String, Object> fewShotLearning(@RequestParam String input) {
        requireText(input, "input");
        log.info("Few-shot 输入长度：{}", input.length());

        String promptWithExamples = String.format("""
                将以下信息转换为 JSON 格式：

                示例 1:
                输入: 张三,25岁,工程师
                输出: {"name": "张三", "age": 25, "job": "工程师"}

                示例 2:
                输入: 李四,30岁,设计师
                输出: {"name": "李四", "age": 30, "job": "设计师"}

                现在请转换:
                输入: %s
                输出:
                """, input);

        String response = complete(chatClient.prompt().user(promptWithExamples));

        return Map.of(
                "type", "Few-shot Learning",
                "input", input,
                "technique", "提供示例引导输出格式",
                "response", response
        );
    }

    /**
     * 示例 7: 指定输出格式
     * 要求 AI 按特定格式输出
     * <p>
     * 示例: http://localhost:8080/prompt/formatted?code=for(int i=0; i<n; i++)
     */
    @GetMapping("/formatted")
    public Map<String, Object> formattedOutput(@RequestParam String code) {
        requireText(code, "code");
        log.info("正在分析代码，代码长度：{}", code.length());

        String promptWithFormat = String.format("""
                分析以下代码的复杂度：

                ```java
                %s
                ```

                请严格按以下格式输出：
                1. 功能描述：[一句话说明代码功能]
                2. 时间复杂度：[O(?) 并解释]
                3. 空间复杂度：[O(?) 并解释]
                4. 优化建议：[如果有，提供具体建议]
                """, code);

        String response = complete(chatClient.prompt().user(promptWithFormat));

        return Map.of(
                "type", "Formatted Output",
                "code", code,
                "technique", "指定明确的输出格式",
                "response", response
        );
    }

    /** 使用官方 PromptTemplate 完成模板渲染，而非只声明未使用的变量 Map。 */
    @GetMapping("/template")
    public Map<String, String> template(@RequestParam String question) {
        requireText(question, "question");
        Prompt prompt = new PromptTemplate("你是一个{role}。用户问题：{question}。请用{language}回答。")
                .create(Map.of("role", "Java 专家", "question", question, "language", "中文"));
        return Map.of("question", question, "response", complete(this.chatClient.prompt(prompt)));
    }

    private String complete(ChatClient.ChatClientRequestSpec request) {
        String response;
        try {
            response = request.call().content();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("模型调用失败", exception);
        }
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("模型未返回可用的文本");
        }
        return response;
    }

    private void requireText(String value, String parameter) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(parameter + " 不能为空");
        }
    }

    /**
     * 主页 - API 列表
     */
    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "title", "Prompt 示例 API",
                "endpoints", List.of(
                        "/prompt/user-only - 只有用户消息",
                        "/prompt/with-system - 带系统消息",
                        "/prompt/conversation - 多轮对话",
                        "/prompt/different-roles - 不同角色",
                        "/prompt/step-by-step - 分步思考",
                        "/prompt/few-shot - Few-shot 学习",
                        "/prompt/formatted - 格式化输出",
                        "/prompt/template - PromptTemplate 模板渲染"
                )
        );
    }
}
