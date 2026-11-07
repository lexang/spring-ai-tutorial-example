package com.example.mcp.demo.handler;

import java.util.List;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpLogging;
import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.ai.mcp.annotation.McpToolListChanged;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP 客户端通知处理器。
 *
 * <p>clients 必须与配置中的连接名称一致；这里使用 remote profile 的
 * local-server。</p>
 */
@Component
public class McpClientHandlers {
    private static final Logger log = LoggerFactory.getLogger(McpClientHandlers.class);

    @McpLogging(clients = "local-server")
    public void handleLogging(McpSchema.LoggingMessageNotification notification) {
        log.info("收到 MCP 服务器日志事件，级别={}", notification.level());
    }

    @McpProgress(clients = "local-server")
    public void handleProgress(McpSchema.ProgressNotification notification) {
        log.info("MCP 工具进度={}", notification.progress());
    }

    @McpToolListChanged(clients = "local-server")
    public void handleToolListChanged(List<McpSchema.Tool> tools) {
        log.info("MCP 工具列表已更新，数量={}", tools.size());
    }

}
