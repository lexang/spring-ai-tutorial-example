# 结构化输出：人员信息提取

配套博客《结构化输出实战：把描述转换为业务对象》，预约 2026-11-08 09:00（北京时间）。主线代码是 PersonExtractionController，使用同一 POST 请求切换提示、Schema、原生选项，转换后继续做业务验证。

## 版本与构建

JDK 17+、Maven 3.9+、Boot 4.1.2、Spring AI 2.0.2。

~~~powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
~~~

离线测试不需要 API Key。共 28 个测试通过，覆盖泛型、转换器、Schema 重试/累计、空返回、原生 Options、校验器重复注册、业务验证和中文 HTTP 错误。

## 启动真实模型

示例通过 Anthropic 兼容接口访问 MiniMax：

~~~powershell
$env:MINIMAX_API_KEY = "从密钥管理器读取的值"
$env:MINIMAX_BASE_URL = "https://api.minimax.io/anthropic"
$env:MINIMAX_MODEL = "MiniMax-M3"
mvn spring-boot:run
~~~

模型名为示例值，运行前核对服务端能力和账户权限；Key 只通过外部变量读取，仓库不含真实凭据。

## 主接口

~~~http
POST http://localhost:8080/api/extraction/person
Content-Type: application/json

{"description":"张三，25岁，软件工程师","reliability":"SCHEMA"}
~~~

模式 PROMPT 只做转换；SCHEMA（默认）启用响应侧检查；NATIVE 启用供应商原生 Schema 并做响应检查。所有模式都做最终人员业务验证，缺少姓名/职业或年龄为空/超范围时不返回成功对象。

返回 person、reliability、model、usageReported、totalTokens。没报告用量保持未知，不视为免费请求。更多请求见 [requests.http](./requests.http)。

## 代码与扩展

- [PersonExtractionController](./src/main/java/com/example/springai/PersonExtractionController.java)：博客 POST 主线。
- [PersonExtractionTest](./src/test/java/com/example/springai/PersonExtractionTest.java)：输入、模式、重试和业务校验。
- [StructuredOutputController](./src/main/java/com/example/springai/StructuredOutputController.java)：人员/简历/产品、泛型、Map/List、列表包装等扩展。
- [LenientJsonOutputConverter](./src/main/java/com/example/springai/LenientJsonOutputConverter.java)：解释文字代码围栏的受控提取。
- [PipeSeparatedPersonConverter](./src/main/java/com/example/springai/PipeSeparatedPersonConverter.java)：非 JSON 教学格式。
- [StructuredOutputExceptionHandler](./src/main/java/com/example/springai/StructuredOutputExceptionHandler.java)：中文 400/502，不泄露供应商原文。

## 需要理解的边界

Schema Advisor 默认追加三次重试，最多四次调用；耗尽后返回最后响应，转换和业务检查仍需处理失败。默认 Bean 转换器会清理独立围栏并忽略未知字段，不能将解析成功当作业务正确。

显式注册校验器后不再使用 spec.validateSchema，2.0.2 会追加第二个校验器。原始文本先经过 Schema 校验，再进入宽松 converter；无 Schema 的 converter 请求 native 会回退，validateSchema 则在模型调用前失败。

原生能力要按实际兼容服务验证，不能用 Options 实现了接口证明模型接受 Schema。OpenAI 原生顶层列表可用 object 包装，仍需验证集合内的数据。

本项目不包含真实供应商成功率或费用评测。Person 结果也不自动写库，业务事实要结合源描述/数据库继续确认。

## 官方依据

- [v2.0.2 Structured Output](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/structured-output.adoc)
- [系列入口](https://bitzw.modelgateai.com/blog/spring-ai-tutorials)
