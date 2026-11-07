# Spring AI 2.0 有效 Agent 工作流示例

本项目把官方页面中的五种模式落成可测试的应用层代码：

- Chain Workflow：顺序执行，上一阶段输出进入下一阶段；
- Parallelization Workflow：独立视角并行执行，再由聚合步骤合并；
- Routing Workflow：受约束的分类结果选择专用处理路径；
- Orchestrator-Workers：先生成结构化子任务，再并行派发；
- Evaluator-Optimizer：生成、评审、改进，直到通过或达到上限。

官方 `effective-agents.adoc` 中的 `ChainWorkflow`、`ParallelizationWorkflow` 等名称是示意类，不是 Spring AI 2.0 发布 JAR 中的通用类。本项目将它们实现为自己的 `AgentWorkflowService`，底层只依赖 Spring AI 的 `ChatClient` 和结构化输出能力。

## 运行条件

- JDK 17 或更高版本
- Maven 3.9+
- 默认不需要 API Key、模型服务或 Docker

## 构建与测试

~~~powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
~~~

默认使用确定性的离线模型，13 个测试覆盖五种工作流、HTTP 参数校验、结构化计划、错误状态和迭代终点。

## 启动离线 HTTP 示例

~~~powershell
mvn spring-boot:run
~~~

服务默认监听 `8091`，请求见 `requests.http`：

- `POST /api/agents/chain`
- `POST /api/agents/parallel`
- `POST /api/agents/route`
- `POST /api/agents/orchestrate`
- `POST /api/agents/evaluate-optimize`

离线模型只用于验证编排边界和返回结构，不代表真实 LLM 的质量。

## 启用真实模型（可选）

~~~powershell
$env:OPENAI_API_KEY = "从外部密钥管理器读取的值"
$env:SPRING_PROFILES_ACTIVE = "live"
mvn spring-boot:run
~~~

还可以设置 OPENAI_BASE_URL、OPENAI_CHAT_MODEL、OPENAI_TIMEOUT 和 OPENAI_MAX_RETRIES。后两者控制单次 SDK 调用，不是整个工作流 deadline；真实模型调用会增加延迟和费用。

## 安全与可靠性边界

- 路由器精确接受 billing、technical、general，未知值返回模型错误，不静默回退；
- Plan 非空、最多八项，单项不超过一千字符；解析成功不等于计划语义正确；
- 每请求最多八个步骤/视角，全局线程池四个工作者、等待队列十六，满队列返回 503；
- Future 取消不保证供应商 HTTP 调用立即停止，已产生费用不能撤销；
- 评审最多四轮，缺少 pass 或 feedback 返回 502。初稿保留；accepted=false、stopReason=ITERATION_LIMIT 表示耗尽次数但未通过；
- 默认绑定 127.0.0.1:8091，不记录提示和回答正文，单次文本输入最多 12000 字符；
- 模型失败为中文 502，非法输入/JSON 为 400，不输出下游详情；
- API Key 只能来自环境变量或外部配置，仓库不包含凭据。
