# Spring AI 2.0 Prompt Engineering 示例

本项目通过分类、订单提取和代码审查演示提示构造，再提供两阶段背景生成和有限采样。官方资料用于核实 API，默认预览不代表真实模型效果。

## 版本和安全边界

- Spring AI BOM `2.0.2`
- Spring Boot `4.1.2`
- Java `17+`
- 默认 `spring.ai.model.chat=none`，构建和预览不需要 API Key。
- 真实模型调用只在 `live` profile 下启用；密钥从 `MINIMAX_API_KEY` 读取，仓库不保存凭据。
- 示例不会执行模型生成的代码，也不会把“思维链”当作必须暴露的内部数据。

## 构建和测试

```bash
mvn clean package -Dmaven.build.cache.enabled=false
```

11 个测试覆盖提示词渲染、system/user 消息、两阶段传参、严格多数与平票、无效采样、模型 502、非法 JSON 和参数校验。默认构建不访问模型服务。

## 运行离线预览

```bash
mvn spring-boot:run
```

然后打开 `requests.http` 中的 `/api/prompts/*` 请求。预览端点只返回最终提示词，便于检查变量是否已经渲染、用户输入是否被放在明确的上下文边界内。

## 运行真实模型示例

先准备环境变量，再显式启用 `live` profile：

```bash
export MINIMAX_API_KEY=your-key
export MINIMAX_BASE_URL=https://api.minimax.io/anthropic
export MINIMAX_MODEL=MiniMax-M3
mvn spring-boot:run -Dspring-boot.run.profiles=live
```

Windows PowerShell：

```powershell
$env:MINIMAX_API_KEY = "your-key"
$env:MINIMAX_BASE_URL = "https://api.minimax.io/anthropic"
$env:MINIMAX_MODEL = "MiniMax-M3"
mvn spring-boot:run "-Dspring-boot.run.profiles=live"
```

`/api/live/step-back` 会先请求通用背景，再把背景注入第二次请求；`/api/live/self-consistency` 会重复调用模型并进行标准化答案投票，因此会增加延迟和费用。

服务默认绑定 127.0.0.1:8089。输入最多 12000 个字符，samples 必须为 1–7，不再静默截断。模型返回空内容或调用失败为中文 502，非法 JSON 与输入为 400，响应不暴露下游异常详情。

采样只提取 FINAL= 后的非空答案，缺少标记计为 invalidSamples；全部无效时失败。vote.strictMajority 以有效样本为分母，只有超过一半才有 answer，平票和相对多数都返回 null。应同时检查 requestedSamples 与 validSamples，这不是事实正确性保证。

树状分支与自动提示工程仅提供提示构造，不实现搜索树和自动评分。真实供应商配置是可替换示例，本次没有远程模型效果验证。

## 目录结构

```text
example-18-prompt-engineering/
├── pom.xml
├── README.md
├── requests.http
└── src/
    ├── main/java/com/example/promptengineering/
    │   ├── PromptEngineeringApplication.java
    │   ├── PromptPatternCatalog.java
    │   ├── PromptPreviewController.java
    │   └── LivePromptController.java
    ├── main/resources/
    │   ├── application.properties
    │   └── application-live.properties
    └── test/java/com/example/promptengineering/
        ├── PromptPatternCatalogTest.java
        └── PromptEngineeringApplicationTest.java
```

## 关键实现对应关系

| 官方模式 | 项目实现 |
|---|---|
| Zero-shot、Few-shot | `PromptPatternCatalog.zeroShot/fewShot` |
| System、Role、Context | `systemAndRole`、`contextual` |
| Step-back | `stepBack` + `completeStepBack` 两阶段调用 |
| Chain of Thought | `conciseReasoning`，只请求可核验的简短步骤 |
| Self-consistency | `selfConsistencySample` + `majorityVote` |
| Tree of Thoughts | `treeOfThoughtsBranches`，应用层负责评估分支 |
| Automatic Prompt Engineering | `automaticPromptCandidates`，候选生成与评测分离 |
| Code prompting | `codePrompt`，使用 `<>` 模板分隔符并明确“不执行代码” |
