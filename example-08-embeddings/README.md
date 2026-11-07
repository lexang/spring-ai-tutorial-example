# Spring AI 2.0 Embeddings 示例

本项目配套“文本向量化与批量结果校验”博客，使用 Spring AI 2.0.2、Spring Boot 4.1.2、Java 17 编译目标和本地 ONNX Transformers 模型。主线是检查实际向量和相似度，不进行模型效果排名；旧内存搜索端点保留作扩展。

## 环境与配置

- Java 17 或更高版本
- Maven 3.9 或更高版本
- 默认无需 API Key

默认从 HuggingFace 下载 `all-MiniLM-L6-v2`，官方示例对应 384 维；实际结果仍以向量长度为准。首次模型资源加载需要网络，并不因“本地推理”自动成为离线可启动项目。可通过环境变量使用预先准备的 ONNX 模型和配套 tokenizer：

```powershell
$env:EMBEDDING_MODEL_URI="file:D:/models/model.onnx"
$env:EMBEDDING_TOKENIZER_URI="file:D:/models/tokenizer.json"
```

也支持 `classpath:` 和 `https:` URI。远程资源默认缓存到系统临时目录，可用 `EMBEDDING_CACHE_DIRECTORY` 修改。

## 运行

```bash
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn spring-boot:run
```

应用监听 `http://localhost:8080`。启动时会批量向量化 10 篇示例文档，因此首次加载模型可能稍慢。使用 [requests.http](./requests.http) 可测试全部端点。

当前 clean package 共 22 个测试：21 个离线测试通过，1 个真实 ONNX 上下文测试按环境条件跳过。新增测试检查批量索引、数量、维度、空向量、NaN、用量缺失与 HTTP 错误边界，以及 float 中间乘法溢出。要加载真实 ONNX 模型，先设置 `RUN_ONNX_INTEGRATION_TESTS=true`；该测试要求模型和 tokenizer 资源可用。没有执行的模型测试不算通过，也不代表中文检索效果已验证。

## 博客主线

```http
POST http://localhost:8080/api/embedding/analyze
Content-Type: application/json

{"texts":["Java 程序设计","Java 企业应用开发"]}
```

请求接受 1–50 条文本，每条非空且最多 2000 个字符。这个上限是应用限制，不是模型 Token 上限。响应包含 dimensions、按输入索引排列的 vectors；恰好两条输入时还计算 cosineSimilarity。模型返回的每个向量都会检查数量、索引唯一性、维度和有限元素，不能只看第一个向量。

本地 ONNX 的 embedForResponse 没有供应商计费 Usage，promptTokens/totalTokens 保持 null。这个接口没有自动分块长文本，也不保证任意 50 条文本都适合一次 ONNX 推理；根据模型 tokenizer、内存和长度限制缩小批次。已开启 padding，替换模型时还需核对输出节点名和 tokenizer 行为。

输入错误返回 400，模型异常或非法向量返回 502 且不泄漏异常原文。新主线不记录文本正文。余弦分数范围是 [-1,1]，不是百分比或回答正确率；分数为 1 也不证明原始文本相同。

主要接口：

- `GET /api/embed`：生成单个文本向量
- `POST /api/embed/batch`：批量生成向量
- `GET /api/similarity`：计算余弦相似度、欧氏距离和点积
- `GET /api/search`：搜索内存文档库
- `/api/documents`：添加、批量添加和清空文档
- `POST /api/documents/similar`：查找与给定文本相似的文档
- `GET /api/documents/stats`：查看文档数、实际向量维度和模型类型
- 所有参数错误返回 HTTP 400 和中文 JSON；模型或下游异常返回不泄漏内部细节的 HTTP 500

## 重要边界

- 向量维度由模型决定，不能硬编码；切换模型后应重建已有向量索引。
- 不同模型生成的向量不应直接比较。
- 本项目的内存文档库仅用于教学：使用 `CopyOnWriteArrayList` 提供基本并发读写安全，但不提供持久化、元数据过滤、事务和跨进程一致性；生产环境应使用向量数据库。
- `EmbeddingModel.dimensions()` 的默认实现可能通过实际嵌入调用获取维度。示例统计接口优先读取索引中首个真实向量，索引为空时才回退到 `dimensions()`，因此仍可能产生一次模型调用。
- `topK` 必须是大于 0 的整数；批量接口拒绝空列表、空字符串和 null 元素。
- 切换模型、输出维度、MetadataMode 或文本预处理后，旧向量不可直接复用，必须清空并重建索引。

博客预约：2026-11-12 09:00 +08:00，slug 为 spring-ai-embeddings。下一步：[示例 09 向量数据库](../example-09-vector-stores/README.md)。
