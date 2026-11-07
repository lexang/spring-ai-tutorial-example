package com.example.mcp.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.ai.mcp.client.enabled=false")
class McpClientApplicationTest {

    @Test
    void startsOfflineWithoutExternalMcpServer() {
        // SpringBootTest 能够创建完整应用上下文即表示默认配置没有触发外部连接。
    }

}
