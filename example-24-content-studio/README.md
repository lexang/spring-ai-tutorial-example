# Spring AI 2.0 多模态内容创作平台示例

本项目编排 ChatModel、ImageModel 和 TextToSpeechModel，一次请求返回文章、图片提示词、图片结果和音频。默认离线模型不访问网络，返回可解码的一像素 PNG 和半秒静音 WAV，只验证媒体契约，不代表图片创作或语音合成效果。

完整代码与请求在本目录，文案与媒体仍需发布前审核。

## 运行

```powershell
mvn clean package "-Dmaven.build.cache.enabled=false"
mvn spring-boot:run
```

调用 `POST /api/content/packages`：

```json
{
  "topic": "Spring AI 2.0 如何统一编排多种模型",
  "audience": "Java 开发者",
  "style": "professional"
}
```

支持 `professional`、`friendly`、`concise`、`story` 四种受控风格。结果返回结构化文案、图片 URL/base64 二选一、音频 base64、Content-Type 和审核警告。

## live profile

```powershell
$env:OPENAI_API_KEY = "从外部密钥管理器读取的值"
$env:SPRING_PROFILES_ACTIVE = "live"
mvn spring-boot:run
```

`live` profile 同时启用 OpenAI Chat、Image 和 Speech 模型。模型名可通过 `OPENAI_CHAT_MODEL`、`OPENAI_IMAGE_MODEL`、`OPENAI_SPEECH_MODEL` 外部覆盖，默认分别为 `gpt-4o-mini`、`gpt-image-1-mini` 和 `gpt-4o-mini-tts`。项目显式要求 TTS 返回 WAV，使 `audioContentType=audio/wav` 与真实输出一致。真实调用需要有效凭据、模型权限、额度和网络，本地没有执行真实媒体生成。

## 验证与边界

7 个离线测试覆盖完整编排、结构化计划、空图片/音频、风格校验、MockMvc 和中文 `400`/`502`。生产系统还必须处理异步任务、对象存储、临时 URL、媒体大小、版权/肖像权、内容安全、事实核查、失败补偿、成本预算和人工审核。

mode=contract-demo 明确表示离线媒体，在线结果为 model-generated，在线不再提示“离线占位”。base64 优先，结果不同时返回 URL 与 base64；图片/音频限制 10 MiB，WAV 还检查长度和 RIFF/WAVE 标识，测试实际通过解码器读取。模型正文的安全提示不是正式内容审核器。默认绑定 127.0.0.1:8080。
