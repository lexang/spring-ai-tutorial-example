package com.example.springai.model;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** 仅用于离线结构化输出测试的确定性模型。 */
public final class DeterministicCodeChatModel implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
        String text = prompt.getUserMessage().getText();
        String json;
        String language = List.of("java", "kotlin", "python", "javascript", "typescript").stream()
            .filter(value -> text.contains(" " + value + " 代码") || text.contains(" " + value + " 实现"))
            .findFirst().orElse("java");
        if (text.startsWith("[TEST_PLAN]")) {
            String framework = switch (language) {
                case "java", "kotlin" -> "JUnit 5";
                case "python" -> "pytest";
                default -> "Vitest";
            };
            json = """
                {"framework":"%s","testCases":["正常输入返回结果","空输入被拒绝","异常被转换为业务错误"],"notes":["使用边界值和异常路径","测试不得调用真实外部服务"]}
                """.formatted(framework);
        }
        else if (text.startsWith("[IMPLEMENT]")) {
            String code = switch (language) {
                case "java" -> "public final class Normalizer { public static String normalize(String value) { return value == null ? \\\"\\\" : value.trim(); } }";
                case "kotlin" -> "fun normalize(value: String?): String = value?.trim().orEmpty()";
                case "python" -> "def normalize(value): return value.strip() if value is not None else ''";
                case "typescript" -> "export function normalize(value: string | null): string { return value == null ? '' : value.trim(); }";
                default -> "export function normalize(value) { return value == null ? '' : value.trim(); }";
            };
            json = """
                {"language":"%s","code":"%s","explanation":"先处理 null，再去除首尾空白。","warnings":["生产代码还应定义空值业务语义"]}
                """.formatted(language, code);
        }
        else {
            json = """
                {"summary":"方法缺少输入校验，可能在空值路径失败。","issues":[{"severity":"HIGH","line":1,"message":"没有验证参数","suggestion":"在进入业务逻辑前拒绝 null 或空字符串"}],"score":72,"suggestions":["补充边界测试","不要在日志中输出敏感输入"]}
                """;
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

}
