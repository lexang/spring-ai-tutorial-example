# Spring AI 2.0 测试与评估示例

本项目配套测试评估博客，基于 [v2.0.2 官方评估文档](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/testing.adoc)，演示 Evaluator、RelevancyEvaluator、FactCheckingEvaluator 和 Testcontainers Service Connection。

## 运行条件

- JDK 17 或更高版本
- Maven 3.9+
- 默认不需要 API Key、模型服务、数据库或 Docker
- 可选的 Ollama Testcontainers 测试需要本机 Docker；它只验证连接详情，不下载模型

## 构建与测试

```powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
```

默认测试使用 `DeterministicJudgeChatModel`，只验证官方评估器的提示词、`EvaluationRequest` 映射和 YES/NO 解析，不宣称具备真实语义判断能力。容器集成测试不属于默认 Surefire 测试集。

当前默认构建 15 个本机/离线测试全部通过，包含供应商 SDK 请求本机固定 Judge 的配置/解析验证。容器 profile 已验证执行入口，但 Docker 环境不可用，1 个容器测试跳过，不能记为集成通过。

若已准备 Docker，并希望运行可选的 Ollama Service Connection 测试：

```powershell
$env:RUN_OLLAMA_TESTCONTAINER_TESTS = "true"
mvn -Pcontainer verify
```

该测试使用 Spring AI 官方源码同样采用的 `ollama/ollama:0.23.1` 镜像标签。容器启动、拉取模型和真实评估调用不属于默认构建的一部分。

## 启动 HTTP 示例

```powershell
mvn spring-boot:run
```

请求示例见 `requests.http`：

- `GET /api/evaluations/demo`：一次相关性通过和一次事实核验失败
- `POST /api/evaluations/relevancy`：评估回答是否与问题和上下文相关
- `POST /api/evaluations/fact-checking`：评估声明是否被文档支持

所有 HTTP 输入都使用 Bean Validation，错误响应为中文。默认端口可以通过 `SERVER_PORT` 环境变量覆盖。

## 显式启用模型 Judge

```powershell
$env:MINIMAX_API_KEY = "从密钥管理器读取的值"
$env:MINIMAX_BASE_URL = "https://api.minimax.io/anthropic"
$env:MINIMAX_MODEL = "MiniMax-M3"
mvn spring-boot:run "-Dspring-boot.run.profiles=live-judge"
```

该 profile 关闭固定 Judge、创建 Anthropic 兼容模型。相同 HTTP 接口返回 mode=model-judge；默认为 contract-demo，不能把默认返回值当真实语义评测。真实请求会产生费用，模型对上下文的 YES 判断也不等于事实已核验。角色、格式和领域能力需按实际 Judge 校准。

结果应检查 pass，而不是统一把 score 当通过概率：FactCheckingEvaluator 的 pass=true 仍可有 score=0，当前发布实现如此。两种评估器只接受去除首尾空白后的精确 yes，解释性输出也可能判为 false。

输入有列表和字符上限，不等于模型 Token 预算；模型/解析失败返回中文 502，不泄露下游原文。博客预约 2026-11-23 09:00 +08:00，slug 为 spring-ai-testing-evaluation。

## 依赖边界

`spring-ai-client-chat` 提供评估器 API；`spring-ai-spring-boot-testcontainers`、Ollama starter 和 Testcontainers 依赖只放在测试路径。API Key 只能通过外部配置注入，仓库不包含任何凭据。
