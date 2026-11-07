package com.example.springai.service;

import java.util.Base64;
import java.util.List;
import java.util.Locale;

import com.example.springai.model.ContentModels.ContentPlan;
import com.example.springai.support.ContentStudioException;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.stereotype.Service;

@Service
public class ContentStudioService {

    private static final int MAX_TOPIC_LENGTH = 500;

    private final ChatClient chatClient;

    private final ImageModel imageModel;

    private final TextToSpeechModel speechModel;
    private final boolean offline;

    public ContentStudioService(ChatModel chatModel, ImageModel imageModel, TextToSpeechModel speechModel) {
        this.chatClient = ChatClient.builder(chatModel)
            .defaultSystem("你是内容策划助手。事实不确定时明确标注；不要模仿在世艺术家，不生成商标、个人隐私或危险内容。")
            .build();
        this.imageModel = imageModel;
        this.speechModel = speechModel;
        this.offline = chatModel instanceof com.example.springai.model.OfflineContentChatModel
            && imageModel instanceof com.example.springai.model.OfflineImageModel
            && speechModel instanceof com.example.springai.model.OfflineSpeechModel;
    }

    public ContentPackage create(ContentRequest request) {
        ValidatedRequest validated = validate(request);
        try {
            ContentPlan plan = this.chatClient.prompt()
                .user("围绕主题生成文章、图片提示词和适合口播的摘要。主题：" + validated.topic()
                        + "；受众：" + validated.audience() + "；风格：" + validated.style())
                .options(ChatOptions.builder().maxTokens(2000))
                .call()
                .entity(ContentPlan.class, spec -> spec.validateSchema());
            validatePlan(plan);

            ImageResponse imageResponse = this.imageModel.call(new ImagePrompt(plan.imagePrompt()));
            Image image = requireImage(imageResponse);
            TextToSpeechResponse speechResponse = this.speechModel.call(new TextToSpeechPrompt(plan.speechText()));
            byte[] audio = requireAudio(speechResponse);

            return new ContentPackage(plan.title(), plan.article(), plan.imagePrompt(),
                    isBlank(image.getB64Json()) ? image.getUrl() : null, image.getB64Json(),
                    Base64.getEncoder().encodeToString(audio), "audio/wav", plan.speechText(),
                    this.offline ? List.of("离线媒体为一像素图片与半秒静音，不是生成效果", "发布前必须完成事实、版权和安全审核")
                        : List.of("发布前必须完成事实、版权和安全审核"),
                    this.offline ? "contract-demo" : "model-generated");
        }
        catch (ContentStudioException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new ContentStudioException("文案、图片或语音生成失败", exception);
        }
    }

    private ValidatedRequest validate(ContentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String topic = required(request.topic(), "topic", MAX_TOPIC_LENGTH);
        String audience = required(request.audience(), "audience", 80);
        String style = required(request.style(), "style", 80).toLowerCase(Locale.ROOT);
        if (!List.of("professional", "friendly", "concise", "story").contains(style)) {
            throw new IllegalArgumentException("style 仅支持 professional、friendly、concise 或 story");
        }
        return new ValidatedRequest(topic, audience, style);
    }

    private void validatePlan(ContentPlan plan) {
        if (plan == null || isBlank(plan.title()) || isBlank(plan.article())
                || isBlank(plan.imagePrompt()) || isBlank(plan.speechText())) {
            throw new ContentStudioException("文案模型没有返回完整内容方案");
        }
        if (plan.title().length() > 200 || plan.article().length() > 12000
                || plan.imagePrompt().length() > 2000 || plan.speechText().length() > 5000) {
            throw new ContentStudioException("内容方案超过标题、文章或媒体输入上限");
        }
    }

    private Image requireImage(ImageResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new ContentStudioException("图片模型没有返回图片");
        }
        Image image = response.getResult().getOutput();
        if (isBlank(image.getUrl()) && isBlank(image.getB64Json())) {
            throw new ContentStudioException("图片模型返回结果缺少 URL 或 base64 数据");
        }
        if (!isBlank(image.getB64Json())) {
            try {
                if (Base64.getDecoder().decode(image.getB64Json()).length > 10 * 1024 * 1024) {
                    throw new ContentStudioException("图片超过 10 MiB 上限");
                }
            }
            catch (IllegalArgumentException exception) { throw new ContentStudioException("图片 base64 格式无效", exception); }
        }
        return image;
    }

    private byte[] requireAudio(TextToSpeechResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                || response.getResult().getOutput().length == 0) {
            throw new ContentStudioException("文字转语音模型没有返回音频");
        }
        byte[] audio = response.getResult().getOutput();
        if (audio.length < 44 || audio.length > 10 * 1024 * 1024
                || !new String(audio, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                || !new String(audio, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WAVE")) {
            throw new ContentStudioException("音频不是有效长度的 WAV 数据");
        }
        return audio;
    }

    private String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ContentRequest(String topic, String audience, String style) {
    }

    public record ContentPackage(String title, String article, String imagePrompt,
            String imageUrl, String imageBase64, String audioBase64, String audioContentType,
            String speechText, List<String> warnings, String mode) {
    }

    private record ValidatedRequest(String topic, String audience, String style) {
    }

}
