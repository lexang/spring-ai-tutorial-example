package com.example.mcp.demo;

import java.util.List;
import java.util.Map;

import com.example.mcp.demo.controller.McpClientController;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class McpClientPolicyTests {
    @SuppressWarnings("unchecked") private final ObjectProvider<List<McpSyncClient>> provider = mock(ObjectProvider.class);
    private final McpClientController controller = new McpClientController(provider);

    @Test void blockedToolDoesNotEvenResolveClients() {
        assertThat(controller.callTool("delete-file", Map.of("fileName", "x")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(provider);
    }

    @Test void allowedCallPreservesProtocolErrorResult() {
        var client = clientWithTool();
        when(provider.getIfAvailable(any())).thenReturn(List.of(client));
        var result = mock(McpSchema.CallToolResult.class);
        when(result.isError()).thenReturn(true);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(result);
        var response = controller.callTool("basic-calculation", Map.of("a", 1, "b", 0, "operation", "/"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(result);
    }

    @Test void ambiguousNamesCannotExecuteTheFirstMatchingServer() {
        var first = clientWithTool();
        var second = clientWithTool();
        when(provider.getIfAvailable(any())).thenReturn(List.of(first, second));
        assertThat(controller.callTool("basic-calculation", Map.of()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(first, never()).callTool(any());
        verify(second, never()).callTool(any());
    }

    @Test void sdkFailureDoesNotLeakDownstreamText() {
        var client = clientWithTool();
        when(provider.getIfAvailable(any())).thenReturn(List.of(client));
        when(client.callTool(any())).thenThrow(new IllegalStateException("机密下游正文"));
        var response = controller.callTool("basic-calculation", Map.of());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().toString()).doesNotContain("机密下游正文");
    }

    private static McpSyncClient clientWithTool() {
        var client = mock(McpSyncClient.class);
        var listed = mock(McpSchema.ListToolsResult.class);
        var tool = mock(McpSchema.Tool.class);
        when(tool.name()).thenReturn("basic-calculation");
        when(listed.tools()).thenReturn(List.of(tool));
        when(client.listTools()).thenReturn(listed);
        return client;
    }
}
