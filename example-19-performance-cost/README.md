# Spring AI 2.0 性能与成本控制示例

本项目用确定性的离线 `ChatModel` 演示用量、预算和批处理边界：

- `Usage` 的标准 token、原生 usage 和 prompt-cache 字段；
- `ChatResponseMetadata` 的响应 ID、模型、限流和 Prompt 过滤索引；
- `UsageCalculator` 在多步流程中的累计语义；
- `TokenCountBatchingStrategy` 的 token 预算批处理；
- 应用层的预估成本、实际成本和预算拒绝；
- Spring AI 的 `gen_ai.client.token.usage` 观察性入口。

Spring AI 不维护跨供应商价格表，`demo.cost.*-per-million` 只是可替换的本地策略。真实价格、缓存计费和模型上下文限制必须以供应商账单与模型文档为准。

## 运行条件

- JDK 17 或更高版本
- Maven 3.9+
- 默认不需要 API Key、模型服务、数据库或 Docker

## 构建与测试

```powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
```

默认测试使用离线模型，不会访问网络。项目使用 Spring AI BOM `2.0.2` 和 Spring Boot `4.1.2`。

缓存语义由 `DEMO_PROMPT_TOKENS_INCLUDE_CACHE` 控制（默认 true）：true 表示 prompt token 已包含缓存，false 表示未缓存输入。接入 2.0.2 Anthropic/Bedrock 时按原生用量校准，不能无条件扣除缓存；2.0.3-SNAPSHOT 才有缓存输入统一纳入 prompt 的修正。费率是示例配置，不是供应商报价。

## 启动离线 HTTP 示例

```powershell
mvn spring-boot:run
```

服务默认监听 `8090`，请求见 `requests.http`：

- `POST /api/performance/estimate`：估算输入 token 和上限成本；
- `POST /api/performance/chat`：在预算允许时调用模型并返回实际 usage；
- `GET /api/performance/usage`：演示两次调用的累计 usage，以及累计后 native usage 不再保留；
- `POST /api/performance/batch`：按 token 上限规划 embedding 文档批次；
- `/actuator/metrics/gen_ai.client.token.usage`：查看模型层 token 指标（调用模型后才会创建该 meter）。

## 启用真实 OpenAI 模型（可选）

凭据必须通过环境变量注入：

```powershell
$env:OPENAI_API_KEY = "从外部密钥管理器读取的值"
$env:SPRING_PROFILES_ACTIVE = "live"
mvn spring-boot:run
```

也可以设置 `OPENAI_BASE_URL`、`OPENAI_CHAT_MODEL`、`OPENAI_TIMEOUT` 和 `OPENAI_MAX_RETRIES`。OpenAI 2.0.2 使用官方 SDK，`live` profile 对应 `spring.ai.openai.timeout` 和 `spring.ai.openai.max-retries`；`max-retries` 是追加重试数。是否重试仍需结合请求幂等性、供应商语义和预算判断。

## 用量与元数据的边界

`/chat` 新增 `metadata` 摘要，包含 responseId、model、usageReported、六个限流字段、Prompt 过滤索引和可选的 OpenAI 原生用量细项。`ResponseMetadataSupport` 不直接输出供应商原生对象或任意过滤详情。OpenAI 的输入音频/缓存 Token 以及输出推理/接受预测/音频/拒绝预测 Token 都经过已解析 SDK 对象测试。

缺少用量时的 `EmptyUsage` 会返回零计数和空 native Map，不能视为免费调用；示例此时返回 `usageReported=false`，Token 与 `actualCost=null`。真实 `DefaultUsage(0, 0)` 仍被识别为已报告的零。DefaultUsage 本身还会将缺失标准计数归一为零，因此应结合供应商原始响应核对完整性。

`EmptyRateLimit` 也返回零，但它表示未报告；摘要中以 `reported=false` 和 null 区分。真实额度用尽的 remaining=0 保持为已报告。reset 是时长，不是绝对时间。

`/usage` 的 `usageComplete` 表示两轮是否都报告 Usage；为 false 时累计数只是已知部分。实际相加两轮非空 Usage 后 native 对象丢弃；如果只有一轮有用量，仍可能保留该轮 native。内置工具 return-direct 可以只有一次模型调用。

全项目 16 个离线测试通过，其中 7 个用量/元数据边界测试。actualCost 是按已报告用量和演示政策计算的金额，不是最终供应商账单。

服务绑定 127.0.0.1:8090，文本最多 12000 个字符，输出预算 1–8192 Token，batch 最多一百项。字符上限不是模型上下文限制，本地估算不构成严格账单上界。

## 安全边界

- 仓库不包含任何 API Key。
- 默认不记录 prompt/completion 正文；生产环境不要为了排查问题长期打开正文日志。
- token 预估只是本地 tokenizer 的近似，模型响应中的 `Usage` 才是实际计费依据。
