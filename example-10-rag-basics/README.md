# Spring AI 2.0 RAG 示例

本项目与教程 10 上、下篇配套，演示 Spring AI 2.0 的两类 RAG 入口：

- `QuestionAnswerAdvisor`：适合快速实现基于向量库的问答。
- `RetrievalAugmentationAdvisor`：按查询转换、扩展、检索、合并、后处理和上下文增强组成模块化 RAG。

项目基线：

- Java 17+
- Spring Boot 4.1.2
- Spring AI 2.0.2
- MiniMax Anthropic 兼容接口
- Transformers ONNX Embedding + `SimpleVectorStore`

## 依赖依据

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store-advisor</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-rag</artifactId>
</dependency>
```

Spring AI 2.0 将旧的 `spring-ai-advisors-vector-store` 更名为
`spring-ai-vector-store-advisor`。MiniMax 专用支持也已移除，本项目按升级说明使用
`spring-ai-starter-model-anthropic` 和兼容地址。

## 启动前准备

### 1. 配置模型凭据

PowerShell：

```powershell
$env:MINIMAX_API_KEY="你的 API Key"
```

Bash：

```bash
export MINIMAX_API_KEY="你的 API Key"
```

可选配置：

```text
MINIMAX_BASE_URL=https://api.minimax.io/anthropic
MINIMAX_MODEL=MiniMax-M3
SERVER_PORT=8080
```

API Key 不得写入 `application.properties` 或提交到仓库。

### 2. 准备 ONNX 嵌入模型

默认配置从 HuggingFace 下载 `all-MiniLM-L6-v2`：

```properties
spring.ai.embedding.transformer.tokenizer.uri=${EMBEDDING_TOKENIZER_URI:https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/raw/main/tokenizer.json}
spring.ai.embedding.transformer.onnx.model-uri=${EMBEDDING_MODEL_URI:https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx}
```

当前网络无法访问 HuggingFace 时，先下载文件，再传入 `file:` URI：

```powershell
$env:EMBEDDING_TOKENIZER_URI="file:D:/models/all-MiniLM-L6-v2/tokenizer.json"
$env:EMBEDDING_MODEL_URI="file:D:/models/all-MiniLM-L6-v2/model.onnx"
$env:EMBEDDING_CACHE_DIRECTORY="D:/models/spring-ai-cache"
```

路径仅是本机示例，不会写入仓库。默认模型更偏向英文语料；中文生产场景应切换为经验证的多语言或中文嵌入模型，并重新生成全部文档向量。

## 运行

```bash
mvn spring-boot:run
```

启动时默认写入 7 个演示文档。需要关闭自动初始化时设置：

```text
RAG_INITIALIZE_SAMPLE_DATA=false
```

`SimpleVectorStore` 只适合本地教学，应用重启后数据会丢失。生产环境应改用持久化向量库，并通过独立 ETL 流程导入文档。

## HTTP 接口

项目根目录提供 [`requests.http`](./requests.http)，可在 IntelliJ IDEA 中直接执行。

### Part 1：基础 RAG

| 接口 | 说明 |
|---|---|
| `GET /api/rag/part1/simple` | 默认 `QuestionAnswerAdvisor` |
| `GET /api/rag/part1/filtered` | 使用结构化产品/类别过滤 |
| `GET /api/rag/part1/custom` | 自定义 `PromptTemplate` |
| `GET /api/rag/part1/modular` | 默认严格空上下文策略 |
| `GET /api/rag/part1/modular/filtered` | 模块化 RAG 请求级过滤 |
| `GET /api/rag/part1/modular/allow-empty` | 允许模型在无检索结果时继续回答 |

示例：

```bash
curl --get "http://localhost:8080/api/rag/part1/filtered" \
  --data-urlencode "question=Spring AI 的 RAG 有什么作用？" \
  --data-urlencode "product=Spring AI" \
  --data-urlencode "category=RAG"
```

公网参数不会直接拼接成过滤 DSL。项目使用 `FilterExpressionBuilder` 生成
`Filter.Expression`，避免把用户输入解释为操作符或元数据字段。

### Part 2：高级 RAG

| 接口 | 说明 |
|---|---|
| `GET /api/rag/part2/rewrite` | 查询重写后检索 |
| `GET /api/rag/part2/translate` | 翻译为英语检索 |
| `GET /api/rag/part2/expand` | 多查询扩展与合并 |
| `GET /api/rag/part2/advanced` | 重写、扩展、检索、后处理、增强 |

查询转换和扩展使用温度 `0.0` 的独立 `ChatClient.Builder`。响应生成仍使用模型的正常默认配置。

`MultiQueryExpander.numberOfQueries(3)` 表示让模型生成 3 个变体；默认再加入原始查询，因此通常得到 4 个查询。若模型没有严格返回指定数量的行，Spring AI 会回退为原始查询。

模型输出可能包含重复变体；项目通过 `DistinctQueryExpander` 在进入官方 Advisor 前保序去重，避免当前 `RetrievalAugmentationAdvisor` 结果映射的重复键异常。

### 单独观察 RAG 模块

| 接口 | 说明 |
|---|---|
| `GET /api/rag/modules/rewrite` | 返回重写前后文本 |
| `GET /api/rag/modules/translate` | 返回翻译前后文本 |
| `GET /api/rag/modules/expand` | 返回扩展查询列表 |
| `GET /api/rag/modules/compress` | 将一轮历史和追问压缩为独立查询 |

## 重要行为

### 空上下文

`RetrievalAugmentationAdvisor` 默认使用严格策略。检索结果为空时，
`ContextualQueryAugmenter` 会把请求改写为“知识库范围外，不能回答”的提示。

设置 `allowEmptyContext(true)` 后，空结果时会保留原始查询，模型可能使用自身知识回答。因此该选项会降低“只依据知识库”的约束，不能作为默认安全策略。

### 过滤优先级

`VectorStoreDocumentRetriever` 支持：

1. Builder 中的静态 `Filter.Expression`。
2. Builder 中的 `Supplier<Filter.Expression>` 动态过滤。
3. `Query.context` 或 Advisor 参数中的请求级过滤。

请求级过滤优先于 Retriever 自身配置。本项目直接传递结构化 `Filter.Expression`，而不是接收任意 DSL 字符串。

### 文档合并

`ConcatenationDocumentJoiner` 按文档 ID 去重，重复项保留第一次出现的文档及其原分数，最终结果按分数降序排列。完整流水线随后使用自定义 `TopDocumentPostProcessor` 限制上下文文档数。

## 测试与打包

离线测试不调用模型，也不下载 ONNX 文件：

```bash
mvn test
mvn clean package
```

测试覆盖（当前共 32 个测试，31 个离线测试通过，1 个真实模型/ONNX 上下文测试按条件跳过）：

- 结构化过滤值不会被解释成过滤 DSL。
- Retriever 默认过滤与请求级覆盖规则。
- 文档 ID 去重、第一次出现保留、分数保留和降序排列。
- 自定义后处理器的数量限制。
- 严格/允许空上下文行为。
- `QuestionAnswerAdvisor` 和 `RetrievalAugmentationAdvisor` 的文档 context/metadata 契约。
- `MultiQueryExpander` 重复 Query 的保序去重和空结果回退。
- `ChatClient.content()` 空响应的中文 502 错误结构。

完整 Spring 上下文测试默认跳过。准备好 API Key、ONNX 模型和网络后再显式开启：

```powershell
$env:RUN_RAG_INTEGRATION_TESTS="true"
mvn -Dtest=RagBasicsApplicationContextTests test
```

## 项目结构

```text
src/main/java/com/example/springai/
├── config/                 # VectorStore、示例数据和 ChatClient Bean
├── part1/                  # 基础 RAG、过滤和模块化 RAG
├── part2/                  # 高级 RAG HTTP 接口
├── ragmodules/             # 转换、扩展、合并和后处理模块
└── support/                # 输入校验、Builder 隔离和异常响应
```

更多原理和代码说明见：

- [v2.0.2 官方 RAG 文档](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/retrieval-augmented-generation.adoc)
- [示例完整源码](./src/main/java/com/example/springai/)

## 博客主线：回答与检索来源

两篇预约稿分别为 2026-11-14、11-15 09:00 +08:00，slug 为 spring-ai-rag-grounded-answers 与 spring-ai-rag-pipeline。

基础接口：

```http
POST http://localhost:8080/api/rag/ask
Content-Type: application/json

{"question":"Spring AI 的 RAG 有什么作用？","product":"Spring AI","category":"RAG"}
```

进阶接口：

```http
POST http://localhost:8080/api/rag/part2/advanced
Content-Type: application/json

{"question":"我想让模型回答项目文档里的问题，应该怎样组织查询和检索？"}
```

两者返回 status、answer、retrievedSources、answerUsage。来源取自同一次 ChatClientResponse：基础用 QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS，进阶用后处理后的 RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT。不是事后重新搜索出来的资料，也不是已经核验的逐句引用；excerpt 只展示前 300 字符，并不代表模型只收到这部分内容。

空来源时应用返回 NO_CONTEXT，但 Advisor 之前仍可能已经调用聊天模型。框架的 allowEmptyContext(false) 是提示约束，不是禁止出站调用或事实保证。资料非空时仍可能不相关，不能把有来源列表等同于回答已正确。answerUsage 只标识回答生成调用的 Usage，不包含另外的改写/扩展请求，不用于宣称完整流水线费用。

演示文档 source 已统一标成 tutorial-demo，不能把这些教学摘要当作逐篇原文或正式引用。输入错误/非法 JSON 返回 400；新主线模型或检索失败返回 502，日志与响应不包含下游原文。当前未验证真实模型与 ONNX 效果；分类/产品过滤仍不是权限认证。
