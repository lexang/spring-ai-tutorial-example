# Spring AI 2.0 ETL Pipeline 示例

这是[教程 11：ETL 数据处理流水线](../../doc/11-etl-pipeline.md)的完整可运行项目。代码依据本仓库的 Spring AI 2.0 官方页面 [`api/etl-pipeline.adoc`](../../api/etl-pipeline.adoc) 和本地 2.0 源码核对。

项目覆盖：

- `DocumentReader`：Text、JSON、Markdown、HTML/JSoup、PDF、Tika。
- `DocumentTransformer`：Token 分块、内容格式规则、关键词和摘要元数据增强。
- `DocumentWriter`：`FileDocumentWriter` 与实现了 `DocumentWriter` 的 `VectorStore`。
- 完整的 Extract → Transform → Load 流程。
- 输入校验、中文错误响应、固定资源边界和受控文件输出。
- 29 个离线测试，以及 1 个默认跳过的 ONNX 集成测试。

## 版本基线

| 组件 | 版本 |
|---|---|
| Java | 17+ |
| Spring Boot | 4.1.2 |
| Spring AI BOM | 2.0.2 |

关键依赖均由 Spring AI BOM 管理，不单独锁定传递依赖版本：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-jsoup-document-reader</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-markdown-document-reader</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pdf-document-reader</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tika-document-reader</artifactId>
</dependency>
```

HTML 示例使用官方 `spring-ai-jsoup-document-reader`，不再手工声明旧版 `org.jsoup:jsoup`。项目也不包含 Windows 专用 PyTorch 运行时或 milestone 仓库。

## 三种运行模式

### 1. 离线学习 Reader、Splitter 和文件 Writer

默认配置为：

```properties
spring.ai.model.chat=none
spring.ai.model.embedding=none
```

因此不需要 API Key，也不会下载 ONNX 模型：

```bash
mvn spring-boot:run
```

以下端点可以直接使用：

- `/api/etl/reader/**`
- `/api/etl/transformer/split`
- `/api/etl/transformer/split/compare`
- `/api/etl/transformer/format`
- `/api/etl/writer/file`

需要 ChatModel 或 VectorStore 的端点会返回明确的中文 `422` 响应，不会在启动阶段失败。

### 2. 启用 ONNX EmbeddingModel 和 VectorStore

完整 Load 与相似度搜索需要嵌入模型。默认 URI 指向 Hugging Face 的 `all-MiniLM-L6-v2`：

PowerShell：

```powershell
$env:SPRING_AI_EMBEDDING_MODEL = "transformers"
mvn spring-boot:run
```

也可以改为已下载模型，避免运行时联网：

```powershell
$env:SPRING_AI_EMBEDDING_MODEL = "transformers"
$env:EMBEDDING_TOKENIZER_URI = "file:D:/models/all-MiniLM-L6-v2/tokenizer.json"
$env:EMBEDDING_MODEL_URI = "file:D:/models/all-MiniLM-L6-v2/model.onnx"
mvn spring-boot:run
```

Linux/macOS：

```bash
export SPRING_AI_EMBEDDING_MODEL=transformers
export EMBEDDING_TOKENIZER_URI=file:/opt/models/all-MiniLM-L6-v2/tokenizer.json
export EMBEDDING_MODEL_URI=file:/opt/models/all-MiniLM-L6-v2/model.onnx
mvn spring-boot:run
```

启用后可调用：

- `/api/etl/pipeline/process`
- `/api/etl/pipeline/multi-format`
- `/api/etl/pipeline/functional`
- `/api/etl/pipeline/search`
- `/api/etl/pipeline/clear`

### 3. 启用模型元数据增强

`KeywordMetadataEnricher` 和 `SummaryMetadataEnricher` 会产生真实模型调用。本项目按 Spring AI 2.0 的迁移要求，通过 Anthropic 兼容接口访问 MiniMax：

PowerShell：

```powershell
$env:SPRING_AI_CHAT_MODEL = "anthropic"
$env:MINIMAX_API_KEY = "你的密钥"
$env:MINIMAX_BASE_URL = "https://api.minimax.io/anthropic"
$env:MINIMAX_MODEL = "MiniMax-M3"
mvn spring-boot:run
```

凭据只从环境变量读取，仓库中没有默认密钥。

## 配置项

| 环境变量 | 默认值 | 用途 |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP 端口 |
| `SPRING_AI_CHAT_MODEL` | `none` | 设为 `anthropic` 后启用元数据增强 |
| `MINIMAX_API_KEY` | 空 | MiniMax API Key |
| `MINIMAX_BASE_URL` | `https://api.minimax.io/anthropic` | Anthropic 兼容地址 |
| `MINIMAX_MODEL` | `MiniMax-M3` | Chat 模型 |
| `SPRING_AI_EMBEDDING_MODEL` | `none` | 设为 `transformers` 后启用 ONNX 和 VectorStore |
| `EMBEDDING_TOKENIZER_URI` | Hugging Face tokenizer URI | Tokenizer 资源 |
| `EMBEDDING_MODEL_URI` | Hugging Face ONNX URI | ONNX 模型资源 |
| `EMBEDDING_CACHE_DIRECTORY` | `${java.io.tmpdir}/spring-ai-onnx-model` | 模型缓存目录 |
| `ETL_OUTPUT_DIRECTORY` | `${java.io.tmpdir}/spring-ai-etl-output` | 文件 Writer 的固定输出目录 |

## 快速验证

仓库提供 [`requests.http`](requests.http)，可以在 IntelliJ IDEA 或支持 REST Client 的编辑器中逐条执行。

### Reader

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/etl/reader/text` | UTF-8 文本读取与自动元数据 |
| GET | `/api/etl/reader/json?keys=title,content` | 选择 JSON 正文字段 |
| GET | `/api/etl/reader/json/metadata` | `JsonMetadataGenerator` |
| GET | `/api/etl/reader/json/pointer?pointer=/1` | RFC 6901 JSON Pointer |
| GET | `/api/etl/reader/markdown` | Markdown 水平线、代码块和引用配置 |
| GET | `/api/etl/reader/html` | 官方 `JsoupDocumentReader` |
| GET | `/api/etl/reader/pdf/page` | 按页读取 PDF |
| GET | `/api/etl/reader/pdf/paragraph` | 依赖 PDF catalog/目录结构 |
| GET | `/api/etl/reader/tika` | Tika 自动识别格式 |

### Transformer

| 方法 | 路径 | 是否调用模型 |
|---|---|---|
| GET | `/api/etl/transformer/split` | 否 |
| GET | `/api/etl/transformer/split/compare` | 否 |
| GET | `/api/etl/transformer/format` | 否 |
| GET | `/api/etl/transformer/enrich/keywords` | 是 |
| GET | `/api/etl/transformer/enrich/summary` | 是 |
| GET | `/api/etl/transformer/chain` | 是 |

分块示例：

```http
GET http://localhost:8080/api/etl/transformer/split?chunkSize=200&minChunkSizeChars=80&encoding=CL100K_BASE&chinesePunctuation=true
```

`minChunkSizeChars` 是寻找标点断句位置时使用的最小字符阈值，不是相邻块的重叠大小。`TokenTextSplitter` 没有 overlap 参数。

支持的编码类型：

- `CL100K_BASE`，默认。
- `P50K_BASE`。
- `O200K_BASE`。

### Writer 和完整 Pipeline

```http
POST http://localhost:8080/api/etl/writer/file?markers=true&metadataMode=ALL&append=false
```

`FileDocumentWriter` 只写入 `ETL_OUTPUT_DIRECTORY` 下固定的 `etl-output.txt`，HTTP 请求不能提交任意文件路径。

```http
POST http://localhost:8080/api/etl/pipeline/process?format=text&enableEnrich=false&enrichLimit=3
```

`format` 仅支持 `text`、`json`、`markdown`。未知格式返回 `400`，不会静默退化为文本读取。

```http
GET http://localhost:8080/api/etl/pipeline/search?query=Spring%20AI%20ETL&topK=3
```

```http
DELETE http://localhost:8080/api/etl/pipeline/clear
```

`clear` 会删除当前应用进程通过 ETL 端点成功写入并跟踪到的文档 ID，而不是只返回“重启即可清空”的提示。

## 关键 2.0 API

### 三个接口同时支持函数式与语义化方法

```java
public interface DocumentReader extends Supplier<List<Document>> {
    default List<Document> read() {
        return get();
    }
}

public interface DocumentTransformer extends Function<List<Document>, List<Document>> {
    default List<Document> transform(List<Document> documents) {
        return apply(documents);
    }
}

public interface DocumentWriter extends Consumer<List<Document>> {
    default void write(List<Document> documents) {
        accept(documents);
    }
}
```

因此官方的两种组合方式等价：

```java
vectorStore.accept(splitter.apply(reader.get()));
vectorStore.write(splitter.split(reader.read()));
```

### TokenTextSplitter 必须使用 Builder

```java
TokenTextSplitter splitter = TokenTextSplitter.builder()
        .withEncodingType(EncodingType.CL100K_BASE)
        .withChunkSize(800)
        .withMinChunkSizeChars(350)
        .withMinChunkLengthToEmbed(5)
        .withMaxNumChunks(10000)
        .withKeepSeparator(true)
        .withPunctuationMarks(List.of('.', '?', '!', '\n', '。', '？', '！'))
        .build();
```

2.0 的重要行为：

- 只有总 token 数大于 `chunkSize` 时，才应用标点断句。
- 小文本不会仅因为包含标点而被额外拆碎。
- 原始元数据和 `ContentFormatter` 默认复制到所有子块。
- 本地 2.0 源码还为子块加入 `parent_document_id`、`chunk_index`、`total_chunks`。
- `maxNumChunks` 只限制主循环；实现退出循环后仍可能追加一个剩余尾块，因此不是严格的最终列表长度上限。

### 官方 JSoup Reader

```java
JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
        .charset("UTF-8")
        .selector("article .content")
        .separator("\n\n")
        .groupByElement(false)
        .includeLinkUrls(true)
        .metadataTags(List.of("author", "date", "description"))
        .additionalMetadata("source", "sample.html")
        .build();

List<Document> documents = new JsoupDocumentReader(resource, config).read();
```

正确包名是 `org.springframework.ai.reader.jsoup`，不是旧教程中的 `reader.html`。

当前实现把空字符串传给 Jsoup 作为 base URI。`includeLinkUrls=true` 使用 `abs:href`：绝对链接可以保留，相对链接在没有基准 URI 时可能是空字符串；不要把它当成始终可靠的绝对 URL。`groupByElement=true` 无匹配时返回空列表，而合并模式会返回一个空 Document。

### 模型增强

```java
KeywordMetadataEnricher enricher = KeywordMetadataEnricher.builder(chatModel)
        .keywordCount(5)
        .build();
```

关键词写入 `excerpt_keywords`。自定义关键词模板必须包含 `{context_str}`；一旦设置自定义模板，`keywordCount` 会被忽略。

关键词增强器会原地修改输入文档；没有 generation 或空文本时可能不写入元数据。摘要增强器对空响应会写入空字符串。示例通过 `DocumentEnrichmentValidator` 拒绝空增强结果，避免把不完整文档继续写入向量库。

```java
SummaryMetadataEnricher enricher = new SummaryMetadataEnricher(
        chatModel,
        List.of(SummaryType.PREVIOUS, SummaryType.CURRENT, SummaryType.NEXT)
);
```

摘要元数据键为：

- `section_summary`
- `prev_section_summary`
- `next_section_summary`

## 安全边界

官方 ETL 文档特别提醒：Reader 常通过 `Resource`、文件或 URL 加载数据。不要直接用用户提交的 URL 或路径构造 Reader，否则可能导致 SSRF、内网探测或本地文件读取。

本示例采取以下约束：

- Reader 只读取 classpath 中的固定样例。
- JSON 字段、JSON Pointer、CSS selector、分页和 `topK` 都有边界校验。
- 文件 Writer 的目录来自服务端配置，文件名固定。
- 底层异常只写入服务端日志，对外返回中文安全消息。
- API Key 只通过环境变量注入。

## 测试

```bash
mvn clean test
```

默认结果：

- 29 个离线测试通过。
- 1 个 ONNX/VectorStore 集成测试跳过。
- 离线上下文测试确认 ChatModel 和 EmbeddingModel 均为 `none` 时应用仍可启动。

离线测试覆盖：

- Text、JSON、JSON Pointer、Markdown、JSoup Reader。
- 三个 ETL 接口的 `read/transform/write` 委托关系。
- 2.0 小文本标点行为、元数据与 ContentFormatter 复制。
- `ContentFormatTransformer` 的排除规则合并行为。
- `FileDocumentWriter` markers、MetadataMode 和 append。
- 完整 Pipeline 的写入、文档 ID 跟踪、真实 delete 调用和未知格式拒绝。
- PDF 按页页码元数据、无目录 PDF 的段落 Reader 失败、Tika 单文档输出。
- 空模型响应、删除失败后的跟踪保留和写入/清理并发边界。

若已准备可访问的 ONNX 模型，可运行集成测试：

PowerShell：

```powershell
$env:RUN_ETL_INTEGRATION_TESTS = "true"
$env:EMBEDDING_TOKENIZER_URI = "file:D:/models/all-MiniLM-L6-v2/tokenizer.json"
$env:EMBEDDING_MODEL_URI = "file:D:/models/all-MiniLM-L6-v2/model.onnx"
mvn test
```

## 已知边界

- `TextReader` 会把整个资源读入内存，不适合直接处理超大文本。
- `JsonReader` 先用 `readTree` 构建完整 JSON 树；官方页面写的“数组流式处理”与 2.0.2 发布实现不一致。数组转换虽使用并行 stream，但不会降低解析峰值内存。
- `JsonReader.get(pointer)` 对无效或不存在的 RFC 6901 Pointer 抛出 `IllegalArgumentException`。
- `JsonReader` 指定字段不存在时会回退为整个对象的字符串表示；数组元素应为对象，不要把 Pointer 指向标量。
- `MarkdownDocumentReader` 使用平台默认字符集，标题只进入元数据，代码块/引用块的合并边界是当前访问器的方向性实现；不要假设样式标记原样保留。
- `PagePdfDocumentReader` 单页 Document 可能没有 `end_page_number`；`ParagraphPdfDocumentReader` 需要 PDF outline/catalog，且 Reader 在构造时解析并持有 PDF 文档。
- `TikaDocumentReader` 使用无限制 `BodyContentHandler` 将全文放入内存，返回一个 Document；Reader 实例不要跨请求共享或反复复用。
- `ParagraphPdfDocumentReader` 依赖 PDF 的 catalog/目录结构，并非所有 PDF 都适用。
- `FileDocumentWriter` 使用系统默认字符编码；跨平台交换文件时应特别检查编码。
- 元数据增强和向量写入分别依赖外部 ChatModel 与 EmbeddingModel，离线测试不把编译通过表述为真实服务调用通过。

## 博客主线：预览后再显式入库

预约 2026-11-16 09:00 +08:00，slug 为 spring-ai-etl-indexing。

```http
POST http://localhost:8080/api/etl/pipeline/preview?format=json
```

预览只读取固定 classpath 样例并执行相同 Splitter，不解析模型/向量库 Bean，也不执行 Load。返回 pipeline=[Extract,Transform]、loaded=false 及分块统计和部分预览；它不是完整文件输出或已入库状态。默认离线启动就能使用。

检查样例后，显式启用 EmbeddingModel，再调用 /api/etl/pipeline/process 执行写入。该教学流程没有持久化任务台账、自动版本替换或全局事务；重复导入可能产生新文档 ID，不能把调用完成当成幂等索引更新。clear 只清理当前进程跟踪的写入 ID，也不代表外部数据库精确删除数量。

新增两个预览测试验证不获取模型、不写向量库及未知格式拒绝；完整构建为 30 个测试，29 个通过、1 个真实 ONNX 条件测试跳过。

## 项目结构

```text
example-11-etl-pipeline/
├── pom.xml
├── README.md
├── requests.http
└── src/
    ├── main/java/com/example/springai/
    │   ├── EtlPipelineApplication.java
    │   ├── config/VectorStoreConfig.java
    │   ├── pipeline/
    │   ├── reader/
    │   ├── transformer/
    │   ├── writer/
    │   └── support/
    ├── main/resources/
    │   ├── application.properties
    │   └── samples/
    └── test/java/com/example/springai/
```
