# Spring AI MCP Server 教程示例

本目录是 Maven 父工程，运行模块为 [mcp-server-examples](./mcp-server-examples/README.md)。使用 Spring AI 2.0.2、Boot 4.1.2、Java 17+，通过 WebMVC starter 提供 Streamable-HTTP MCP 服务，不需要模型 Key。

博客预约 2026-11-21 09:00 +08:00，slug 为 spring-ai-mcp-server。客户端文章先连接本例，服务端篇再讲工具实现。

## 构建与启动

先停止占用 8080 的其他教程应用：

```powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn -pl mcp-server-examples spring-boot:run
```

默认只监听 127.0.0.1:8080，协议端点为 /mcp。它不是“POST 一个业务 JSON”就能调用的普通 REST 接口；使用 [配套客户端](../example-15-mcp-client/README.md) 或本目录的 SDK 集成测试访问。

## 默认暴露内容

默认注册四个计算工具及 CatalogService 的资源、提示与完成。输入由生成的 JSON Schema 和 Java 业务代码共同验证：除零、非有限输入、溢出及非白名单公式会失败。ToolCallResult 的 isError 应由调用方检查，不能只看 HTTP 状态。

文件工具实现保留但默认不注册。仅在你控制的本地专用数据目录中，显式设置 MCP_ENABLE_FILE_TOOLS=true 和 MCP_DATA_DIRECTORY 才启用；不要把它当作生产文件权限方案。它没有完整解决符号链接、竞态、覆盖恢复和用户授权。

## 安全边界

MCP transport 默认不提供认证。当前回环地址与小工具清单只是教学限制；部署到网络前必须添加认证、授权、来源限制、配额和审计。readOnlyHint 也是声明，不是权限检查。

应用的 server.version 是业务握手标识，不是 Spring AI 或 SDK 版本。依赖由一个 BOM 管理，不能因为升级说明讨论 SDK 2.0 就自行覆盖当前标签的 SDK 版本。

Windows 测试沿用已有的 windows-test-sockets 配置，使用模块 target 目录放置 JDK NIO 临时 socket；这是测试 JVM 环境配置，不跳过协议测试。父工程的测试插件版本由 Boot 管理。

## 验证结果

当前 clean package 共 13 个测试全部通过，包括随机端口的真实 Streamable-HTTP SDK 连接、工具/资源/提示发现、计算调用及非法参数的工具错误。还验证文件工具默认不注册、显式启用条件保留以及直接实现的读写测试。文件测试只使用自己的临时目录，不清理用户数据。

客户端工程 7 个测试通过，另有两端真实 JAR 的本机闭环，计算结果为 15、禁止工具返回 403。两个临时程序已经关闭，不依赖 Docker、模型或外部凭据。

## 官方依据

- [v2.0.2 Server Starter](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/mcp/mcp-server-boot-starter-docs.adoc)
- [v2.0.2 Server 注解](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/mcp/mcp-annotations-server.adoc)
- [v2.0.2 Streamable-HTTP](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/mcp/mcp-streamable-http-server-boot-starter-docs.adoc)
