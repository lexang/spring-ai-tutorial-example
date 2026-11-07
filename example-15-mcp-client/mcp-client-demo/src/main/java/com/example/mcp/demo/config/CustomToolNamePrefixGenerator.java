package com.example.mcp.demo.config;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.stereotype.Component;

/**
 * 为来自不同服务器的同名工具增加稳定前缀。
 */
@Component
public class CustomToolNamePrefixGenerator implements McpToolNamePrefixGenerator {

    @Override
    public String prefixedToolName(McpConnectionInfo connectionInfo, McpSchema.Tool tool) {
        String clientName = connectionInfo.clientInfo().name()
            .replaceAll("[^A-Za-z0-9]+", "_")
            .toLowerCase();
        String toolName = tool.name().replaceAll("[^A-Za-z0-9]+", "_");
        String result = clientName + "_" + toolName;
        return result.substring(0, Math.min(64, result.length()));
    }

}
