# Spring AI 2.0 Chat Memory 示例

本项目与 [教程 12：对话记忆](../../doc/12-chat-memory.md) 配套，演示 Spring AI 2.0 的 `ChatMemory`、`ChatMemoryRepository`、`MessageChatMemoryAdvisor` 和 `VectorStoreChatMemoryAdvisor`。

示例默认可以完全离线启动：不配置 API Key、不下载 ONNX 模型，也能操作和测试 InMemory、JDBC/H2 两种窗口记忆。真实模型聊天和向量语义记忆必须通过环境变量显式启用。

## 版本与官方依据

- Java 17+
- Spring Boot 4.1.2
- Spring AI 2.0.2
- 官方页面：`pages/api/chat-memory.adoc`
- 升级说明：`pages/upgrade-notes.adoc`
- 本地源码：`ChatMemory`、`ChatMemoryRepository`、`MessageWindowChatMemory`、两个 Memory Advisor 及 JDBC Repository

依赖使用 Spring AI 2.0 的正式模块名：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store-advisor</artifactId>
</dependency>
```

项目没有 milestone 仓库、Windows 专用 PyTorch 依赖，也没有写死本机模型路径。

## 三种运行模式

| 模式 | 默认状态 | 能力 | 外部条件 |
|---|---|---|---|
| 离线窗口记忆 | 启用 | InMemory、JDBC/H2 的写入、查询、清除与窗口淘汰 | 无 |
| Message Advisor 对话 | 关闭 | `MessageChatMemoryAdvisor` 多轮聊天 | ChatModel、API Key |
| 向量语义记忆 | 关闭 | `VectorStoreChatMemoryAdvisor` + `SimpleVectorStore` | ChatModel、EmbeddingModel、首次下载 ONNX |

## 项目结构

```text
example-12-chat-memory/
├── pom.xml
├── README.md
├── requests.http
└── src/
    ├── main/java/com/example/springai/
    │   ├── ChatMemoryApplication.java
    │   ├── config/ChatMemoryConfig.java
    │   ├── controller/
    │   │   ├── MemoryController.java
    │   │   ├── ChatController.java
    │   │   └── VectorMemoryController.java
    │   ├── service/
    │   │   ├── MemoryManagementService.java
    │   │   ├── ChatConversationService.java
    │   │   ├── VectorMemoryChatService.java
    │   │   └── ManualChatMemoryService.java
    │   └── support/
    │       ├── RequestRules.java
    │       └── ChatMemoryExceptionHandler.java
    ├── main/resources/application.properties
    └── test/java/com/example/springai/
        ├── InMemoryChatMemoryRepositoryTest.java
        ├── MessageWindowChatMemoryTest.java
        ├── MessageChatMemoryAdvisorTest.java
        ├── VectorStoreChatMemoryAdvisorTest.java
        ├── JdbcChatMemoryRepositoryTest.java
        ├── ManualChatMemoryServiceTest.java
        ├── ChatMemoryApplicationTests.java
        └── ConfiguredModelsIntegrationTest.java
```

## 快速开始：默认离线模式

### 1. 构建并测试

```bash
mvn clean package
```

默认构建不会调用真实模型，也不会加载 ONNX。测试覆盖：

- 完整 turn 边界淘汰；
- SystemMessage 保留、替换及超过窗口上限的边界；
- InMemory `saveAll()` 的列表浅快照与查询副本行为；
- 会话隔离与清除；
- Memory Advisor 强制要求 conversation ID；
- Advisor Builder 的 `.order(...)` 发布 API；
- JDBC `sequence_id` 顺序；
- JDBC 创建时间元数据；
- `saveAll()` 替换整段会话；
- JDBC 工具调用消息过滤；
- 向量记忆 XML 转义、类型包装及模型失败后的单边写入；
- 无模型时应用正常启动。

当前独立构建共 20 个测试：19 个离线测试通过，1 个真实模型/ONNX 集成测试按环境变量条件跳过。新增测试检查空模型文本不能成为成功响应，以及不同会话 ID 不带入其他会话消息。

### 2. 启动

博客预约 2026-11-17 09:00 +08:00，slug 为 spring-ai-chat-memory。重点是窗口策略、持久化与会话范围，不宣称本例实现了认证或永久历史归档。

```bash
mvn spring-boot:run
```

或者：

```bash
java -jar target/spring-ai-chat-memory-1.0.0.jar
```

### 3. 查看可选功能状态

注意：MessageWindowChatMemory 会把裁剪后的窗口写回 Repository，文件型 H2 并不会因此保存全部历史。模型调用失败或空响应被应用拒绝时，Advisor 可能已写入用户消息；本例没有自动回滚或同会话并发事务保证。生产系统要另外处理请求幂等、顺序及失败策略。

```bash
curl http://localhost:8080/api/chat/status
curl http://localhost:8080/api/vector-memory/status
```

默认会返回 `enabled: false`，并给出中文启用提示。这不是启动错误。

### 4. 离线操作 InMemory 记忆

```bash
curl -X POST http://localhost:8080/api/memory/in-memory/demo-memory/messages \
  -H "Content-Type: application/json" \
  -d '{"role":"user","text":"我叫张三"}'

curl http://localhost:8080/api/memory/in-memory/demo-memory

curl -X DELETE http://localhost:8080/api/memory/in-memory/demo-memory
```

`role` 只支持 `system`、`user`、`assistant`。

### 5. 离线操作 JDBC/H2 记忆

```bash
curl -X POST http://localhost:8080/api/memory/jdbc/demo-jdbc/messages \
  -H "Content-Type: application/json" \
  -d '{"role":"assistant","text":"这条消息会写入 H2"}'

curl http://localhost:8080/api/memory/jdbc/demo-jdbc
```

JDBC 响应中的 `createdAt` 来自：

```java
JdbcChatMemoryRepository.CONVERSATION_TS
```

其值是 `java.time.Instant`。InMemory 消息没有这个持久化时间戳，因此 `createdAt` 为 `null`。

## 启用真实 ChatModel

本示例使用 Spring AI 2.0 的 Anthropic Starter，通过 Anthropic 兼容接口调用 MiniMax。凭据只从环境变量读取。

PowerShell：

```powershell
$env:SPRING_AI_CHAT_MODEL = "anthropic"
$env:MINIMAX_API_KEY = "你的 API Key"
$env:MINIMAX_BASE_URL = "https://api.minimax.io/anthropic"
$env:MINIMAX_MODEL = "MiniMax-M3"
mvn spring-boot:run
```

Linux/macOS：

```bash
export SPRING_AI_CHAT_MODEL=anthropic
export MINIMAX_API_KEY='你的 API Key'
export MINIMAX_BASE_URL='https://api.minimax.io/anthropic'
export MINIMAX_MODEL='MiniMax-M3'
mvn spring-boot:run
```

调用 Message Advisor 对话：

```bash
curl -X POST http://localhost:8080/api/chat/jdbc/demo-chat \
  -H "Content-Type: application/json" \
  -d '{"message":"我叫李四，请记住"}'

curl -X POST http://localhost:8080/api/chat/jdbc/demo-chat \
  -H "Content-Type: application/json" \
  -d '{"message":"我叫什么名字？"}'
```

每次调用都会显式传递：

```java
.advisors(advisor ->
    advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
```

Spring AI 2.0 不再提供默认 conversation ID。缺失该参数会立即抛出 `IllegalArgumentException`。

## 启用向量语义记忆

向量模式同时需要 ChatModel 和 Transformers EmbeddingModel：

PowerShell：

```powershell
$env:SPRING_AI_CHAT_MODEL = "anthropic"
$env:MINIMAX_API_KEY = "你的 API Key"
$env:SPRING_AI_EMBEDDING_MODEL = "transformers"
mvn spring-boot:run
```

Linux/macOS：

```bash
export SPRING_AI_CHAT_MODEL=anthropic
export MINIMAX_API_KEY='你的 API Key'
export SPRING_AI_EMBEDDING_MODEL=transformers
mvn spring-boot:run
```

首次启用时会下载 Spring AI 默认的 `all-MiniLM-L6-v2` tokenizer 和 ONNX 模型，并缓存到临时目录。也可以提供本地或其他远程 URI：

```powershell
$env:EMBEDDING_TOKENIZER_URI = "file:D:/models/all-MiniLM-L6-v2/tokenizer.json"
$env:EMBEDDING_MODEL_URI = "file:D:/models/all-MiniLM-L6-v2/model.onnx"
$env:EMBEDDING_CACHE_DIRECTORY = "D:/models/spring-ai-cache"
```

不要把个人绝对路径提交到仓库。

调用向量记忆：

```bash
curl -X POST http://localhost:8080/api/vector-memory/vector-demo \
  -H "Content-Type: application/json" \
  -d '{"message":"我最喜欢 Java 和 Spring AI","topK":5}'

curl -X POST http://localhost:8080/api/vector-memory/vector-demo \
  -H "Content-Type: application/json" \
  -d '{"message":"我喜欢哪些技术？","topK":3}'

curl -X DELETE http://localhost:8080/api/vector-memory/vector-demo
```

删除操作使用结构化 `Filter.Expression`：

```java
var filter = new FilterExpressionBuilder()
    .eq("conversationId", conversationId)
    .build();
vectorStore.delete(filter);
```

不会把用户输入直接拼成过滤字符串。

## API 一览

| 方法 | 路径 | 是否离线可用 | 说明 |
|---|---|---:|---|
| `GET` | `/api/chat/status` | 是 | ChatModel 状态 |
| `GET` | `/api/vector-memory/status` | 是 | ChatModel、EmbeddingModel 与向量功能状态 |
| `POST` | `/api/memory/{storage}/{conversationId}/messages` | 是 | 手动添加消息 |
| `GET` | `/api/memory/{storage}/{conversationId}` | 是 | 查看当前窗口 |
| `DELETE` | `/api/memory/{storage}/{conversationId}` | 是 | 清除当前窗口 |
| `POST` | `/api/chat/{storage}/{conversationId}` | 否 | Message Advisor 多轮对话 |
| `POST` | `/api/vector-memory/{conversationId}` | 否 | 向量语义记忆对话 |
| `DELETE` | `/api/vector-memory/{conversationId}` | 否 | 删除指定会话的向量文档 |

`storage` 支持 `in-memory` 或 `jdbc`。为兼容官方 H2 Schema 的 `VARCHAR(36)`，示例将 conversation ID 限制为 1 到 36 位，只允许英文字母、数字、点、下划线、冒号和连字符。

## JDBC 持久化说明

默认配置：

```properties
spring.datasource.url=jdbc:h2:file:./data/chat-memory;DB_CLOSE_ON_EXIT=FALSE
spring.ai.chat.memory.repository.jdbc.initialize-schema=embedded
```

数据库文件位于运行目录的 `data/` 下，所以应用重启后 JDBC 记忆仍存在。InMemory 记忆会丢失。

Spring AI 2.0 的 JDBC 表使用：

- `sequence_id`：决定同一会话内的稳定消息顺序；
- `timestamp`：记录消息创建时间；
- `conversation_id`：H2 官方 Schema 中长度为 36。

生产环境切换 PostgreSQL、MySQL/MariaDB、SQL Server、HSQLDB 或 Oracle 时，需要自行添加对应 JDBC Driver。非嵌入式数据库建议使用 Flyway/Liquibase 管理 Schema，并设置：

```properties
spring.ai.chat.memory.repository.jdbc.initialize-schema=never
```

从 Spring AI 1.x 升级的旧表必须增加 `sequence_id`，不能只改依赖版本。

## 2.0 行为边界

### Chat Memory 不等于完整历史

`MessageWindowChatMemory` 会淘汰旧 turn，因此它只适合作为模型上下文。审计、客服质检、计费和数据分析应另建完整 Chat History 存储。

### 窗口按完整 turn 淘汰

`maxMessages` 是上限，不保证实际总能保存这么多条。如果原始切点落在 assistant 或 tool 消息中间，2.0 会继续前移到下一个 `UserMessage`，避免保留孤立的半个 turn。

### SystemMessage 的处理

- 淘汰普通消息时保留 SystemMessage；
- 新增一个不同的 SystemMessage 会替换此前的 SystemMessage；
- 相同 SystemMessage 可重复保留，批量加入的多个 SystemMessage 也不会被数量淘汰，因此实际总数可能超过 `maxMessages`；
- 窗口大小应覆盖一个有代表性的完整 turn，不能把 `maxMessages` 当作严格容量上限。

### InMemory List 引用边界

2.0.2 的 `InMemoryChatMemoryRepository.saveAll()` 使用 `List.copyOf(messages)` 保存浅快照；修改原始 List 或清空查询返回的副本不会改变仓库列表。Message 内部可变元数据仍不是深复制。回归测试已按这一新契约更新，历史 2.0.0 的 List 引用限制不再适用于当前基线。

### JDBC 工具消息限制

`JdbcChatMemoryRepository` 会静默过滤：

- 含 tool call 的 `AssistantMessage`；
- `ToolResponseMessage`。

2.0.1 默认由 `ToolCallingAdvisor` 管理工具循环内部历史，Memory Advisor 只保存最终用户/助手交换。若必须完整持久化工具消息，应采用用户控制的工具执行或专门的会话存储方案。

### 向量记忆不是多模态记忆

`VectorStoreChatMemoryAdvisor` 只保存 USER/ASSISTANT 文本，将检索结果作为纯文本追加到 SystemMessage。它不适合多模态记忆，也不能把检索文本视为可信指令。

## 发布 API 核对说明

当前官方 Chat Memory 页面在自定义向量记忆模板的文字中写的是 `.promptTemplate(...)`，但 Spring AI 2.0.0 发布 JAR 和本地 2.0.x 源码实际提供：

```java
VectorStoreChatMemoryAdvisor.builder(vectorStore)
    .systemPromptTemplate(promptTemplate)
    .build();
```

本项目以发布 JAR 可编译 API 为准。模板必须同时包含 `{instructions}` 和 `{long_term_memory}`。

2.0.1 升级说明中的 Memory Advisor 顺序示例还写了 `.advisorOrder(...)`，但两个 Memory Advisor 的发布 Builder 实际方法是 `.order(...)`。项目测试会编译并校验这个方法。

2.0.0 正式 JAR 已对检索文本做 XML 转义，并使用 `<memory-entry type="...">` 包装角色；这不是仅存在于后续 Snapshot 的能力。不过向量 Advisor 会先写 UserMessage 再调用模型，模型失败时仍可能留下没有 AssistantMessage 的单边记录。

## 真实模型集成测试

默认测试类 `ConfiguredModelsIntegrationTest` 被 JUnit 正确标记为 skipped。只有显式启用时才执行真实模型调用并加载 ONNX：

PowerShell：

```powershell
$env:RUN_CHAT_MEMORY_INTEGRATION_TESTS = "true"
$env:SPRING_AI_CHAT_MODEL = "anthropic"
$env:MINIMAX_API_KEY = "你的 API Key"
$env:SPRING_AI_EMBEDDING_MODEL = "transformers"
mvn test
```

## 安全提示

- API Key、数据库密码只能通过环境变量或外部配置注入；
- H2 Console 默认关闭；
- conversation ID 和消息长度均有边界校验；
- 服务日志只记录会话 ID 和消息长度，不打印完整用户消息；
- `VectorStoreChatMemoryAdvisor` 的 XML 转义和 `<memory-entry>` 包装只是约定层防护，有工具权限的 Agent 更适合保留消息类型的架构。

更多请求示例见 [requests.http](./requests.http)。
