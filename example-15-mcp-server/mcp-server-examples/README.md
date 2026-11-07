# Spring AI 2.0 MCP Server 示例

这是一个可以独立运行的 Spring AI 2.0 MCP Server WebMVC 示例。项目只使用
spring-ai-starter-mcp-server-webmvc，默认协议是推荐的 Streamable-HTTP。

## 版本和依赖

- Java 17
- Spring Boot 4.1.2
- Spring AI 2.0.2

Windows 测试自动激活父 POM 的 `windows-test-sockets` profile，用
`-Djdk.net.unixdomain.tmpdir=target` 将 JDK NIO 的 socket 临时文件放入测试模块已有的构建目录。
本机 JDK 21.0.5 在默认 TEMP 下曾报 `Unable to establish loopback connection`
与 `Invalid argument: connect`；指定此目录后，真实 Streamable-HTTP 集成测试通过。
这是测试 JVM 的环境配置，不改变 MCP 协议，也不禁用集成测试。
- org.springframework.ai:spring-ai-starter-mcp-server-webmvc

不要在同一个应用中同时加入 WebFlux、STDIO 和其他 MCP Server starter。需要切换
传输时，应建立独立 profile 或独立应用，并保持一次只激活一种 transport。

## 启动

    mvn clean package -Dmaven.build.cache.enabled=false
    mvn spring-boot:run

服务监听 8080，MCP 端点为：

    http://localhost:8080/mcp

普通健康检查为：

    http://localhost:8080/actuator/health

MCP 端点是 JSON-RPC/Streamable-HTTP，不是普通 REST API；请使用 MCP Client、
MCP Inspector 或本目录的集成测试访问。

## MCP 能力

注解扫描会注册：

- 计算工具：basic-calculation、percentage-calculation、scientific-calculation、
  complex-calculation
- 文件工具默认不注册；显式 MCP_ENABLE_FILE_TOOLS=true 后才包含 list-files、read-file、write-file、delete-file
- 资源：system://info 和 guide://{topic}
- 提示：code-review
- 自动完成：code-review 的语言参数

@McpTool、@McpResource、@McpPrompt、@McpComplete 和 @McpArg 均来自
org.springframework.ai.mcp.annotation。@McpToolParam 只有 required 和 description
等 2.0 支持的属性；不要使用 1.x 示例中的 enumValues、minimum 或 maximum。

## 数据目录和安全

文件工具默认使用临时目录下的 spring-ai-mcp-data。可通过环境变量
MCP_DATA_DIRECTORY 指定目录。工具只接受单层文件名，拒绝路径分隔符和 ..，
测试使用构造器注入临时目录，不会污染用户主目录。

默认只监听回环地址；这些路径校验不构成完整文件权限。可选文件工具还需审查符号链接、覆盖、并发与授权，不能在无认证的网络服务上开放。

复合计算只执行三个白名单公式，未使用已经从现代 JDK 移除的 JavaScript
ScriptEngine，也不会执行用户提交的任意表达式。

## Stateless 和 SSE

官方 2.0 仍支持在 WebMVC starter 中切换：

    spring.ai.mcp.server.protocol=STATELESS

Stateless 端点仍配置在 streamable-http.mcp-endpoint（不是
stateless.mcp-endpoint），并且不支持向客户端发起 elicitation、sampling、ping
等双向请求。SSE 自 2.0.0 起已弃用，只有为兼容旧客户端时才应显式选择 SSE。

## 测试

    mvn test -Dmaven.build.cache.enabled=false

McpServerIntegrationTest 会启动随机端口的真实 Spring Boot WebMVC 应用，使用
HttpClientStreamableHttpTransport 初始化 SDK 客户端，列出工具/资源/提示并调用
basic-calculation；不需要模型、API Key、Docker 或外部服务。

当前默认集成测试确认文件工具未暴露，另有显式启用条件测试与直接文件实现测试。父目录 [README](../README.md) 提供完整构建与客户端连接步骤。
