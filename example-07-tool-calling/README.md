# Spring AI 2.0 Tool Calling 示例

本项目配套两篇原创博客教程，使用 Spring AI 2.0.2、Spring Boot 4.1.2、Java 17 编译目标。主线是 **只读库存查询**：用同一接口对比自动执行、手动循环、仅查看工具请求和直接返回。库存来自固定 Map，没有连接真实库存系统，也不提供下单、预订或扣减库存。

2.0.2 的 ToolCallingManager 默认每工具 40 次、每 turn 总计 150 次；这些上限不是应用层循环超时或授权校验的替代。人工确认 `requiresConfirmation` 属于 2.1，不在本示例中使用。

## 环境要求

- Java 17 或更高版本
- Maven 3.9 或更高版本
- 可访问 MiniMax Anthropic 兼容接口的 API Key

默认通过 Anthropic starter 访问 MiniMax 兼容端点。以下模型名是可覆盖的示例值，运行前确认账户支持工具调用和对应模型；本项目没有对真实供应商效果或费用进行评测。密钥只从环境变量读取：

```powershell
$env:MINIMAX_API_KEY="你的密钥"
$env:ANTHROPIC_BASE_URL="https://api.minimax.io/anthropic"
$env:ANTHROPIC_CHAT_MODEL="MiniMax-M3"
```

Linux 或 macOS：

```bash
export MINIMAX_API_KEY="你的密钥"
```

## 运行与验证

```bash
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn spring-boot:run
```

应用默认监听 `http://localhost:8080`。可直接执行 [requests.http](./requests.http)，或调用以下端点：

```bash
curl "http://localhost:8080/api/datetime?question=现在几点了"
curl "http://localhost:8080/api/calculator?expression=15%2B27"
curl "http://localhost:8080/api/advanced/tool-context?question=查询客户1" \
  -H "X-Tenant-ID: tenant-a" -H "X-User-ID: user-001"
curl "http://localhost:8080/api/default-tools/runtime-append?question=北京天气怎么样，现在几点了"
```

这些 HTTP 示例会调用外部模型并可能产生费用；离线单元测试不会访问模型。模型回答具有非确定性，不要求它每次选择相同工具或产生相同文本。新接口只记录工具名，不记录完整问题、参数和结果；旧扩展示例仍有详细教学日志，不适合直接部署为公开服务。

## 两篇博客的主线接口

`POST /api/tools/query`，JSON 请求：

```json
{"question":"请查询 BOOK-001 的可用库存","mode":"AUTO"}
```

| mode | 执行方式 |
|---|---|
| AUTO（默认） | ChatClient 的 ToolCallingAdvisor 自动执行只读工具循环 |
| MANUAL | ChatModel 返回原始响应，应用使用 ToolCallingManager 循环执行 |
| PLAN_ONLY | 发出模型请求，但不执行它返回的工具请求；仍可能产生模型费用 |
| DIRECT | 使用 returnDirect 工具，执行后跳过再次调用模型 |

请求缺少问题、超过 2000 字符、JSON 或枚举无效，返回 400。模型/工具/协议执行失败返回 502，不暴露下游异常正文。可控终止返回 200 JSON，但 `status` 是 `TOOL_CALL_LIMIT` 或 `MODEL_CALL_LIMIT`，调用方必须检查它，不能把所有 200 当成完整业务答案。

正常结果包含 `status`、`answer`、`finishReason`、`pendingTools`、`usage`。库存 `BOOK-001=12`、`BOOK-002=0` 来自服务端固定的 `demo-shanghai` 仓库。`warehouseId` 通过 ToolContext 注入，不进入模型工具输入 Schema；工具结果包含演示仓库信息并会发给模型，因此 ToolContext 不意味着工具输出自动保密。

`PLAN_ONLY` 只是教学演示，不是身份认证、可恢复人工审批或权限策略；`DIRECT` 返回工具序列化结果字符串，不保证是自然语言。生产系统必须在工具实现和业务服务中验证身份、数据访问范围及写操作幂等性。

## 预算与验证范围

配置将本项目工具限制收紧为每工具 3 次、每 turn 总计 6 次，默认框架上限仍是 40/150。新接口另设最多 6 次模型调用，可通过 `TOOL_MAX_MODEL_CALLS` 覆盖为 1–20，预算在服务端执行。它不是网络超时，也不会撤销此前已执行的工具。

两种执行模式每次请求新建用量/预算记录器，不共享可变计数；自动模式记录器放在工具循环内部，手动模式逐轮记录。`usage` 只累计模型报告的 Token，缺失值不当零；`reportedCalls < modelCalls` 时 `complete=false`，不能据部分 Token 推算完整账单。直接返回仍计入首轮模型用量。

`mvn clean package` 共 31 个离线测试通过。其中 20 个主线测试使用固定 ChatModel 配合真实 Spring AI Advisor、Callback 和 Manager，验证消息配对、上下文隔离、预算、用量缺失、直接返回、异常类型分发和 HTTP 错误边界；另 11 个保留测试覆盖旧扩展示例与上下文启动。没有访问真实模型，也没有把固定数据测试当成供应商性能评测。

## 示例范围

- `ToolCallingController`：`@Tool` 基础用法、计算、天气、数据库和多工具协作
- `AdvancedToolCallingController`：`FunctionToolCallback`、显式 `ToolCallback` Bean、`ToolContext`、`returnDirect`、转换器与异常处理
- `MethodToolCallbackController`：通过反射和 `MethodToolCallback` 动态定义工具
- `DefaultToolsController`：`defaultTools(...)` 与调用级 `.tools(...)` 的追加语义
- `UserControlledToolsController`：直接调用 `ChatModel`，使用 `ToolCallingManager` 手动执行完整循环；包含最多 10 轮保护、`returnDirect` 分支和只持久化最终用户/助手交换的 ChatMemory 示例
- `ApiExceptionHandler`：统一返回中文错误响应，并避免向调用方泄漏模型供应商、文件路径或下游异常细节

Spring AI 2.0 的关键迁移点：

1. `ChatClient` 自动注册 `ToolCallingAdvisor` 并负责工具循环；直接调用 `ChatModel` 时必须由应用执行循环。
2. ChatClient 使用 `.tools(...)` 和 `.defaultTools(...)`；已移除的 `.functions(...)`、`toolNames(...)` 和裸 `Function` Bean 名称解析不能继续使用。
3. 调用级 `.tools(...)` 会追加到 ChatClient 的默认工具。低层 `ToolCallingChatOptions.toolCallbacks(...)` 则是有效的 ChatModel API，运行时列表会替换模型默认工具。
4. `ToolContext` 位于 `org.springframework.ai.chat.model`，只承载应用上下文，不向模型暴露，也不再包含对话历史。

## 配置说明

模型与服务地址在 `src/main/resources/application.properties` 中配置。切换到其他协议供应商时，需要同时替换 starter、属性前缀和模型名，不能只修改 API Key。默认异常处理器只把 RuntimeException 的消息反馈给模型，checked exception 和 Error 会重新抛出；要连运行时工具异常也由调用方捕获，可配置：

```properties
spring.ai.tools.throw-exception-on-error=true
```

示例默认使用 Spring AI 自动配置的异常处理器，上面的属性因此可以直接生效。若要演示
自定义 `ToolExecutionExceptionProcessor`，使用 `custom-tool-errors` profile：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=custom-tool-errors
```

项目中的 `@Schema` 注解直接复用 Spring AI 模型模块带来的 Swagger 注解版本，不再额外
锁定旧版本；工具 Schema 的必填字段和文件系统越界行为由离线契约测试覆盖。

旧 `UserControlledToolsController` 的手动循环会检查 `ToolExecutionResult.returnDirect()` 并限制最多 10 轮；新库存主线按前述独立模型预算停止。超过
预算或模型未返回可展示文本时会终止请求。控制器异常由统一处理器转换为稳定的中文 JSON，
统一处理器只记录异常类型；旧工具自己的详细教学日志另行说明，不应包含真实敏感数据。

本项目未启用 Tool Search，因为示例工具集规模较小。大型工具集可按教程添加 `spring-ai-starter-tool-search-advisor`，并为每次请求提供会话 ID。

## 对应教程

- 基础篇：Spring AI 工具调用：接入只读 Java 能力，预约 2026-11-10 09:00 +08:00。
- 进阶篇：工具执行控制：循环、预算与异常边界，预约 2026-11-11 09:00 +08:00。

两篇共用本项目，不需要重复创建两个同款工程。旧控制器、函数工具、转换器、文件读取和模拟天气示例保留供扩展阅读，未在博客逐个铺开；天气与客户数据同样是演示数据。不要公开暴露这些未接入认证的教学接口。

官方依据：[v2.0.2 工具文档](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/tools.adoc)、[ChatModel 工具调用](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/tools/chatmodel-tool-calling.adoc)、[升级说明](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/upgrade-notes.adoc)。
