# Spring AI 2.0 智能文档问答示例

本项目演示一条可离线运行和测试的文档问答链路：HTTP 文本请求经过 `DocumentReader` 读取、`TokenTextSplitter` 分块、`VectorStore` 写入和检索，再由 `QuestionAnswerAdvisor` 把检索上下文加入模型请求。回答会同时返回检索来源，便于核查答案依据。

完整请求与代码在本项目目录，博客正文按文档生命周期组织，不重复基础 API 清单。

## 版本与依赖

- Java 17+
- Spring Boot 4.1.2
- Spring AI 2.0.2 BOM
- `spring-ai-client-chat`
- `spring-ai-vector-store-advisor`
- `spring-ai-vector-store`
- `spring-ai-starter-model-openai`，仅在 `live` profile 中实际使用

默认配置使用确定性的离线 `ChatModel` 和 128 维 Unicode n-gram 哈希 `EmbeddingModel`。它们只用于演示链路和稳定测试，不代表真实模型的语义理解或回答质量。`SimpleVectorStore` 也仅适合测试与演示，进程重启后数据会丢失。

## 构建与启动

默认离线模式不需要 API Key：

```powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn spring-boot:run
```

启动后可执行 [requests.http](./requests.http) 中的请求。主要端点如下：

| 方法 | 路径 | 作用 |
|---|---|---|
| `GET` | `/api/knowledge/status` | 查看来源数、分块数和当前模型 |
| `POST` | `/api/knowledge/documents` | 写入或替换同一 `source` 的文档 |
| `POST` | `/api/knowledge/ask` | RAG 问答并返回检索来源 |
| `DELETE` | `/api/knowledge/documents?source=...` | 删除指定来源的全部分块 |

同一 `source` 再次写入会使用稳定的分块 ID 替换内容，并删除新版本不再包含的旧分块。`category` 只允许 Unicode 文字、数字、下划线和连字符，避免把任意用户输入直接拼入过滤表达式。

## 切换到真实模型

`live` profile 使用 OpenAI 的 `ChatModel` 与 `EmbeddingModel`。密钥必须由环境变量或外部密钥管理器注入：

```powershell
$env:OPENAI_API_KEY = "从外部密钥管理器读取的值"
$env:SPRING_PROFILES_ACTIVE = "live"
mvn spring-boot:run
```

模型名等供应商配置可继续通过 Spring Boot 外部配置覆盖。仓库不保存任何真实凭据。

本地离线测试不覆盖真实 OpenAI 请求；真实调用仍取决于有效凭据、模型权限、额度和网络。切换到生产环境时还应把 `SimpleVectorStore` 替换为 PGvector 等持久化实现，并补充租户权限、文件解析与病毒扫描、文档版本事务、评估、观测、限流和配额控制。

## 测试范围

项目包含 12 个离线测试，覆盖：

- Spring 上下文和默认离线 Bean；
- `DocumentReader`、Splitter 元数据、稳定分块 ID 与替换/删除；
- `QuestionAnswerAdvisor` 检索上下文、分类过滤、来源回传和无结果回答；
- 输入校验、模型空响应与底层失败的中文 `400`/`502` 映射。

返回 sources 是本次 Advisor 检索候选，包含 chunkId，不等同于模型逐句引用。status=NO_CONTEXT 时应用覆盖为明确的资料不足回答，ANSWERED 只表示有候选，不证明答案真实；QuestionAnswerAdvisor 本身仍调用了模型。usage 缺失保留 reported=false/null。

删除向量失败时保留分块跟踪，以便重试。写入新版本再清理旧块不是数据库事务，生产需要版本事务或可恢复策略；本地读写锁不跨进程。服务默认绑定 127.0.0.1:8080。

运行：

```powershell
mvn test "-Dmaven.build.cache.enabled=false"
```
