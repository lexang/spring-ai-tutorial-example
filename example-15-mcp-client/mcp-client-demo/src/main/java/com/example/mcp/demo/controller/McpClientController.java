package com.example.mcp.demo.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.mcp.demo.config.McpToolPolicy;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用 REST 端点观察和调用已经由 Spring AI 自动配置的 MCP 客户端。
 */
@RestController
@RequestMapping("/api/mcp")
public class McpClientController {

    private final ObjectProvider<List<McpSyncClient>> clientsProvider;

    public McpClientController(ObjectProvider<List<McpSyncClient>> clientsProvider) {
        this.clientsProvider = clientsProvider;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        List<McpSyncClient> clients = clients();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", !clients.isEmpty());
        result.put("clientCount", clients.size());
        result.put("message", clients.isEmpty() ? "未配置 MCP 连接，请使用 remote 或 stdio profile" : "MCP 客户端实例已创建，工具查询会验证连接");
        result.put("java", System.getProperty("java.version"));
        return result;
    }

    @GetMapping("/tools")
    public ResponseEntity<?> tools() {
        List<McpSyncClient> clients = clients();
        if (clients.isEmpty()) {
            return unavailable();
        }

        try {
            List<Map<String, Object>> tools = clients.stream()
                .flatMap(client -> client.listTools().tools().stream())
                .filter(tool -> McpToolPolicy.allows(tool.name()))
                .map(tool -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", tool.name());
                    item.put("description", tool.description());
                    item.put("inputSchema", tool.inputSchema());
                    return item;
                })
                .toList();
            return ResponseEntity.ok(tools);
        }
        catch (RuntimeException exception) { return downstreamFailure(); }
    }

    @PostMapping("/tools/{toolName}")
    public ResponseEntity<?> callTool(@PathVariable String toolName,
            @RequestBody(required = false) Map<String, Object> arguments) {
        if (!McpToolPolicy.allows(toolName)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "本教学接口不允许执行该工具"));
        }
        Map<String, Object> safeArguments;
        try {
            if (arguments != null && arguments.size() > 16) { throw new IllegalArgumentException("参数过多"); }
            safeArguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        }
        catch (RuntimeException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "参数不能包含 null，且字段数不能超过 16"));
        }
        List<McpSyncClient> clients = clients();
        if (clients.isEmpty()) {
            return unavailable();
        }

        try {
            List<McpSyncClient> matches = clients.stream().filter(client -> client.listTools().tools().stream()
                    .anyMatch(tool -> tool.name().equals(toolName))).toList();
            if (matches.size() > 1) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "多个连接提供同名工具，请明确唯一服务配置"));
            }
            if (matches.size() == 1) {
                McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder(toolName)
                    .arguments(safeArguments)
                    .build();
                return ResponseEntity.ok(matches.get(0).callTool(request));
            }
        }
        catch (RuntimeException exception) { return downstreamFailure(); }

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "未找到 MCP 工具: " + toolName));
    }

    private ResponseEntity<Map<String, String>> downstreamFailure() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "MCP 服务调用失败，请检查连接与工具参数"));
    }

    private List<McpSyncClient> clients() {
        List<McpSyncClient> clients = this.clientsProvider.getIfAvailable(List::of);
        return clients == null ? List.of() : clients;
    }

    private ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "没有可用的 MCP 客户端，请配置 MCP_SERVER_URL 或启用 stdio profile"));
    }

}
