package com.example.springai.service;

import java.util.List;
import java.util.Locale;

import com.example.springai.model.CodeModels.CodeReview;
import com.example.springai.model.CodeModels.GeneratedImplementation;
import com.example.springai.model.CodeModels.TestPlan;
import com.example.springai.support.CodeEngineeringException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;

@Service
public class CodeEngineeringService {

    private static final int MAX_CODE_LENGTH = 30_000;

    private final ChatClient chatClient;

    public CodeEngineeringService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
            .defaultSystem("你是谨慎的代码工程助手。只分析用户提供的代码，不执行代码，不猜测缺失依赖；输出必须符合请求的结构化类型。")
            .build();
    }

    public CodeReview review(CodeRequest request) {
        ValidatedCode code = validate(request);
        try {
            CodeReview result = this.chatClient.prompt()
                .user("[REVIEW]\n审查以下 " + code.language() + " 代码，指出安全、正确性和可测试性问题：\n" + code.code())
                .options(ChatOptions.builder().maxTokens(2000))
                .call()
                .entity(CodeReview.class, spec -> spec.validateSchema());
            if (result == null || !validText(result.summary()) || result.issues() == null
                    || result.issues().size() > 50 || result.score() < 0 || result.score() > 100
                    || !validTexts(result.suggestions(), 50)) {
                throw new CodeEngineeringException("代码审查模型没有返回完整结构化结果");
            }
            int lines = code.code().split("\\R", -1).length;
            if (result.issues().stream().anyMatch(issue -> issue == null
                    || !List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(issue.severity())
                    || issue.line() < 1 || issue.line() > lines
                    || !validText(issue.message()) || !validText(issue.suggestion()))) {
                throw new CodeEngineeringException("代码审查包含无效严重级别、行号或说明");
            }
            return result;
        }
        catch (CodeEngineeringException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new CodeEngineeringException("代码审查或结构化转换失败", exception);
        }
    }

    public TestPlan generateTests(CodeRequest request) {
        ValidatedCode code = validate(request);
        try {
            TestPlan result = this.chatClient.prompt()
                .user("[TEST_PLAN]\n为以下 " + code.language() + " 代码生成测试计划。使用 "
                        + frameworkFor(code.language()) + "，覆盖正常、边界和异常路径。\n" + code.code())
                .options(ChatOptions.builder().maxTokens(2000))
                .call()
                .entity(TestPlan.class, spec -> spec.validateSchema());
            if (result == null || !frameworkFor(code.language()).equals(result.framework())
                    || !validTexts(result.testCases(), 50) || result.testCases().isEmpty()
                    || !validTexts(result.notes(), 50)) {
                throw new CodeEngineeringException("测试生成模型没有返回测试用例");
            }
            return result;
        }
        catch (CodeEngineeringException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new CodeEngineeringException("测试计划生成或结构化转换失败", exception);
        }
    }

    public GeneratedImplementation generateImplementation(CodeRequest request) {
        ValidatedCode code = validate(request);
        try {
            GeneratedImplementation result = this.chatClient.prompt()
                .user("[IMPLEMENT]\n根据以下需求生成最小、可审查的 " + code.language() + " 实现：\n" + code.code())
                .options(ChatOptions.builder().maxTokens(2000))
                .call()
                .entity(GeneratedImplementation.class, spec -> spec.validateSchema());
            if (result == null || !validText(result.code()) || result.language() == null
                    || !code.language().equals(result.language().toLowerCase(Locale.ROOT))
                    || !validText(result.explanation()) || !validTexts(result.warnings(), 50)) {
                throw new CodeEngineeringException("代码生成模型没有返回实现");
            }
            return result;
        }
        catch (CodeEngineeringException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new CodeEngineeringException("代码生成或结构化转换失败", exception);
        }
    }

    private ValidatedCode validate(CodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String language = required(request.language(), "language", 40).toLowerCase(Locale.ROOT);
        if (!List.of("java", "kotlin", "python", "javascript", "typescript").contains(language)) {
            throw new IllegalArgumentException("language 仅支持 Java、Kotlin、Python、JavaScript 或 TypeScript");
        }
        String code = required(request.code(), "code", MAX_CODE_LENGTH);
        if (code.matches("(?s).*\\b(sk-[A-Za-z0-9_-]{12,}|AIza[A-Za-z0-9_-]{20,})\\b.*")
                || code.matches("(?is).*\\bpassword\\s*=.*")) {
            throw new IllegalArgumentException("code 疑似包含凭据，请先移除密钥或密码");
        }
        return new ValidatedCode(language, code);
    }

    private String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String frameworkFor(String language) {
        return switch (language) {
            case "java", "kotlin" -> "JUnit 5";
            case "python" -> "pytest";
            default -> "Vitest";
        };
    }

    private boolean validText(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_CODE_LENGTH;
    }

    private boolean validTexts(List<String> values, int limit) {
        return values != null && values.size() <= limit && values.stream().allMatch(this::validText);
    }

    public record CodeRequest(String language, String code) {
    }

    private record ValidatedCode(String language, String code) {
    }

}
