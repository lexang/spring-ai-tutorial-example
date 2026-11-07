# Prompt 模板：预览与生成示例

配套博客《Prompt 模板实战：先预览，再调用模型》。主线使用固定 UTF-8 资源创建系统模板，用户问题独立成为 UserMessage；预览与生成复用同一消息组装逻辑。

## 环境和版本

JDK 17+、Maven 3.9+；Spring Boot 4.1.2、Spring AI 2.0.2。离线预览不需要 API Key，真实生成使用示例配置的 Anthropic/MiniMax 兼容服务。

## 离线预览

~~~powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn spring-boot:run "-Dspring-boot.run.profiles=preview"
~~~

preview profile 关闭远程模型自动配置，生成端点明确报错，只有预览和本地输入检查可以执行，不会访问供应商。

~~~http
POST http://localhost:8080/template/preview
Content-Type: application/json

{"question":"分析 List<String> 和 JSON：{\"a\":1}","expertise":"Java"}
~~~

返回 system 与 user 两个字段，用户正文中的大括号和占位符文本保持原样。接口使用固定 classpath 资源，不能由用户直接拼出文件路径。

## 使用真实模型

停止预览应用，不使用 preview profile，配置：

~~~powershell
$env:MINIMAX_API_KEY = "从密钥管理器读取的值"
$env:MINIMAX_BASE_URL = "https://api.minimax.io/anthropic"
$env:MINIMAX_MODEL = "MiniMax-M3"
mvn spring-boot:run
~~~

如果 SPRING_PROFILES_ACTIVE 外部变量仍设为 preview，需要移除它。模型名是示例值，实际能力和权限以配置的服务为准。

向 POST /template/expert 发送相同请求体执行生成。也保留 GET /template/expert 作为简单交互入口；输入无效返回中文 400，生成失败/空回答返回中文 502。

## 代码入口

- [ExpertAssistant](./src/main/java/com/example/springai/ExpertAssistant.java)：预览与调用共用 buildPrompt，显式 UTF-8。
- [system-expert.st](./src/main/resources/prompts/system-expert.st)：系统模板。
- [TemplateController](./src/main/java/com/example/springai/TemplateController.java)：主接口及其他模板演示。
- [PreviewModelConfiguration](./src/main/java/com/example/springai/PreviewModelConfiguration.java)：离线模式的生成拒绝。
- [requests.http](./requests.http)：两条 POST 主线及扩展请求。

其他端点继续演示 StringTemplate、不同分隔符、资源翻译、代码审查、批量输入、文章大纲、客服与 Usage。它们需要真实生成配置。

## 模板边界

render/createMessage/create 都不会调用模型。SystemPromptTemplate.createMessage(Map) 返回系统消息，但带 Options/media 的继承重载仍走用户消息逻辑，使用前核对实际角色。

资源模板构造器在 2.0.2 使用默认字符集；专家示例显式 UTF-8。其他 Resource 构造示例在 Java 17 Windows 上需确保 JVM 为 UTF-8，或按专家示例显式读取。

自定义分隔符只解决 JSON 大括号和模板表达式冲突，不是 JSON 字符串转义。模板参数不是可信指令，应用仍负责工具和数据权限。

DollarTemplateRenderer 是教学用简单变量替换器，只扫描原模板一次，参数值不二次展开；不支持列表/条件，也不做 HTML/JSON 转义。

## 验证

独立 clean package 共 30 个测试，全部通过，不访问外部模型。包含默认/自定义模板、资源文件、角色与重载、未知变量、输入与 HTTP 错误、预览模式无密钥启动和最终文本保持。

真实模型生成效果、兼容 API 行为、费用和网络需另行验证，预览成功不代表这些项目已验证。

## 参考

- [博客文章](https://bitzw.modelgateai.com/blog/spring-ai-prompt-templates)
- [官方 v2.0.2 Prompt](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/prompt.adoc)
