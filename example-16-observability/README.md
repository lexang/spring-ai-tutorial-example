# Spring AI 2.0 可观测性示例

本项目与中文教程 16 配套，逐节依据官方 `observability/index.adoc`、ChatClient/Advisor 观察 API、向量库观察 API 和 2.0 升级说明编写。

## 版本基线

- Java 17
- Spring Boot 4.1.2
- Spring AI BOM 2.0.2

## 示例做了什么

项目使用确定性离线实现，不需要 API Key 或数据库：

- `OfflineChatModel` 使用官方 `ChatModelObservationDocumentation` 产生模型层观察；
- `ChatClient` 自动产生 `spring.ai.chat.client` 观察；
- `RequestContextAdvisor` 自动产生 `spring.ai.advisor` 观察；
- `SimpleVectorStore` 接入同一个 `ObservationRegistry`，产生 `db.vector.client.operation` 观察；
- Actuator 暴露 `/actuator/metrics` 和 `/actuator/prometheus`，可查看 Micrometer 计时器；
- 流式接口验证观察在 Flux 完成或异常时才停止。

确定性嵌入模型只是测试替身，不代表供应商 EmbeddingModel 的 token 观察；不要把 VectorStore 的观察当成模型自身计费统计。总页的供应商列举应与目标发布源码核对，不能仅凭名单认定其他实现没有观察。

## 启动与验证

```powershell
mvn clean package
mvn spring-boot:run
```

然后按 `requests.http` 顺序请求。先调用同步 Chat 和向量检索，再访问：

```text
http://localhost:8080/actuator/metrics
http://localhost:8080/actuator/prometheus
```

Prometheus 会把 Micrometer 的点号名称转换为下划线，例如 ChatModel 计时器会出现 `gen_ai_client_operation_seconds_count`、`gen_ai_client_operation_seconds_sum` 和 `gen_ai_client_operation_seconds_max` 等序列。低基数键可进入指标；高基数键只应进入追踪。

## 观察名称与关键属性

| 组件 | 观察名称 | 低基数示例 | 高基数示例 |
|---|---|---|---|
| ChatClient | `spring.ai.chat.client` | `spring.ai.kind=chat_client`、`spring.ai.chat.client.stream` | Advisor 名称、会话 ID、工具名 |
| Advisor | `spring.ai.advisor` | `spring.ai.advisor.name`、`spring.ai.kind=advisor` | `spring.ai.advisor.order` |
| ChatModel | `gen_ai.client.operation` | `gen_ai.operation.name`、`gen_ai.system`、请求/响应模型 | temperature、token 用量、finish reason |
| VectorStore | `db.vector.client.operation` | `db.operation.name`、`db.system`、`spring.ai.kind` | query、filter、topK、返回文档 |

2.0 Tool Calling 的观察语义也有变化：工具 span 名称使用 `execute_tool <tool-name>`，`gen_ai.operation.name` 为 `execute_tool`，并新增 `spring.ai.tool.type` 和 `spring.ai.tool.call.id`。本离线示例没有伪造模型工具调用，因此不把工具 observation 写成已验证结果。

## 敏感内容保护

提示词、完成内容、工具参数/结果和向量查询响应默认不导出。只有在明确知道数据边界时，才通过环境变量开启：

```powershell
$env:SPRING_AI_LOG_CHAT_CLIENT_PROMPT = "true"
$env:SPRING_AI_LOG_CHAT_CLIENT_COMPLETION = "true"
```

开启正文日志会触发 Spring AI 的安全警告；生产环境应进行脱敏、采样、访问控制和保留期管理。`spring.ai.tools.observations.include-content` 与 `spring.ai.vectorstore.observations.log-query-response` 同样默认关闭。

关闭响应日志不代表查询不会进入 trace。默认 VectorStore convention 会添加 query/filter 高基数字段；本例使用 PrivacyVectorObservationConvention 不附加它们，保留操作类型、维度等。默认只监听回环地址，健康详情不对外展开；Prometheus 指标端点仍需要生产鉴权与网络隔离。

当前 6 个离线测试通过，包含配置后的向量库真实观察标签检查。另已启动打包程序，确认五类观察指标与 Prometheus 输出存在、查询哨兵不出现在 scrape 中，随后关闭进程。模型与 Usage 是人工离线演示值，不是供货方延迟或成本数据。

博客预约 2026-11-22 09:00 +08:00，slug 为 spring-ai-observability。

## 真实供应商模型

生产项目通常加入 `spring-ai-starter-model-openai`、Anthropic 或其他供应商 starter。Starter 会把供应商的 ChatModel、HTTP observation 和 Boot 的 ObservationRegistry 接起来；替换本项目的 `OfflineChatModel` 时，应保留 `spring-boot-starter-actuator`，并根据官方供应商页面核对 tracing bridge、流式 HTTP span 的父子关系和模型支持范围。API Key 只能通过环境变量或外部密钥系统注入。
