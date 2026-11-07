# Spring AI 2.0 代码生成与分析示例

本项目演示如何用 Spring AI 2.0 的 ChatClient 结构化输出 API 构建代码工程助手：代码审查、测试计划生成和最小实现生成。默认使用确定性离线 ChatModel，不执行用户代码、不访问网络。

完整源码和请求位于本目录，重点是结构化建议与业务校验，不自动执行或部署生成代码。

## 版本与依赖

- Java 17+
- Spring Boot 4.1.2
- Spring AI 2.0.2 BOM
- `spring-ai-client-chat`
- `spring-ai-starter-model-openai`（仅 `live` profile）

核心结果通过 `.entity(Type.class, spec -> spec.validateSchema())` 解析为 Java record。`validateSchema()` 只能保证格式和 Schema 约束，不能证明生成的代码正确、安全或可编译。

## 运行

```powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn spring-boot:run
```

端点：

| 方法 | 路径 | 作用 |
|---|---|---|
| `POST` | `/api/code/review` | 返回问题、严重级别、行号和评分 |
| `POST` | `/api/code/tests` | 返回语言对应的测试计划 |
| `POST` | `/api/code/implement` | 返回最小实现、解释和警告 |

请求体统一为 language 与 code。支持 Java、Kotlin、Python、JavaScript、TypeScript；代码长度上限 30,000 字符。Java/Kotlin 测试框架为 JUnit 5，Python 为 pytest，JS/TS 为 Vitest。疑似 Key 或 password 赋值会在调用模型前返回中文 400，但此轻量检测不是完整秘密扫描器。

## 切换真实模型

```powershell
$env:OPENAI_API_KEY = "从外部密钥管理器读取的值"
$env:SPRING_PROFILES_ACTIVE = "live"
mvn spring-boot:run
```

密钥不写入仓库。真实模型调用需要有效凭据、额度、网络和适合结构化输出的模型能力；本地没有执行真实供应商调用。

## 测试范围

11 个离线测试覆盖结构化对象解析、Schema 校验、三个业务端点、输入语言/长度/凭据校验、中文 400 和统一 502；输出还检查评分 0–100、白名单严重级别、原代码行号范围、列表与生成语言。服务默认绑定 127.0.0.1:8080，单次输出预算为 2000 Token，Schema 自我纠正可能增加调用次数。

生成的代码只作为文本返回，固定离线模板不代表模型真实实现质量。生产流水线必须在隔离沙箱中编译、测试、扫描和人工审核，不能直接部署。
