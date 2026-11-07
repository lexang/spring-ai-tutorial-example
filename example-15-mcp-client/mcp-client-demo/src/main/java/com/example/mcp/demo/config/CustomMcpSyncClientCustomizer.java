package com.example.mcp.demo.config;

import java.time.Duration;

import io.modelcontextprotocol.client.McpClient;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.stereotype.Component;

/**
 * 2.0 的客户端定制器接口位于 org.springframework.ai.mcp.customizer。
 */
@Component
public class CustomMcpSyncClientCustomizer implements McpClientCustomizer<McpClient.SyncSpec> {

    @Override
    public void customize(String serverConfigurationName, McpClient.SyncSpec spec) {
        spec.requestTimeout(Duration.ofSeconds(30));
    }

}
