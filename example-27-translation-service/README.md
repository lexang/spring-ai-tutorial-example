# Spring AI 2.0 多语言翻译服务示例

本项目用 ChatClient、JSON 编码任务数据和结构化输出构建多语言翻译服务。语言枚举、术语与长度由代码校验；JSON/分隔符只是数据边界，不构成模型提示注入防护。默认固定模型只替换术语并附标记，不进行真实翻译。

完整源码和请求在本目录，默认绑定 127.0.0.1:8080。

## 运行

```powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn spring-boot:run
```

## 端点

```http
POST http://localhost:8080/api/translation
Content-Type: application/json

{
  "text": "Spring AI 让构建企业级 AI 应用变得简单。",
  "source": "ZH",
  "target": "EN",
  "glossary": [
    {"source": "Spring AI", "target": "Spring AI"}
  ]
}
```

完整请求见 [requests.http](./requests.http)。

## live profile

```powershell
$env:OPENAI_API_KEY = "从外部密钥管理器读取的值"
mvn spring-boot:run "-Dspring-boot.run.profiles=live"
```

live 模式启用 OpenAI Chat，默认模型为 `gpt-4o-mini`，可通过 `OPENAI_CHAT_MODEL` 覆盖。密钥只从环境变量读取，不写入仓库。

## 关键边界

- 语言代码只能取 `ZH EN JA KO FR DE ES` 白名单；非法值返回中文 400。
- 源文本最大 5000 字符；术语表最多 50 条；同一对源语言和目标语言不允许相同。
- 数据用 JsonHelper 编码为 JSON，支持引号、换行和文本中的协议标记；这种机械隔离不能保证 LLM 忽略嵌入指令。
- 术语最多五十条，单词/译名最多二百字符，同一源词不重复。返回命中由应用按原文和译文的精确包含关系复核，不信任模型自行宣称。
- `entity(TranslationResult.class, spec -> spec.validateSchema())` 确保返回强类型且 JSON 形状自洽。
- 模型抛异常或结果为空时，应用返回中文 502，不会泄漏内部堆栈。

live profile 同时启用 OpenAI 和关闭离线 Bean，含不访问外部服务的装配测试；真实翻译质量仍需任务语料、人工与对应环境评估，本次不提供质量结论。
