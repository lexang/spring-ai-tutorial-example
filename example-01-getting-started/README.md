# Spring AI 2.0 入门示例

用 ChatClient 实现同步聊天和 SSE 输出，配套博客《Spring AI 2.0 实战：搭建第一个聊天接口》。本目录可独立运行，下载源码包解压后直接进入此目录即可。

## 环境与版本

- JDK 17 或更高版本，Maven 3.9+。
- Spring Boot 4.1.2，Spring AI BOM 2.0.2。
- 真实聊天需要 OpenAI API Key、模型访问权限和网络；密钥只从环境变量读取。

## 先运行离线测试

```powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
```

测试使用固定 ChatModel，不需要 API Key。覆盖同步请求、空输入、参数绑定、空回答、供应商错误、Options 与延迟订阅。它验证应用行为，不访问真实模型。

## 启动真实聊天

PowerShell：

```powershell
$env:OPENAI_API_KEY = "从密钥管理器读取的值"
$env:OPENAI_CHAT_MODEL = "gpt-4o-mini"
mvn spring-boot:run
```

Linux/macOS：

```bash
export OPENAI_API_KEY='从密钥管理器读取的值'
export OPENAI_CHAT_MODEL='gpt-4o-mini'
mvn spring-boot:run
```

模型名是示例值，需确认账户权限和该模型支持的参数。配置中的 temperature=0.7 不适用于所有模型，切换前先核对供应商规格。application.properties 已关闭非聊天模型，不保存实际凭据。

## 运行主接口

Windows 用 curl.exe，Linux/macOS 用 curl：

```powershell
curl.exe -G "http://localhost:8080/chat" --data-urlencode "message=请用一句话介绍 Spring AI"
curl.exe -N -G "http://localhost:8080/chat/stream" --data-urlencode "message=分三点说明 Spring AI 的用途"
curl.exe -i -G "http://localhost:8080/chat" --data-urlencode "message= "
```

前两个有效请求访问真实模型。同步返回非空文本；SSE 使用 text/event-stream，按顺序发送片段；空白输入应返回中文 400。回答内容和分块大小不固定。也可以使用 [requests.http](./requests.http)。

## 项目结构

- [pom.xml](./pom.xml)：Boot parent 和 AI BOM 依赖管理。
- [ChatController.java](./src/main/java/com/example/springai/ChatController.java)：主接口与温度扩展。
- [GettingStartedExceptionHandler.java](./src/main/java/com/example/springai/GettingStartedExceptionHandler.java)：中文 400/502。
- [ChatControllerTest.java](./src/test/java/com/example/springai/ChatControllerTest.java)：离线契约测试。
- build.gradle / build.gradle.kts：可选 Gradle 构建，显式导入 Boot 与 AI BOM。

## 扩展接口和重要边界

/chat/safe 演示 ResponseEntity 返回；/chat/creative 接受 message 和 temperature，拒绝 NaN、无穷和超范围值。主页列出全部接口。

ChatClient.call() 只选择同步规格，content() 才执行；流式内容在订阅时产生。本例用 Spring MVC 承载 SSE，不需要额外加入 WebFlux starter。

GET 接口用于教学；正式服务宜用 POST 请求体，并实现权限、长度和并发控制。SSE 已发送事件后报错会中断流，不能保证再返回 JSON 502。日志仅记录长度和错误类别，不输出用户正文或供应商错误原文。

## 官方依据

- [v2.0.2 ChatClient 文档](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chatclient.adoc)
- [v2.0.2 入门文档](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/getting-started.adoc)
