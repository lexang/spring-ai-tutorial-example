# Spring AI 2.0 VectorStore 中文示例

本项目对应教程 09，使用 Spring AI 2.0.2、Spring Boot 4.1.2、`SimpleVectorStore` 和本地 ONNX `EmbeddingModel` 演示：

- 文档写入与批量写入
- `SearchRequest` 相似度搜索、阈值和 topK
- 文本过滤表达式与 `FilterExpressionBuilder` DSL
- 按 ID 或元数据过滤删除
- 基于检索结果的最小 RAG 问答

## 运行前准备

嵌入模型默认从 Hugging Face 下载。无法联网时，可通过环境变量指定本地文件：

```powershell
$env:EMBEDDING_MODEL_URI="file:D:/models/model.onnx"
$env:EMBEDDING_TOKENIZER_URI="file:D:/models/tokenizer.json"
```

问答端点通过 Anthropic 兼容接口访问 MiniMax，需要设置密钥；密钥只从环境变量读取：

```powershell
$env:MINIMAX_API_KEY="你的密钥"
```

默认 `spring.ai.model.chat=none`，检索不需要 API Key，问答端点不会意外调用聊天模型；有文本上下文但模型未启用时返回 503。要体验旧问答端点，还需设置 `CHAT_MODEL_PROVIDER=anthropic` 以及有效 MINIMAX_API_KEY。默认启动仍需要下载或准备 ONNX 模型。

## 构建与启动

```bash
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn spring-boot:run
```

应用默认监听 `http://localhost:8080`。启动后会写入十条固定 ID 的示例文档；使用 [requests.http](./requests.http) 调用接口。

主要端点：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/documents` | 添加一个文档，元数据只能是简单标量 |
| POST | `/api/documents/batch` | 批量添加（最多 1000 条） |
| GET | `/api/documents/search` | 相似度搜索，默认 topK 为官方 `SearchRequest.DEFAULT_TOP_K`（4） |
| POST | `/api/documents/search` | 博客主线，query/topK/threshold/category/year 放入 JSON 正文，按结构化 DSL 过滤 |
| GET | `/api/documents/search/filter` | 使用文本过滤表达式 |
| GET | `/api/documents/search/dsl` | 使用结构化过滤 DSL |
| DELETE | `/api/documents/{id}` | 按 ID 删除；不存在的 ID 按官方契约忽略 |
| DELETE | `/api/documents/filter` | 按过滤表达式删除 |
| GET | `/api/qa/ask` | 检索后调用聊天模型回答 |
| GET | `/api/qa/ask/category` | 按 `category` 过滤后回答 |
| GET | `/api/documents/stats` | 返回实现名称和持久化边界，不虚构文档计数 |

错误响应统一为 `code`、`message`、`path`、`timestamp` 字段；服务端异常不会把底层连接信息或密钥返回给调用方。

## SimpleVectorStore 边界

Spring AI 官方源码将 `SimpleVectorStore` 定位为测试或演示实现：它用 `ConcurrentHashMap` 保存内容和向量，对全部向量执行线性余弦搜索，并支持元数据过滤、过滤删除以及 `save(File)`/`load(File)` JSON 快照。它没有数据库事务、集群一致性或自动持久化能力，不能直接作为生产数据库。

## 切换 PGvector（可选）

1. 使用 Maven 构建档 `-Ppgvector`，加入真实 `spring-ai-starter-vector-store-pgvector` 依赖。
2. 同时启用 Spring 运行档 pgvector，它会选择 PGvector、恢复 JDBC 自动配置并读取环境变量。
3. 按官方要求安装 `vector`、`hstore` 和 `uuid-ossp` 扩展。
4. 默认不初始化 schema、不删除已有表。专用教学数据库首次建表时设 `PGVECTOR_INITIALIZE_SCHEMA=true`；生产环境应先由迁移流程准备并审核 schema。
5. `dimensions` 必须与实际 `EmbeddingModel` 一致。当前 ONNX 模型为 384 维；更换模型后必须重建向量列和索引。
6. 使用自定义 schema/table 名称时，建议设置 `spring.ai.vectorstore.pgvector.schema-validation=true`。

PGvector 的批量上限、索引类型和距离类型应按模型、数据规模与基准测试选择；本例使用 vector 类型 HNSW，官方参考中的维度限制为 2000，不能拿它泛指其他列类型和所有索引能力。数据库密码只能从外部注入。

```powershell
$env:PGVECTOR_JDBC_URL = "jdbc:postgresql://localhost:5432/spring_ai_tutorial"
$env:PGVECTOR_USERNAME = "postgres"
$env:POSTGRES_PASSWORD = "从密钥管理器读取的值"
$env:PGVECTOR_INITIALIZE_SCHEMA = "true"
$env:EMBEDDING_DIMENSIONS = "384"
mvn -Ppgvector spring-boot:run "-Dspring-boot.run.profiles=pgvector"
```

只在专门准备的教学数据库上使用上面的初始化开关。默认表为 public.tutorial_vector_store，remove-existing-vector-store-table 始终为 false。重启应用可以保留 PGvector 数据；把初始化开关改为 false 不会自动迁移旧表，更换向量维度需要单独设计迁移。

## 本地验证

默认构建共 22 个离线测试；`mvn -Ppgvector clean package "-Dmaven.build.cache.enabled=false"` 共 24 个测试，23 个通过、1 个真实数据库测试因环境未开启而跳过。本轮 Docker 服务不可用，未运行数据库集成测试；PGvector profile 已完成依赖解析、源码编译及不初始化 DDL 的离线契约验证。

若需要运行真实数据库测试，显式设置 `RUN_PGVECTOR_INTEGRATION_TESTS=true` 和 `PGVECTOR_TEST_JDBC_URL`、`PGVECTOR_TEST_USERNAME`、`PGVECTOR_TEST_PASSWORD`，这些必须指向专用测试数据库。测试使用固定三维模型，在随机 pgvector_it_ 表验证写入、同 ID 更新、重开读取、过滤及删除，最后只清理该随机测试表；它不验证真实 ONNX 模型质量。

测试与演示接口没有认证系统。分类过滤不是身份权限，不要将任意过滤删除接口公开暴露；删除演示应先保存明确的文档 ID，只删除自己新增的教学数据。

博客预约：2026-11-13 09:00 +08:00，slug 为 spring-ai-vectorstore-pgvector；RAG 的来源回答与生成链路留到后续教程。
