package com.example.mcp.demo.config;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;

import static org.assertj.core.api.Assertions.assertThat;

class CustomToolContextToMcpMetaConverterTest {

    @Test
    void arbitraryObjectsAndNestedSecretsAreNotForwarded() {
        var metadata = new CustomToolContextToMcpMetaConverter().convert(new ToolContext(Map.of(
                "traceId", "trace-1", "tenantId", Map.of("password", "secret"),
                "unknown", "not-forwarded", "userId", Double.NaN)));
        assertThat(metadata).containsOnlyKeys("app_traceId");
    }

    @Test
    void convertsBusinessContextAndDropsSecrets() {
        Map<String, Object> context = new HashMap<>();
        context.put("userId", "user-1");
        context.put("authToken", "do-not-forward");
        context.put("nullable", null);
        context.put(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, "internal");

        Map<String, Object> metadata = new CustomToolContextToMcpMetaConverter()
            .convert(new ToolContext(context));

        assertThat(metadata).containsEntry("app_userId", "user-1");
        assertThat(metadata).doesNotContainKey("app_authToken");
        assertThat(metadata).doesNotContainKey("app_nullable");
        assertThat(metadata).doesNotContainKey("app_" + McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY);
    }

}
