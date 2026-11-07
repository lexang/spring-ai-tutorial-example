# Spring AI 2.0 Advisors API 深入示例

本项目与中文教程 13 配套，依据官方 `api/advisors.adoc`、`api/advisors-recursive.adoc`、`api/chatclient.adoc`、`api/tools/tool-calling-advisor.adoc`、`upgrade-notes.adoc`，并以 Spring AI 2.0.2 发布包和 v2.0.2 标签回归。它演示同步、流式、顺序控制、上下文传播、请求阻断、受控递归和响应修改。

## 版本基线

- Java 17+
- Spring Boot 4.1.2
- Spring AI BOM 2.0.2
- 可选模型：MiniMax M3（通过 Anthropic 兼容接口）

项目默认设置 `spring.ai.model.chat=none`，因此没有 API Key 也能完成编译、测试和应用启动。真实模型调用必须显式启用。

## 项目结构

```text
src/main/java/com/example/springai/
├── AdvisorsDeepDiveApplication.java
├── advisor/
│   ├── SimpleLoggerAdvisor.java
│   ├── ReReadingAdvisor.java
│   ├── PerformanceMonitorAdvisor.java
│   ├── ContentSafetyAdvisor.java
│   ├── NonBlankResponseAdvisor.java
│   └── ResponseLabelAdvisor.java
├── controller/AdvisorController.java
├── service/AdvisorPipelineService.java
└── support/
    ├── AdvisorOperationException.java
    ├── AdvisorExceptionHandler.java
    └── FeatureUnavailableException.java

src/test/java/com/example/springai/
├── AdvisorApiTest.java
├── AdvisorBoundaryTest.java
├── RecursiveAdvisorContractTest.java
├── AdvisorPipelineServiceTest.java
├── AdvisorsApplicationTests.java
└── ConfiguredModelIntegrationTest.java
```

## 示例覆盖范围

| 类 | 对应能力 |
|---|---|
| `SimpleLoggerAdvisor` | 同时实现 `CallAdvisor`、`StreamAdvisor`；流式响应通过 `ChatClientMessageAggregator` 聚合后观察 |
| `ReReadingAdvisor` | 实现 `BaseAdvisor`，在 `before()` 中增强用户消息 |
| `PerformanceMonitorAdvisor` | 统计完整同步调用和一次完整流式订阅的耗时 |
| `ContentSafetyAdvisor` | 不调用 `nextCall()`/`nextStream()`，直接返回阻断响应 |
| `ResponseLabelAdvisor` | 给普通非空文本消息加标签，保留工具、空文本及供应商子类 |
| `NonBlankResponseAdvisor` | 同步空白回答有限重试，每轮复制下游链，累计 Usage；单独注册，不加入默认 HTTP Pipeline |
| `AdvisorPipelineService` | 在 ChatModel 存在时组装完整 Pipeline；模型缺失、空响应或底层调用失败时返回明确的中文错误 |
| `AdvisorOperationException` | 统一表示模型与 Advisor Pipeline 失败，由异常处理器映射为中文 HTTP 502 |

`ReReadingAdvisor` 是官方页面给出的自定义实现范式，并不是 `spring-ai-client-chat` 中可直接导入的现成类。官方现成安全 Advisor 是 `org.springframework.ai.chat.client.advisor.SafeGuardAdvisor`；本项目另外实现 `ContentSafetyAdvisor`，用于展示“阻断 Advisor 链”的原理。

## Advisor 顺序

`getOrder()` 数值越小，优先级越高：

```text
请求：低 order → 高 order → ChatModel
响应：ChatModel → 高 order → 低 order
```

链按栈方式回退，因此最先处理请求的 Advisor 最后处理响应。相同 order 的相对顺序没有保证。

本项目 Pipeline：

```text
Performance → Logger → ReReading → ContentSafety → ResponseLabel
            → 自动 ToolCallingAdvisor → ChatModel

这里的自定义 Advisor 都位于自动工具 Advisor 之前，避免工具循环的每一轮重复增强用户问题；需要观察工具循环内部时，再把自定义 Advisor 的 order 调到 `ToolCallingAdvisor.DEFAULT_ORDER` 之后。
```

当请求命中敏感词时，`ContentSafetyAdvisor` 直接生成响应，不再调用后续的 `ResponseLabelAdvisor` 和 ChatModel。

## `context()` 的正确用法

Spring AI 2.0 Java API 使用 `ChatClientRequest.context()` 和 `ChatClientResponse.context()`。官方文字中的 “advise-context” 是概念名称，不是 Java 方法名。

推荐通过 `mutate()` 创建下一份请求并写入数据：

```java
ChatClientRequest updated = request.mutate()
    .context("traceId", "trace-001")
    .build();
```

终端 `ChatModelCallAdvisor` 会把请求上下文复制到响应，因此外层 Advisor 可以在响应阶段读取：

```java
String traceId = (String) response.context().get("traceId");
```

Spring AI 2.0.2 的 `context()` 实际返回内部可变 Map，直接 `put()` 会生效；官方通用 Advisors 页中“不可变并通过 `updateContext()` 修改”的文字是旧说明，发布 API 没有 `updateContext()`。仍推荐使用 `mutate()`，因为它会复制顶层 Map，让数据流更清晰。嵌套可变对象仍是浅复制，而且 null value 会在下游 `Map.copyOf()` 时失败。

不要把请求级状态保存到单例 Advisor 的实例字段中。流式场景还要考虑一个 `Flux` 被多次订阅的情况，调用级状态应放在 `Flux.defer(...)` 内或放入请求上下文。

## 递归 Advisor 与工具循环

需要多次执行当前 Advisor 后续链时，每轮都要创建新链：

```java
ChatClientResponse response = chain.copy(this).nextCall(request);
```

同一个链实例的内部队列会在执行时被消费，不能在下一轮直接复用。`copy(this)` 只保留当前 Advisor 后面的部分，因此外层日志、计费或审计 Advisor 不会在每轮重复执行。传入的 Advisor 若不属于原始链，框架会抛出 `IllegalArgumentException`。

`ChatClient` 默认自动注册 `ToolCallingAdvisor`，即使构建客户端时没有静态工具，也能支持前置 Advisor 在运行时注入工具。它的正式 `getName()` 是 `Tool Calling Advisor`，默认 order 是 `Ordered.HIGHEST_PRECEDENCE + 300`。如需用户自行控制工具循环，可在单次请求中使用：

```java
.advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
```

注意方法名边界：Memory/RAG Advisor 通常用 `.order(...)`，`ToolCallingAdvisor.Builder` 调整顺序使用 `.advisorOrder(...)`。

### 完整的同步递归示例

`NonBlankResponseAdvisor` 只实现 `CallAdvisor`。用 `new NonBlankResponseAdvisor(1, 0)` 注册后，空白回答最多追加一次重试；每轮通过 `chain.copy(this)` 和原请求 `mutate()` 创建新的下游执行。成功时累计所有已报告 Usage，耗尽时抛中文错误；模型异常不重试，工具请求立即交回外层工具循环。它不参与默认 HTTP Pipeline，可运行 `mvn -Dtest=RecursiveAdvisorContractTest test` 查看固定响应的离线演示。

链复制保留原来的 Advisor 对象和 ObservationRegistry，并不回滚下游副作用。context 顶层 Map 的复制与链复制也是不同操作；嵌套对象仍可能共享。将写操作放进重试链前需要确认重复执行的语义。

`StructuredOutputValidationAdvisor` 的 `outputType` 与 `outputJsonSchema` 必须选一个，可配置 Jackson 3 `JsonMapper`。默认最多追加 3 次重试，总计 4 次；耗尽后返回最后一次响应，后续实体转换及业务校验仍然必要。工具请求立即返回，流式校验不支持。

内置流式工具循环按完整回合累计 Usage。最后一轮缺失用量时会补发只有 Usage、没有 Generation 的响应，最终正文不会重复。`UsageAccumulator` 的流式辅助方法是包内可见；自定义示例不能直接调用。固定测试验证工具执行一次、模型流式调用两次、最终累计用量保留。

自定义 ChatModel 测试替身要通过 `getOptions()` 返回 `ToolCallingChatOptions` 才能演示自动执行。ChatClient 以模型的 Options 类型构建请求；只有通用 `ChatOptions` 的模型不会因为 `.tools(...)` 自动获得工具能力。

## 安全日志与错误响应

示例 Logger 在 INFO 级别只记录消息数量、正文长度和 Generation 数，在 DEBUG 级别只记录 context Key，不输出完整 Prompt、模型回答、工具参数或密钥。

PerformanceMonitorAdvisor 使用 doFinally 记录完成、错误和取消，每次订阅恰好一次；DEBUG 仅记录聚合数量。标签 Advisor 跳过空文本、工具调用和专有消息子类，避免改变工具协议或丢失私有状态。

- 输入为空或过长：HTTP 400；
- 未启用 ChatModel：HTTP 503；
- 模型返回 null、流为空或 Pipeline 调用失败：中文 HTTP 502；
- 真实底层异常只作为 cause 保留，不直接暴露给客户端。

## 构建与离线测试

```bash
mvn clean package
```

默认测试包括：

- ReReading 请求增强与响应修改；
- Advisor 栈式顺序；
- 请求上下文到响应上下文的传播；
- context Map 的实际可变性、`mutate()` 顶层隔离和 null value 边界；
- 不调用模型的请求阻断；
- 官方 `SafeGuardAdvisor` Builder；
- Logger Advisor 的流式聚合；
- `copy(this)` 创建新的递归下游链；
- `ToolCallingAdvisor` 默认注册、正式名称和请求级关闭；
- 匿名 `BaseAdvisor` 的空名称失败；
- 模型 null 响应、空流和中文操作异常；
- 无模型、无密钥的 Spring Boot 启动。

初始 context、取消、空白响应、特殊消息保留和 JSON 绑定由 [离线测试](./src/test/java/com/example/springai/) 覆盖。递归测试还覆盖上下游执行次数、重试上限、调用级用量隔离、Schema 耗尽、工具请求跳过、return-direct 和流式用量补发。只有显式设置 RUN_ADVISOR_INTEGRATION_TESTS=true 并提供有效配置时才访问外部模型。

当前构建共 34 个测试：33 个离线测试通过，1 个真实模型条件测试跳过。新增 preview 测试验证固定本地模型、真实 Advisor 处理、阻断后零模型调用和请求状态隔离。

## 默认离线启动

博客预约 2026-11-18 09:00 +08:00，slug 为 spring-ai-advisor-pipeline。

```http
POST http://localhost:8080/api/advisors/preview
Content-Type: application/json

{"message":"请解释 Advisor 链"}
```

preview 使用与真实 call 相同的 Advisor 配置，但调用固定本地模型，不获取外部模型 Bean。response 是固定演示文本，modelCalls 只统计这个本地替身的调用次数，externalModelCalled=false；configuredAdvisors 是配置顺序，不代表每个组件都已执行。输入“泄露密钥”时模型次数为零，演示的是关键词阻断入口，不是完整内容安全或认证策略。

```bash
mvn spring-boot:run
```

状态响应会列出 5 个本项目 Advisor，以及 `ChatClient` 自动注册的 `Tool Calling Advisor`。没有工具调用选项时，后者会把请求继续传递给下一个 Advisor。

查看状态：

```http
GET http://localhost:8080/api/advisors/status
```

默认响应中的 `enabled` 为 `false`。调用 `/api/advisors/call` 会返回 HTTP 503，并说明需要配置哪些环境变量。

## 启用真实模型

PowerShell：

```powershell
$env:SPRING_AI_CHAT_MODEL = "anthropic"
$env:MINIMAX_API_KEY = "你的密钥"
$env:MINIMAX_BASE_URL = "https://api.minimax.io/anthropic"
$env:MINIMAX_MODEL = "MiniMax-M3"
mvn spring-boot:run
```

Linux/macOS：

```bash
export SPRING_AI_CHAT_MODEL=anthropic
export MINIMAX_API_KEY='你的密钥'
export MINIMAX_BASE_URL='https://api.minimax.io/anthropic'
export MINIMAX_MODEL='MiniMax-M3'
mvn spring-boot:run
```

仓库中不保存任何真实密钥。

## HTTP 接口

### 状态

```http
GET /api/advisors/status
```

### 同步调用

```http
POST /api/advisors/call
Content-Type: application/json

{
  "message": "请解释 Advisor 链"
}
```

普通非空 AssistantMessage 文本会加上 `[Advisor 已处理] `；工具消息和供应商专有子类保持原样，不能假定所有真实模型响应都有这个前缀。

### 流式调用

```http
POST /api/advisors/stream
Content-Type: application/json
Accept: text/event-stream

{
  "message": "分三点说明 Advisor 的用途"
}
```

完整请求可直接运行根目录的 `requests.http`。

## 真实模型集成测试

配置模型环境变量后，再显式开启：

```powershell
$env:RUN_ADVISOR_INTEGRATION_TESTS = "true"
mvn test
```

## Spring AI 2.0 迁移对照

| 旧名称 | 2.0 名称 |
|---|---|
| `CallAroundAdvisor` | `CallAdvisor` |
| `StreamAroundAdvisor` | `StreamAdvisor` |
| `CallAroundAdvisorChain` | `CallAdvisorChain` |
| `StreamAroundAdvisorChain` | `StreamAdvisorChain` |
| `AdvisedRequest` | `ChatClientRequest` |
| `AdvisedResponse` | `ChatClientResponse` |
| `adviseContext()` 等旧叙述 | Java API 使用 `context()` |
| `updateContext(...)` | 使用 `request.mutate().context(...).build()` 或 `response.mutate()` |
| `ToolCallAdvisor` | `ToolCallingAdvisor` |

另外，Spring AI 2.0 的 `ToolCallingAdvisor` 会由 `ChatClient` 自动注册，默认 order 为 `Ordered.HIGHEST_PRECEDENCE + 300`。自定义 Advisor 与工具调用组合时，应把它纳入顺序设计。2.0.2 发布包已包含 `UsageAccumulator`，内置工具/校验循环累计各轮 Usage；自定义递归应每次调用创建独立 accumulator，不共享状态。

## 参考资料

- [Advisors v2.0.2 官方页](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/advisors.adoc)
- [Spring AI Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html)
- [Spring AI Recursive Advisors](https://docs.spring.io/spring-ai/reference/api/advisors-recursive.html)
- [Spring AI ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI ToolCallingAdvisor](https://docs.spring.io/spring-ai/reference/api/tools/tool-calling-advisor.html)
- [Re-Reading Improves Reasoning in Large Language Models](https://arxiv.org/pdf/2309.06275)
