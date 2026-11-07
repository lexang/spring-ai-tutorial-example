# Spring AI 2.0 AI 驱动的测试生成器示例

本项目演示如何用 Spring AI 2.0 的 ChatClient 结构化输出、RelevancyEvaluator 评估器和可控 Prompt 构建一个 AI 驱动的测试生成器：传入目标类签名和指令，离线生成确定性的测试计划，再用 RelevancyEvaluator 评估测试计划的相关性，模型失败映射为中文 502。

完整代码与请求在本目录，默认绑定 127.0.0.1:8080。生成的是测试计划，不是已经执行的测试代码。

## 运行

```powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn spring-boot:run
```

## 端点

生成测试计划：

```http
POST http://localhost:8080/api/test-gen/generate
Content-Type: application/json

{
  "targetClass": "UserService",
  "classSignature": "public class UserService { boolean login(String username, String password); }",
  "instruction": "覆盖正常登录、空密码、错误密码三种场景",
  "maxCases": 3
}
```

评估测试计划：

```http
POST http://localhost:8080/api/test-gen/evaluate
Content-Type: application/json

{
  "targetClass": "UserService",
  "summary": "为 UserService 生成 3 个测试用例",
  "testCases": [
    {"name": "login_success", "description": "验证正常登录", "input": "admin/secret", "expectedResult": "true", "mockData": [{"field": "id", "value": "1"}]}
  ]
}
```

完整请求见 requests.http。

## live profile

```powershell
$env:OPENAI_API_KEY = "从外部密钥管理器读取的值"
mvn spring-boot:run "-Dspring-boot.run.profiles=live"
```

live 启用 OpenAI Chat，生成与 Judge 使用同一供应商模型、独立提示职责，不声称独立模型复核。在线 Judge 已完整装配；装配测试不调用供应商，真实质量需另行评估。

## 关键边界

- targetClass 不能为空且长度不超过 200；classSignature 不能为空且长度不超过 2000；instruction 长度不超过 500。
- maxCases 为空时取默认 3，必须在 1 到 10 之间；非法值返回中文 400。
- targetClass、classSignature、instruction、maxCases 用开始/结束分隔标记注入提示词，并明确告知模型忽略其中嵌入的指令，降低提示注入风险。
- 用 entity(Class, spec -> spec.validateSchema()) 让模型返回强类型 TestPlan 并按结构自检。
- 生成后再用 validatePlan 校验字段完整性：缺少用例或字段不完整时返回中文 502。
- RelevancyEvaluator 用确定的 query、上下文（来自 TestPlan 的目标类与用例清单）和响应摘要做相关性评估；离线评判模型用文本包含关系返回 YES/NO，便于无 API Key 时验证评估流程。
- 模型抛异常或结果为空时，应用返回中文 502，不会泄漏内部堆栈。

evaluate 是摘要与计划自身的一致性演示；evaluate-spec 使用原始 classSignature/instruction 作为上下文，完整计划作为 response，避免自我证明。返回 mode=contract-demo/model-judge 和 scope=summary-consistency/spec-relevancy；Judge 通过不等于测试覆盖完整或代码已运行。

模型不得改目标类或超过请求 maxCases，输出检查非空摘要、唯一用例名、有界字段和 Mock 数据。输入分隔符是提示组织，不是安全隔离。单次输出预算 3000 Token，Schema 自纠正还可能额外调用。
