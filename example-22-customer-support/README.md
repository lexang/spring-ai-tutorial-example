# Spring AI 2.0 AI 客服助手示例

本项目把 `MessageChatMemoryAdvisor`、`QuestionAnswerAdvisor` 和 `ToolCallingAdvisor` 组合成一个客服助手：FAQ 问题走知识库检索，订单问题自动调用订单工具，回答前后保留会话记忆。客户身份不会放进模型提示词，而是通过 `ToolContext` 传给工具。

完整源码和请求位于本目录；FAQ 与订单数据均为演示数据，不是实际售后政策。

## 版本与依赖

- Java 17+
- Spring Boot 4.1.2
- Spring AI 2.0.2 BOM
- `spring-ai-client-chat`
- `spring-ai-vector-store-advisor`
- `spring-ai-vector-store`
- `spring-ai-starter-model-openai`（仅 `live` profile）

示例使用 `SimpleVectorStore` 和确定性离线模型，默认不访问网络。离线 Embedding 只是 Unicode n-gram 哈希，离线 ChatModel 只为测试稳定触发工具调用；它们不代表真实语义质量。`SimpleVectorStore` 是内存演示实现，重启后知识库丢失，不适合生产。

## 构建与运行

```powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn spring-boot:run
```

请求示例见 [requests.http](./requests.http)：

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/support/status` | 查看模型和已跟踪会话锁 |
| `POST` | `/api/support/chat` | FAQ/RAG、订单工具和记忆统一入口 |
| `DELETE` | `/api/support/conversations` | 清理指定客户的会话 |

请求必须提供 `customerId`、`conversationId` 和 `message`。服务端把记忆键作用域化为 `customerId:conversationId`，所以不同客户即使提交同名会话也不会共享历史。相同作用域的请求使用锁串行化，避免并发请求交错写入一轮对话。

## 切换真实模型

```powershell
$env:OPENAI_API_KEY = "从外部密钥管理器读取的值"
$env:SPRING_PROFILES_ACTIVE = "live"
mvn spring-boot:run
```

密钥只从环境变量读取。真实调用还需要模型权限、额度和网络；本项目没有把真实供应商端到端调用写成已验证。

## 安全边界

`CustomerOrderTools#getOrderStatus` 只接收模型可见的 `orderId`，客户身份从 `ToolContext` 读取：

```java
.toolContext(Map.of(CustomerOrderTools.CUSTOMER_ID, validated.customerId()))
```

工具会再次校验订单所属客户，越权时返回通用“未找到该客户的订单”，不泄露其他客户的订单存在性或物流单号。生产系统还必须把 `customerId` 换成经过认证过滤器确认的主体标识，而不是信任请求 JSON。

## 测试范围

15 个离线测试覆盖：

- `ToolContext` 的授权、越权和缺失上下文失败关闭；
- RAG 来源回传、工具两轮循环和工具错误结果；
- Message Window 只保存最终用户/助手轮次；
- 客户作用域会话隔离、清理和输入校验；
- Spring Boot MockMvc 端到端请求与中文 `400`。

会话锁按引用计数登记等待者，最后一个使用者离开才移除；清理与聊天共用同一锁，避免清理后误建第二把锁。status 的 trackedConversationLocks 是当前活跃/等待的锁项，不是历史会话数量。模型抛出的参数异常仍归为模型 502，不能当成用户 400；非法 JSON 返回中文 400。服务默认绑定 127.0.0.1:8080，锁与记忆均为单实例演示。

```powershell
mvn test "-Dmaven.build.cache.enabled=false"
```

生产迁移时应替换为持久化 ChatMemory/Session 存储和持久化 Vector Store，并补充身份认证、权限过滤、工具审计、敏感数据脱敏、限流、配额、评估和多实例事务。官方文档还特别指出，JDBC ChatMemoryRepository 不保存工具调用消息；本示例使用内存 `MessageWindowChatMemory` 只保存最终轮次，若业务需要完整工具历史，应评估 Spring AI Session 或自定义 Repository。
