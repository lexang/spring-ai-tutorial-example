# 多供应商连接与任务路由

配套博客《多模型接入实战：装配与任务路由》，预约 2026-11-09 09:00（北京时间）。

## 版本与配置

JDK 17+、Maven 3.9+、Spring Boot 4.1.2、Spring AI 2.0.2。spring.ai.model.chat=none，配置类显式创建 OpenAiChatModel 和 AnthropicChatModel，后者通过 MiniMax 兼容地址连接。

OpenAI 模型标为 Primary，使自动 ChatClient.Builder 有明确默认模型。fast/standard/creative/analytical 是同一 OpenAI 模型的四套默认配置；minimax 是独立供应商客户端，不把五个客户端视为五个供应商模型。

~~~powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn spring-boot:run
~~~

无真实凭据可以检查 Bean、模型配置列表和路由计划，不能执行生成。默认共 10 个测试通过，不调用外部模型。

## 预览主路由

~~~http
POST http://localhost:8080/api/routing/plan
Content-Type: application/json

{"task":"ANALYZE"}
~~~

返回 clientId、provider、reason，不访问供应商。Task 为 SHORT_ANSWER/GENERAL/WRITE/ANALYZE，分别选择 fast/standard/creative/minimax。这是明确的示例策略，不是速度、质量或成本最佳选择。

## 真实调用

~~~powershell
$env:OPENAI_API_KEY = "从密钥管理器读取的值"
$env:OPENAI_CHAT_MODEL = "gpt-4o-mini"
$env:MINIMAX_API_KEY = "从密钥管理器读取的值"
$env:MINIMAX_BASE_URL = "https://api.minimax.io/anthropic"
$env:MINIMAX_MODEL = "MiniMax-M3"
mvn spring-boot:run
~~~

停止旧实例后重启。模型名为示例值，默认温度和输出参数应与实际模型匹配；派生客户端不重复硬编码模型名，环境变量配置会继续继承。

~~~http
POST http://localhost:8080/api/routing/chat
Content-Type: application/json

{"task":"SHORT_ANSWER","message":"用一句话解释构造器注入"}
~~~

只调用选中的模型；返回 route、content、responseModel 和已报告 Usage。未知任务/空消息返回中文 400，供应商异常或空响应返回 502。代码没有隐藏的自动 fallback。

## 入口与其他端点

- [MultiModelConfig](./src/main/java/com/example/springai/MultiModelConfig.java)：两个 ChatModel 和五个 ChatClient。
- [TaskRoutingService](./src/main/java/com/example/springai/TaskRoutingService.java)：博客主路由与结果。
- [TaskRoutingController](./src/main/java/com/example/springai/TaskRoutingController.java)：预览/调用 POST 接口。
- [TaskRoutingTest](./src/test/java/com/example/springai/TaskRoutingTest.java)：预览无模型、只请求所选模型、输入/供应商错误。
- [ConfigurerContractTest](./src/test/java/com/example/springai/ConfigurerContractTest.java)：Configurer 与 Registry、Primary 类型边界。
- [requests.http](./requests.http)：完整可复制请求。

/api/chat 支持直接选择五个客户端 ID，/api/models 展示配置的模型名称而不是远程可用性探测。/api/compare 会请求全部客户端，仅做人工观察，不是质量/价格测评。SmartRoutingService 保留关键词启发式作为扩展，不当作真实最优路由。

## 可观测性与限制

手动创建第二个 ChatClient 时，先显式传入 ObservationRegistry/conventions/工具 Builder，再用 ChatClientBuilderConfigurer 应用 customizer；Configurer 不会自动补装 Registry。

仅 Primary ChatClient 不能解决两个 ChatModel 的歧义。连接成功也不等于两个模型支持相同 Schema、工具或媒体能力。跨供应商 fallback、并发、预算、权限和幂等要根据业务设计，示例不提供无条件保证。

本项目不请求真实供应商做性能测试，日志只记录类别/元数据，不输出密钥或完整正文。

## 官方依据

- [v2.0.2 ChatClient 多模型说明](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chatclient.adoc)
- [系列入口](https://bitzw.modelgateai.com/blog/spring-ai-tutorials)
