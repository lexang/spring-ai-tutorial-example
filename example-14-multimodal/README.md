# Spring AI 2.0 多模态处理示例

本项目与中文教程 14 配套，依据 Spring AI 官方的 Multimodality、Image Model、Transcription、TTS、OpenAI Chat/Image/Audio 页面，以及 `upgrade-notes.adoc` 和本地源码编写。

示例用一个 `spring-ai-starter-model-openai` 展示四条彼此独立的调用链：

- `ChatModel` + `Media`：图片理解；
- `ImageModel`：文本生成图片；
- `TranscriptionModel`：语音转文字；
- `TextToSpeechModel`：文字转语音及流式音频。

## 版本基线

- Java 17
- Spring Boot 4.1.2
- Spring AI BOM 2.0.2

2.0.2 已包含 `TranscriptionModel` 继承 `StreamingTranscriptionModel` 的契约；自定义模型若未覆盖 stream，调用会得到 `UnsupportedOperationException`。本项目转写端点继续使用同步方式。OpenAI TTS 已逐块发出网络音频，完整音频必须按顺序消费全部 chunk，不能只取 `blockFirst()`；chunk 数量并不固定。

## 项目结构

```text
example-14-multimodal/
├── pom.xml
├── README.md
├── requests.http
└── src/
    ├── main/java/com/example/springai/
    │   ├── MultimodalApplication.java
    │   ├── controller/MultimodalController.java
    │   ├── service/MultimodalService.java
    │   └── support/
    │       ├── FeatureUnavailableException.java
    │       ├── MultimodalOperationException.java
    │       └── MultimodalExceptionHandler.java
    ├── main/resources/application.properties
    └── test/java/com/example/springai/
        ├── MultimodalApplicationTest.java
        ├── MultimodalExceptionHandlerTest.java
        └── MultimodalServiceTest.java
```

## 默认离线运行

四类目标模型默认都设为 `none`，同时关闭 starter 可能带来的 Embedding 和 Moderation 自动配置。不配置 API Key 也能启动应用、查看状态、验证中文 400/503 响应并运行全部确定性测试；模型空响应和供应商失败的中文 502 契约由假模型测试覆盖：

```powershell
mvn clean package
mvn spring-boot:run
```

查看状态：

```http
GET http://localhost:8080/api/multimodal/status
```

默认响应：

```json
{
  "chatEnabled": false,
  "imageEnabled": false,
  "transcriptionEnabled": false,
  "speechEnabled": false
}
```

## 启用 OpenAI 模型

### 供应商版本边界（2026-11-07 核对）

框架 API 固定为 Spring AI 2.0.2，但模型服务会变化。图像默认 gpt-image-1-mini 仍在官方支持列表中；GPT Image 不接受旧 style 和 response_format 参数，本例不发送它们。

图片理解的 gpt-4o-mini 已标记 Deprecated，保留仅用于已有配置复现，不作为新项目长期模型推荐；用 OPENAI_CHAT_MODEL 选择账号可用且支持图像输入的模型，并重新验证参数与输出。

转写默认更新为 gpt-transcribe、JSON 输出，仍使用 /audio/transcriptions。真实 SDK 本机测试检查：覆盖请求语言不会把 model/format 重置成 Options 构造器默认值。

OpenAI 官方公告说明 tts-1、tts-1-hd 和 gpt-4o-mini-tts 于 2027-01-06 停用。这里的 Speech API 是兼容期示例，不能通过把 model 改为 gpt-realtime-2.1-mini 完成迁移：Realtime 是另一套连接与请求流程，本项目未实现它。其他 TTS 供应商也需要对应 starter、凭据和参数校验，不能只改一个开关。

PowerShell：

```powershell
$env:OPENAI_API_KEY = "你的密钥"
$env:SPRING_AI_CHAT_MODEL = "openai"
$env:SPRING_AI_IMAGE_MODEL = "openai"
$env:SPRING_AI_TRANSCRIPTION_MODEL = "openai"
$env:SPRING_AI_SPEECH_MODEL = "openai"
mvn spring-boot:run
```

Linux/macOS：

```bash
export OPENAI_API_KEY='你的密钥'
export SPRING_AI_CHAT_MODEL=openai
export SPRING_AI_IMAGE_MODEL=openai
export SPRING_AI_TRANSCRIPTION_MODEL=openai
export SPRING_AI_SPEECH_MODEL=openai
mvn spring-boot:run
```

只用某一项能力时，只启用对应开关即可。例如只做转写时，仅设置 `SPRING_AI_TRANSCRIPTION_MODEL=openai`。

本项目为了保持依赖最小只选 OpenAI。官方还提供 Stability AI 的 `ImageModel` 和 ElevenLabs 的 `TextToSpeechModel`；切换它们需要加入相应 starter、改用供应商属性，并重新核对尺寸、音色 ID 和输出格式，不能只替换模型开关。

## 配置项

| 环境变量 | 对应属性 | 默认值 | 作用 |
|---|---|---|---|
| `OPENAI_API_KEY` | `spring.ai.openai.api-key` | 空 | OpenAI 凭据 |
| `OPENAI_BASE_URL` | `spring.ai.openai.base-url` | `https://api.openai.com` | OpenAI API 地址 |
| `SPRING_AI_CHAT_MODEL` | `spring.ai.model.chat` | `none` | 是否创建 ChatModel |
| `SPRING_AI_IMAGE_MODEL` | `spring.ai.model.image` | `none` | 是否创建 ImageModel |
| `SPRING_AI_TRANSCRIPTION_MODEL` | `spring.ai.model.audio.transcription` | `none` | 是否创建转写模型 |
| `SPRING_AI_SPEECH_MODEL` | `spring.ai.model.audio.speech` | `none` | 是否创建 TTS 模型 |
| `OPENAI_CHAT_MODEL` | `spring.ai.openai.chat.model` | `gpt-4o-mini` | 图片理解模型 |
| `OPENAI_IMAGE_MODEL` | `spring.ai.openai.image.model` | `gpt-image-1-mini` | 图片生成模型 |
| `OPENAI_TRANSCRIPTION_MODEL` | `spring.ai.openai.audio.transcription.model` | `gpt-transcribe` | 文件转写模型，输出配置为 JSON |
| `OPENAI_SPEECH_MODEL` | `spring.ai.openai.audio.speech.model` | `gpt-4o-mini-tts` | TTS 模型 |
| `OPENAI_SPEECH_VOICE` | `spring.ai.openai.audio.speech.voice` | `alloy` | 默认音色 |
| `OPENAI_SPEECH_FORMAT` | `spring.ai.openai.audio.speech.response-format` | `mp3` | 默认音频格式 |
| `OPENAI_SPEECH_SPEED` | `spring.ai.openai.audio.speech.speed` | `1.0` | 默认语速 |

Spring AI 2.0 已扁平化 Image、Audio 等模型属性，因此不要继续写 `.options`：

```properties
# 2.0 正确写法
spring.ai.openai.audio.speech.model=gpt-4o-mini-tts

# 旧写法，不应在新项目中使用
spring.ai.openai.audio.speech.options.model=gpt-4o-mini-tts
```

## API 一览

| 方法 | 路径 | 模型 | 返回值 |
|---|---|---|---|
| GET | `/api/multimodal/status` | 无 | 四类模型状态 |
| POST | `/api/multimodal/describe` | ChatModel | 图片说明 JSON |
| POST | `/api/images/generate` | ImageModel | URL/base64 图片列表 |
| POST | `/api/audio/transcriptions` | TranscriptionModel | 转写文本 JSON |
| POST | `/api/audio/speech` | TextToSpeechModel | 二进制音频 |
| GET | `/api/audio/speech/stream` | TextToSpeechModel | 流式二进制 chunk |
| POST | `/api/audio/speech/stream` | TextToSpeechModel | 推荐入口，正文传 text，避免文本出现在 URL |

完整请求位于 `requests.http`。图片和音频路径需要改为本机文件。

## 关键实现

### 图片作为 UserMessage 的 Media

```java
String answer = ChatClient.create(chatModel)
    .prompt()
    .user(user -> user
        .text(question)
        .media(mimeType, resource))
    .call()
    .content();
```

`Media` 只应挂在用户消息上。MIME 类型必须与内容一致，且具体模型支持哪些图片、音频或视频格式，仍以供应商页面为准。

原始字节在 Spring AI 2.0.0 中应使用 `Media.builder().mimeType(...).data(bytes).build()`；公开的双参数构造器只接受 `Resource` 或 `URI`。上传校验要求合法扩展名，允许缺失 MIME 或 `application/octet-stream` 根据扩展名推导，但会拒绝扩展名与声明 MIME 冲突。

### 可移植 ImageModel

```java
ImageOptions options = ImageOptionsBuilder.builder()
    .n(1)
    .width(1024)
    .height(1024)
    .build();

ImageResponse response = imageModel.call(new ImagePrompt(prompt, options));
```

每个 `ImageGeneration` 的 `Image` 可能包含 URL，也可能包含 `b64Json`。代码不能假定始终返回 URL。

### 语音转写

```java
AudioTranscriptionOptions options = null;
if (normalizedLanguage != null) {
    var builder = OpenAiAudioTranscriptionOptions.builder();
    if (transcriptionModel instanceof OpenAiAudioTranscriptionModel openAi) {
        builder.from(openAi.getOptions());
    }
    options = builder.language(normalizedLanguage).build();
}
```

统一接口只暴露可移植的基础契约；语言、时间戳等能力属于供应商选项。

实际 service 对 OpenAiAudioTranscriptionModel 先使用 builder.from(model.getOptions())，再覆盖语言；全新 Options 构造器会补入 whisper-1/text，不能用它无意覆盖已配置的 gpt-transcribe/JSON。

### TTS 与流式输出

```java
TextToSpeechOptions options = TextToSpeechOptions.builder()
    .voice("alloy")
    .format("wav")
    .speed(1.0)
    .build();

TextToSpeechResponse response = textToSpeechModel.call(new TextToSpeechPrompt(text, options));
byte[] audio = response.getResult().getOutput();
Flux<byte[]> chunks = textToSpeechModel.stream(text);
```

2.0 使用 `org.springframework.ai.audio.tts` 下的共享接口。旧的 `SpeechModel`、`SpeechPrompt`、`SpeechResponse` 已移除，`speed` 使用 `Double`，不要写 `1.0f`。

请求只覆盖 `voice` 或 `speed` 时，服务会与 `textToSpeechModel.getOptions()` 的公共字段合并，保留模型默认 `format`。OpenAI 的正式格式限制为 `mp3`、`opus`、`aac`、`flac`、`wav`、`pcm`；示例拒绝其他字符串，避免响应扩展名、Content-Type 和真实编码互相冲突。通用 Options 要合并到 `OpenAiAudioSpeechOptions` 时使用 `merge(TextToSpeechOptions)`，不能把公共接口变量传给只接受具体类型的 `from(...)`。

### 统一错误语义

| 场景 | HTTP 状态 |
|---|---|
| 空参数、非法尺寸、MIME/扩展名冲突、非法 TTS format | 400 |
| 对应模型未启用 | 503 |
| 供应商异常、模型返回 null/空文本/空图片/空音频/空流 | 502 |

错误响应只暴露稳定的中文业务消息，不把供应商原始异常内容返回给客户端。

## 安全与边界

- API Key 只能通过环境变量或外部密钥系统注入。
- 示例限制上传文件为 12 MB，并同时检查 MIME 类型和扩展名；`application/octet-stream` 只在扩展名合法时推导。生产环境还应做魔数检测、病毒扫描、租户配额和超时控制。
- 不直接信任远程媒体 URL。若允许 URL 输入，应防止 SSRF，并限制协议、主机、重定向和响应大小。
- `Media.name` 可能影响模型上下文，避免把不可信文件名直接作为提示的一部分。
- 图片生成和 TTS 返回的是二进制或 base64 数据，日志中不要输出完整内容。
- 模型支持的尺寸、图片数量、音色和格式并不完全相同；可移植 options 不能消除供应商限制。

## 测试说明

```powershell
mvn clean package
```

离线测试使用确定性假模型验证：

- 图片是否作为 `UserMessage.media` 传递；
- `Media.builder().data(byte[])` 和 `ImageOptions.style(...)` 的 2.0 发布契约；
- MIME/扩展名冲突拒绝及 octet-stream MIME 规范化；
- `ImageResponse` 的 URL/base64 两种分支；
- 转写语言参数和上传 Resource；
- OpenAI 正式 TTS/转写枚举与 `merge(...)`；
- TTS 默认 format 合并、非法格式、二进制响应、Flux chunk 和空流；
- 四类模型的空响应、供应商异常和中文 HTTP 502 映射；
- 四类模型均关闭时应用仍能启动；
- 无效参数与未启用模型时给出中文错误。

当前独立 clean package 为 21 个测试，全部通过。新增真实 SDK 的本机图像/转写 HTTP fixture，验证图像报文没有旧 style/response_format、转写保留模型和 JSON，以及 MVC 二进制流顺序/Content-Type/取消和 NaN 语速拒绝。fixture 只请求 127.0.0.1，使用测试假 Key 和合成响应，不验证媒体识别或生成质量，不消耗 OpenAI 额度。

HTTP 流通过 StreamingResponseBody 逐块写入并刷新，读取到结束，不把多段音频当成 JSON 或只取首块。输出失败会取消上游订阅；这不保证供应商立即停止生成或计费。响应已写出后，不能假定任何错误都能改成新的 502 JSON。

博客预约为 2026-11-19 09:00 +08:00，slug 为 spring-ai-multimodal-media。

真实模型调用需要有效 API Key、账户额度和供应商网络，本地默认测试不会产生外部费用。

## 参考

- [Spring AI v2.0.2 多模态页面](https://github.com/spring-projects/spring-ai/blob/v2.0.2/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/multimodality.adoc)
- [Spring AI Multimodality](https://docs.spring.io/spring-ai/reference/api/multimodality.html)
- [Spring AI Image Model API](https://docs.spring.io/spring-ai/reference/api/imageclient.html)
- [Spring AI Transcription API](https://docs.spring.io/spring-ai/reference/api/audio/transcriptions.html)
- [Spring AI Text-To-Speech API](https://docs.spring.io/spring-ai/reference/api/audio/speech.html)
- [OpenAI 图像生成 API 约束](https://developers.openai.com/api/reference/resources/images/methods/generate.md)
- [OpenAI GPT-Transcribe](https://developers.openai.com/api/docs/models/gpt-transcribe.md)
- [OpenAI Speech API 与停用说明](https://developers.openai.com/api/docs/guides/text-to-speech.md)
