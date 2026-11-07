package com.example.mcp.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI 2.0 MCP 客户端示例。
 *
 * <p>默认配置不连接任何外部 MCP 服务器；使用 remote 或 stdio profile
 * 才会创建客户端。</p>
 */
@SpringBootApplication
public class McpClientDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpClientDemoApplication.class, args);
    }

}
