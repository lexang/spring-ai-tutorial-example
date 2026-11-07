package com.example.mcp.demo.config;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.stereotype.Component;

/**
 * 单一工具过滤器示例。Starter 只会选择一个 McpToolFilter bean。
 */
@Component
public class CustomMcpToolFilter implements McpToolFilter {

    @Override
    public boolean test(McpConnectionInfo connectionInfo, McpSchema.Tool tool) {
        String clientName = connectionInfo.clientInfo().name();
        String description = tool.description() == null ? "" : tool.description().toLowerCase();
        if (clientName.contains("restricted")) {
            return false;
        }
        return McpToolPolicy.allows(tool.name())
                && !description.contains("experimental") && !description.contains("deprecated");
    }

}
