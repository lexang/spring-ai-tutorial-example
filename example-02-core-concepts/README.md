# Spring AI 2.0 核心概念示例

通过 Prompt、Token 和 Embedding 三组实验理解聊天请求的数据流，配套博客《Spring AI 核心概念：从聊天到知识检索》。本目录可独立构建和运行。

## 环境与版本

JDK 17+、Maven 3.9+；构建固定 Spring Boot 4.1.2、Spring AI 2.0.2。默认应用创建 OpenAI ChatModel 和 EmbeddingModel，真实接口需要有效密钥和模型权限。

## 离线构建

```powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
```

测试使用固定模型和本地计算，不调用外部 API。覆盖 Prompt 模板、角色、输入与空响应、Token 估算/公式、EmptyUsage 和向量相似度。

## 启动

PowerShell：

```powershell
$env:OPENAI_API_KEY = "从密钥管理器读取的值"
$env:OPENAI_CHAT_MODEL = "gpt-4o-mini"
$env:OPENAI_EMBEDDING_MODEL = "text-embedding-3-small"
mvn spring-boot:run
```

Linux/macOS：

```bash
export OPENAI_API_KEY='从密钥管理器读取的值'
export OPENAI_CHAT_MODEL='gpt-4o-mini'
export OPENAI_EMBEDDING_MODEL='text-embedding-3-small'
mvn spring-boot:run
```

模型名为示例配置，按实际服务和账户权限选择。非聊天/Embedding 模型已关闭。默认端口 8080，与入门项目相同；先停止其他占用该端口的应用。

## 三个主实验

```powershell
curl.exe -G "http://localhost:8080/prompt/with-system" --data-urlencode "role=Java专家" --data-urlencode "message=解释依赖注入"
curl.exe -G "http://localhost:8080/token/estimate" --data-urlencode "text=你好，Spring AI：List<String>"
curl.exe -G "http://localhost:8080/embeddings/similarity" --data-urlencode "text1=如何优化Java程序" --data-urlencode "text2=Java性能分析与调优"
```

/token/estimate 只做本地 tokenizer 估算，不访问远程模型；默认应用启动仍需要配置凭据。无凭据时可以通过测试验证这个端点的控制器方法。

/prompt/conversation 使用预置历史展示消息顺序，不保存前一次 HTTP 请求；真正的会话存储属于 Memory 专题。/prompt/template 使用 PromptTemplate 创建请求。

/embeddings/generate 返回实际维度和前十维预览；/embeddings/find-similar 为固定候选逐个生成向量并排序。后者是控制流程演示，每次请求有多次模型调用；生产检索应先完成文档向量入库。相似度标签是演示启发式，不是正确率或生产阈值。

更多请求在 [requests.http](./requests.http)。

## Token 与费用的边界

/token/usage 读取供应商 Usage；未报告的 EmptyUsage 会返回中文 502，避免记录为免费调用。/token/limit 展示非推理 maxTokens 和推理 maxCompletionTokens 的选择，实际模型必须支持相应选项。

/token/cost-calculator 只演示输入/输出计数乘费率的公式。内置费率与汇率均为演示常量，不是报价或账单；真实缓存、推理、重试及模型费率需另外校准。

## 代码入口

- [PromptController.java](./src/main/java/com/example/springai/PromptController.java)
- [TokenController.java](./src/main/java/com/example/springai/TokenController.java)
- [EmbeddingsController.java](./src/main/java/com/example/springai/EmbeddingsController.java)
- [CoreConceptsExceptionHandler.java](./src/main/java/com/example/springai/CoreConceptsExceptionHandler.java)
- [pom.xml](./pom.xml)

## 官方依据

- [v2.0.2 核心概念](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/concepts.adoc)
- [v2.0.2 响应用量](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/usage-handling.adoc)
