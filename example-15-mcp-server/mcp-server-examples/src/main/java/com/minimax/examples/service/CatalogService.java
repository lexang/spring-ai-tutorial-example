package com.minimax.examples.service;

import java.util.List;
import java.util.Locale;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * 展示 2.0 注解扫描的资源、提示和自动完成能力。
 */
@Component
public class CatalogService {

    @McpResource(uri = "system://info", name = "system-info",
            description = "返回运行时系统信息", mimeType = "text/plain")
    public String systemInfo() {
        return "Java=" + System.getProperty("java.version")
            + "; OS=" + System.getProperty("os.name");
    }

    @McpResource(uri = "guide://{topic}", name = "guide",
            description = "返回指定主题的简短指南", mimeType = "text/plain")
    public String guide(String topic) {
        return "指南主题: " + topic;
    }

    @McpPrompt(name = "code-review", description = "生成代码审查提示")
    public McpSchema.GetPromptResult codeReview(
            @McpArg(name = "language", description = "编程语言", required = true) String language) {
        String message = "请用 " + language + " 检查代码的正确性、安全性和可维护性。";
        return McpSchema.GetPromptResult.builder(List.of(
                McpSchema.PromptMessage.builder(McpSchema.Role.ASSISTANT,
                        McpSchema.TextContent.builder(message).build()).build()))
            .description("代码审查提示")
            .build();
    }

    @McpComplete(prompt = "code-review")
    public List<String> completeLanguage(String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return List.of("Java", "Kotlin", "Python", "TypeScript").stream()
            .filter(language -> language.toLowerCase(Locale.ROOT).startsWith(normalized))
            .toList();
    }

}
