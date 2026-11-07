# Spring AI 中文实战示例

面向 Java / Spring Boot 开发者的 Spring AI 教程配套代码。每个目录都是可独立构建和运行的项目，随着博客教程完成逐步补充。

## 版本基线

- Spring AI 2.0.2
- Spring Boot 4.1.2
- Java 17 或更高版本
- Maven 3.9 或更高版本

版本固定用于复现。供应商支持的模型、参数、额度和网络条件需按实际账户确认；API Key 只通过环境变量或外部配置注入。

## 示例目录

| 目录 | 教程任务 | 本地验证 |
|---|---|---|
| [example-01-getting-started](./example-01-getting-started/README.md) | 用 ChatClient 完成同步聊天与 SSE，处理输入和模型错误 | 10 个离线测试 |
| [example-02-core-concepts](./example-02-core-concepts/README.md) | 通过 Prompt、Token 和 Embedding 实验理解输入与检索 | 14 个离线测试 |

后续按调用、模板、结构化输出、多模型、工具、检索、工程实践和综合业务项目补充，不按官方供应商或数据库子页面复制同款示例。

## 获取和运行

从 GitHub 克隆本仓库，进入所选示例目录执行：

```powershell
git clone https://github.com/lexang/spring-ai-tutorial-example.git
cd spring-ai-tutorial-example
cd example-01-getting-started
mvn clean package "-Dmaven.build.cache.enabled=false"
```

默认测试使用固定模型或本地计算，无需 API Key，不访问远程模型服务。要运行真实 HTTP 示例，在当前启动进程中注入环境变量：

```powershell
$env:OPENAI_API_KEY = "从密钥管理器读取的值"
$env:OPENAI_CHAT_MODEL = "gpt-4o-mini"
mvn spring-boot:run
```

核心概念项目还使用 `OPENAI_EMBEDDING_MODEL` 选择 Embedding 模型。具体端点、准备步骤和结果边界见各项目 README 与 `requests.http`。

默认端口均为 8080，运行另一个项目之前停止占用该端口的服务。模型名是示例配置，切换时要同时核对该模型的参数支持，不能只修改名字。

## 验证范围

离线测试验证 Java API、控制流、输入、错误处理和计算边界。真实模型的回答质量、Token 成本、SSE 体验、限流及网络连通性需要在相应环境中验证。

仓库只保存源代码、构建文件、配置模板、README 和请求示例，不提交密钥、个人环境文件或 Maven/Gradle 构建产物。不要将教学用 GET 聊天接口直接作为完整的生产权限方案。

## 官方参考

- [Spring AI v2.0.2 发布源码](https://github.com/spring-projects/spring-ai/tree/v2.0.2)
- [Spring AI 官方参考文档](https://docs.spring.io/spring-ai/reference/index.html)

本仓库是中文教程配套项目，与 Spring AI 官方仓库分别维护。

## 博客文章

- [Spring AI 实战系列](https://bitzw.modelgateai.com/blog/spring-ai-tutorials)
- [搭建第一个聊天接口](https://bitzw.modelgateai.com/blog/spring-ai-chat-getting-started)
- [从聊天到知识检索的核心概念](https://bitzw.modelgateai.com/blog/spring-ai-core-concepts)
