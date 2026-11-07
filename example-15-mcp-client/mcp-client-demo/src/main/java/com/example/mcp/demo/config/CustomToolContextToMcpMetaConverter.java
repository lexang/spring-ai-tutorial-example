package com.example.mcp.demo.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.stereotype.Component;

/**
 * 将业务上下文转为 MCP _meta，并明确过滤凭据。
 */
@Component
public class CustomToolContextToMcpMetaConverter implements ToolContextToMcpMetaConverter {

    private static final Set<String> ALLOWED_KEYS = Set.of("userId", "tenantId", "traceId");

    @Override
    public Map<String, Object> convert(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return Map.of();
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        toolContext.getContext().forEach((key, value) -> {
            if (value != null
                    && !McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY.equals(key)
                    && ALLOWED_KEYS.contains(key) && isSafeScalar(value)) {
                metadata.put("app_" + key, value);
            }
        });
        return metadata;
    }

    private boolean isSafeScalar(Object value) {
        if (value instanceof String text) { return text.length() <= 256; }
        if (value instanceof Number number) { return Double.isFinite(number.doubleValue()); }
        return value instanceof Boolean;
    }

}
