# ChatClient 请求与响应示例

本项目配套博客《ChatClient 实战：请求配置、响应与 SSE》。主线从一个 POST 请求体出发，选择默认客户端、传入请求级 Options，再读取同步或流式完整响应。

## 环境和版本

JDK 17+、Maven 3.9+；Spring Boot 4.1.2、Spring AI 2.0.2。真实生成需要 OpenAI 服务的凭据与模型权限。

## 先测试，再启动

~~~powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
$env:OPENAI_API_KEY = "从密钥管理器读取的值"
$env:OPENAI_CHAT_MODEL = "gpt-4o-mini"
$env:OPENAI_TIMEOUT = "30s"
$env:OPENAI_MAX_RETRIES = "2"
$env:OPENAI_STREAM_USAGE = "true"
mvn spring-boot:run
~~~

模型名是示例值；maxTokens、温度等参数必须与实际模型匹配。本篇主接口使用普通 OpenAI 聊天模型的 maxTokens，推理模型上限见扩展端点。

## 主线代码与接口

[ChatRequestController](./src/main/java/com/example/springai/ChatRequestController.java) 是文章主线。请求字段为 message、style（TECHNICAL/CONCISE）、maxTokens 和可选 temperature。

~~~http
POST http://localhost:8080/api/chat
Content-Type: application/json

{"message":"解释构造器注入为什么方便测试","style":"TECHNICAL","maxTokens":300,"temperature":0.2}
~~~

同一请求体 POST 到 /api/chat/stream，Accept 选择 text/event-stream，返回逐块 ChatReply。每块都有 content、model、responseId、finishReason、usage 和应用 requestId，允许只有元数据而没有正文的块。

- 默认 style 是 TECHNICAL，默认 maxTokens 是 500；输入长度 8000、输出参数 4096 是本例的应用规则，不代表模型上下文窗口。
- 请求级 Options 传入 Builder，未设置的温度不做请求覆盖。
- usage.reported=false 表示没有供应商用量，Token 字段保持 null。
- 流式每次订阅创建新的请求链；不要将每块的累计 Usage 直接相加。
- API Key 从环境变量读取，日志不输出用户完整正文或供应商异常详情。

更多请求见 [requests.http](./requests.http)。

## 完整示例的其他入口

为了后续主题复用，本项目还保留已有扩展示例：

- ChatClientController：GET 基础/完整响应、多个默认客户端、Options、mutate、entity 和 metadata。
- ClientExamplesController：模板、泛型列表、聚合后转换、问答、摘要、日期工具及 Memory。
- ConversationService：应用管理的有限历史、失败不提交和 reset 竞争。
- MemoryConversationService：框架 Memory Advisor 示例，与应用历史的持久化/失败语义不同。
- BatchChatService：有界线程池、队列和期限，用于独立请求的并发演示。
- metadata 目录：Message metadata 与 Advisor context 的传播和边界。
- OpenAiTransportConfig：可选固定请求头的 SDK/OkHttp 定制。

这些扩展不是博客本篇全部要求；先通过 POST 主线理解一次调用，再进入相应后续主题。

## 验证与边界

默认 clean package 共 37 个测试，全部通过。不请求真实模型，OpenAiLocalTransportTest 只使用本机 HTTP 服务验证真实 SDK 同步/SSE 传输。

新增 [ChatRequestControllerTest](./src/test/java/com/example/springai/ChatRequestControllerTest.java) 验证默认隔离、请求 Options、未知用量、重新订阅、usage-only 块与中文 HTTP 错误。

MVC 承载服务、OpenAI 官方 SDK 负责模型传输；不用 spring.http.client.factory 控制 SDK，也没有额外 WebFlux starter。SSE 发送后报错会中断响应，不能保证改为 JSON 502。

同一个客户端不等于跨请求自动记忆；终止方法也不是结果缓存。真实模型质量、额度、公网流式体验仍需有效环境验证。

## 参考

- [博客文章](https://bitzw.modelgateai.com/blog/spring-ai-chatclient-requests)
- [官方 v2.0.2 ChatClient](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chatclient.adoc)

