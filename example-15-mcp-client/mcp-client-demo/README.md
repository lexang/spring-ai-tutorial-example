# Spring AI 2.0 MCP Client 示例

这个项目只使用 spring-ai-starter-mcp-client，演示同步客户端、Streamable-HTTP/STDIO 配置、通知注解、工具过滤、工具命名前缀和上下文元数据转换。

## 版本和依赖

- Java 17
- Spring Boot 4.1.2
- Spring AI 2.0.2
- 标准客户端 starter：org.springframework.ai:spring-ai-starter-mcp-client

标准 starter 包含 STDIO、Java HttpClient 的 SSE 和 Streamable-HTTP 传输，也支持统一选择 SYNC 或 ASYNC。传输实现与同步/异步类型是不同选择；本例只选标准 starter 和 SYNC，不再重复加入 WebFlux starter。

## 快速运行（离线）

    mvn clean package -Dmaven.build.cache.enabled=false
    mvn spring-boot:run

默认 spring.ai.mcp.client.enabled=false，不会启动 npx 子进程，也不会访问远程地址。

    GET http://localhost:8088/api/mcp/status
    GET http://localhost:8088/actuator/health

## 连接本地 Streamable-HTTP 服务

先启动 example-15-mcp-server，再运行：

    $env:MCP_SERVER_URL = "http://localhost:8080"
    mvn spring-boot:run "-Dspring-boot.run.profiles=remote"

application-remote.yml 使用 spring.ai.mcp.client.streamable-http.connections.local-server，
端点默认为 /mcp。连接成功后，GET /api/mcp/tools 会读取 McpSyncClient.listTools()；
POST /api/mcp/tools/{name} 会发送 CallToolRequest。

## STDIO 配置

application-stdio.yml 通过 servers-configuration 读取 Claude Desktop 格式 JSON。Windows
示例和 Unix 示例分别位于 src/main/resources/mcp-servers-windows.json 和
src/main/resources/mcp-servers-linux.json。

Unix 环境运行时显式指定：

    MCP_STDIO_CONFIG=classpath:mcp-servers-linux.json mvn spring-boot:run -Dspring-boot.run.profiles=stdio

JSON 中的命令和路径需要由使用者自行安装并配置；仓库不会自动下载或执行第三方服务。

## 代码要点

- CustomMcpSyncClientCustomizer 使用 2.0 正确的
  org.springframework.ai.mcp.customizer.McpClientCustomizer<McpClient.SyncSpec>。
- CustomMcpToolFilter、CustomToolNamePrefixGenerator 和
  CustomToolContextToMcpMetaConverter 均使用 2.0 包名。
- 上下文转换器只允许已审核标量 userId/tenantId/traceId，不把任意对象、嵌套 Map 或其他字段放入 _meta；这不是完整 DLP 或认证策略。
- 客户端注解必须写 clients，并且名称要匹配连接配置；示例使用 local-server。

## 测试

    mvn test -Dmaven.build.cache.enabled=false

测试不需要模型、密钥或外部 MCP 服务。真实连接由 remote/stdio profile 显式开启。

REST 入口对直接 SDK 调用另做计算工具白名单；McpToolFilter 不能替代这层检查。同名工具来自多个连接时返回 409，不静默调用第一项。当前接口不是公共鉴权服务，默认回环监听，主线说明见 [父目录 README](../README.md)。
