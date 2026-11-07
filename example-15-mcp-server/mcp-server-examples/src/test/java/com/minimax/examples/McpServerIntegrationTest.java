package com.minimax.examples;

import java.time.Duration;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动真实 WebMVC 应用，并通过 SDK Streamable-HTTP 客户端完成 MCP 闭环。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerIntegrationTest {

    @Autowired
    private Environment environment;

    private McpSyncClient client;

    @BeforeEach
    void connect() {
        Integer port = this.environment.getProperty("local.server.port", Integer.class);
        this.client = McpClient.sync(HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:" + port)
                .endpoint("/mcp")
                .build())
            .initializationTimeout(Duration.ofSeconds(15))
            .requestTimeout(Duration.ofSeconds(15))
            .build();
        this.client.initialize();
    }

    @AfterEach
    void close() {
        if (this.client != null) {
            this.client.close();
        }
    }

    @Test
    void listsAnnotatedToolsResourcesAndPrompts() {
        assertThat(this.client.listTools().tools())
            .extracting(McpSchema.Tool::name)
            .contains("basic-calculation", "percentage-calculation")
            .doesNotContain("read-file", "write-file", "delete-file");
        assertThat(this.client.listResources().resources())
            .extracting(McpSchema.Resource::name)
            .contains("system-info");
        assertThat(this.client.listPrompts().prompts())
            .extracting(McpSchema.Prompt::name)
            .contains("code-review");
    }

    @Test
    void callsCalculatorToolOverStreamableHttp() {
        McpSchema.CallToolResult result = this.client.callTool(
            McpSchema.CallToolRequest.builder("basic-calculation")
                .arguments(Map.of("a", 12, "b", 3, "operation", "+"))
                .build());

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isNotEmpty();
    }

    @Test
    void invalidArgumentsProduceAnExplicitToolError() {
        var result = this.client.callTool(McpSchema.CallToolRequest.builder("basic-calculation")
                .arguments(Map.of("a", "不是数字", "b", 3, "operation", "+")).build());
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).isNotEmpty();
    }

}
