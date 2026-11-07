# Spring AI 2.0 个人知识管理系统

本项目展示 H2 事实库、VectorStore、元数据过滤和 QuestionAnswerAdvisor 组成的个人知识闭环。默认使用确定性离线 ChatModel/EmbeddingModel，不需要 API Key，默认绑定 127.0.0.1:8080。

## 运行

```powershell
mvn clean package
mvn spring-boot:run
```

启动后使用 `requests.http`。种子数据包含 Alice 的 Spring AI、MCP、旅行笔记和 Bob 的预算笔记，可验证租户隔离。

## 真实 OpenAI 模式

```powershell
$env:OPENAI_API_KEY='你的密钥'
mvn spring-boot:run "-Dspring-boot.run.profiles=live"
```

可用 `OPENAI_BASE_URL`、`OPENAI_CHAT_MODEL`、`OPENAI_EMBEDDING_MODEL` 覆盖默认值。密钥不得写入仓库。

## 数据一致性

- H2 `personal_note` 是事实来源，包含稳定 ID、owner、tag 和乐观锁 version。
- `SimpleVectorStore` 是可重建的进程内索引，不适合生产持久化或多实例共享。
- 检索先过滤 owner/tag，再用事实检索视图按 ID 回查 H2，并在送给模型之前替换为当前授权文本；来源不是事后用新标题掩盖旧上下文。
- 写锁包围 TransactionTemplate 的执行与提交，避免事务在方法解锁后才提交。跨数据库与外部向量库仍不是分布式事务，生产应使用 outbox、重试、幂等和对账任务。
- `/api/knowledge/reindex` 从 H2 全量重建本服务管理的索引。

## 验证范围

8 个离线测试覆盖启动重建、用户/标签隔离、RAG 来源、CRUD、乐观锁冲突、标签更新、全量重建及 HTTP 中文 400/404/409，还验证模型真正拿到当前授权文本而不是陈旧/越权索引内容。无来源时 status=NO_CONTEXT，不能用模型自由回答代替事实。ownerId 是模拟请求字段，公开服务必须来自认证主体。真实模型、持久化向量库和多实例一致性需另行验证。
