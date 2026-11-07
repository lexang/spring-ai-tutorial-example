# Spring AI 2.0 实时数据分析助手示例

本项目演示一条可审计的数据分析链路：`ChatClient` 把自然语言转换为强类型 `AnalysisPlan`，应用再从枚举白名单生成参数化 SQL，查询当前 H2 数据并返回图表数据规格。默认离线模型不会访问外部网络。

完整代码与请求在本目录。这里的实时是每次查询当前数据库，不是流式数据平台或实时推送。

## 运行

```powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn spring-boot:run
```

先查看可用指标、维度和样例目录：

```http
GET http://localhost:8080/api/analytics/catalog
```

执行分析：

```http
POST http://localhost:8080/api/analytics/query
Content-Type: application/json

{
  "question": "比较各地区销售额",
  "startDate": "2026-07-20",
  "endDate": "2026-07-26",
  "limit": 10
}
```

写入一条新事件后再次查询，即可看到当前数据库状态：

```http
POST http://localhost:8080/api/analytics/events
Content-Type: application/json

{
  "saleDate": "2026-07-26",
  "region": "华南",
  "product": "Spring AI 课程",
  "channel": "官网",
  "units": 2,
  "revenue": 598.00
}
```

完整请求见 [requests.http](./requests.http)。

## live profile

```powershell
$env:OPENAI_API_KEY = "从外部密钥管理器读取的值"
$env:SPRING_PROFILES_ACTIVE = "live"
mvn spring-boot:run
```

live 模式只启用 OpenAI Chat，默认模型为 `gpt-4o-mini`，可通过 `OPENAI_CHAT_MODEL` 覆盖。数据库用户名和密码分别从 `ANALYTICS_DB_USER`、`ANALYTICS_DB_PASSWORD` 注入。

## 关键边界

- 模型不能输出可直接执行的自由文本 SQL，只能选择 `Metric`、`Dimension` 和 `ChartType` 枚举。
- `validateSchema()` 校验 JSON 形状；应用仍检查 DAY/LINE 组合、标题、日期范围和行数上限。
- 表名、列名和聚合函数全部由应用白名单映射；日期和 `limit` 使用命名参数。
- 返回的是图表数据规格，不执行模型生成的 JavaScript、HTML 或模板代码。
- H2、固定目录和单实例写入端点只用于教学；生产环境应使用只读账号、分析副本、租户过滤、查询超时和审计。

13 个离线测试覆盖 SQL 白名单、结构化计划、Schema 与业务语义边界、H2 聚合、实时写入后可见性、HTTP 端点和中文错误响应。

ORDERS 使用 COUNT(*) 计算样例销售事件行数，不是按真实订单 ID 去重；指标定义必须和实际数据模型一致。默认绑定 127.0.0.1:8080，写入端点和 SQL/参数返回只供本机教学，不可直接公开。
