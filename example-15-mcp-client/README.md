# Spring AI MCP Client 教程示例

本目录是 Maven 父工程，运行模块是 [mcp-client-demo](./mcp-client-demo/README.md)。基线为 Spring AI 2.0.2、Spring Boot 4.1.2、Java 17+，SDK 版本由 BOM 管理，不把业务服务声明的 version 当成库版本。

博客预约 2026-11-20 09:00 +08:00，slug 为 spring-ai-mcp-client。主线是连接、发现和直接调用本仓库的计算工具，不需要聊天模型或 API Key。

## 构建与默认启动

```powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn -pl mcp-client-demo spring-boot:run
```

默认客户端关闭连接，只监听 127.0.0.1:8088，status 与健康检查可离线使用。Actuator 正常不表示远程 MCP 服务已连接；status 也只统计客户端实例，实际 tools 查询才验证协议请求。

## 连接配套服务

先在另一终端进入 [服务端目录](../example-15-mcp-server/README.md)，执行其构建和启动步骤，再回到本目录：

```powershell
$env:MCP_SERVER_URL = "http://127.0.0.1:8080"
mvn -pl mcp-client-demo spring-boot:run "-Dspring-boot.run.profiles=remote"
```

```http
GET http://127.0.0.1:8088/api/mcp/tools
```

```http
POST http://127.0.0.1:8088/api/mcp/tools/basic-calculation
Content-Type: application/json

{"a":12,"b":3,"operation":"+"}
```

完整协议结果保留 isError 与 content。HTTP 200 不代表工具业务成功；禁止工具返回 403，同名服务歧义返回 409，无连接返回 503，SDK 异常返回不泄露下游原文的中文 502。

## 边界

- REST 入口与 MCP 回调都仅允许四个已审核的演示计算工具。清单不是身份认证，也不能让任意同名第三方工具自动可信。
- McpToolFilter 仅过滤 Spring AI 工具回调，不替代直接 McpSyncClient.callTool 的范围检查。两层规则在本例中显式共享。
- ToolContext 仅白名单的 userId/tenantId/traceId 标量进入 _meta，任意对象和嵌套 Map 不外传；字段内容仍需业务审查，元数据不是认证凭据。
- 自动客户端是按服务器连接共享的 session，不是每用户独立会话。多租户隔离、授权与生命周期管理需要另行设计。
- STDIO 配置只作扩展，默认不执行 npx。命令、环境变量、目录和程序来源必须审核，不能运行不可信服务器。
- 本目录使用标准 JDK HttpClient 传输，SYNC/ASYNC 选择与标准/WebFlux starter 选择是两件事，不把异步误写成只能用 WebFlux。

## 验证结果

当前客户端父工程 clean package 的 7 个测试全部通过；配套服务端 13 个测试全部通过。另已运行两端打包 JAR，在随机回环端口验证自动配置连接、四个计算工具发现、12+3=15 以及禁止工具 403，最后关闭本轮创建的两个进程。没有模型请求、API Key、Docker 或外部 MCP 服务。

若本机 Windows JDK 的 NIO 临时 socket 路径触发环境错误，可在已有专用工作目录中用 JVM 参数 -Djdk.net.unixdomain.tmpdir=. 启动 JAR；这不是协议或权限配置。不要用此参数去修改别的运行中服务。

## 官方依据

- [v2.0.2 MCP Client](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/mcp/mcp-client-boot-starter-docs.adoc)
- [v2.0.2 工具调用](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/tools.adoc)
- [v2.0.2 升级说明](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/upgrade-notes.adoc)
