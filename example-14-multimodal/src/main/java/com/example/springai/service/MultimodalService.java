package com.example.springai.service;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.example.springai.support.FeatureUnavailableException;
import com.example.springai.support.MultimodalOperationException;
import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

/**
 * 教程 14 的四条模型调用链。模型 Bean 都是可选的，便于在没有密钥时验证请求和
 * 响应契约。
 */
@Service
public class MultimodalService {

    private static final int MAX_TEXT_LENGTH = 4000;

    private static final long MAX_UPLOAD_BYTES = 12L * 1024 * 1024;

    private static final String DEFAULT_SPEECH_FORMAT = "mp3";

    private static final Set<String> SPEECH_FORMATS = Set.of("mp3", "opus", "aac", "flac", "wav", "pcm");

    private static final Map<String, MediaFileType> IMAGE_FILE_TYPES = Map.of(
            "png", mediaType("image/png", "image/png"),
            "jpg", mediaType("image/jpeg", "image/jpeg", "image/jpg"),
            "jpeg", mediaType("image/jpeg", "image/jpeg", "image/jpg"),
            "gif", mediaType("image/gif", "image/gif"),
            "webp", mediaType("image/webp", "image/webp"));

    private static final Map<String, MediaFileType> AUDIO_FILE_TYPES = Map.of(
            "mp3", mediaType("audio/mpeg", "audio/mpeg", "audio/mp3"),
            "mp4", mediaType("audio/mp4", "audio/mp4", "video/mp4"),
            "mpeg", mediaType("audio/mpeg", "audio/mpeg", "video/mpeg"),
            "mpga", mediaType("audio/mpeg", "audio/mpeg"),
            "m4a", mediaType("audio/mp4", "audio/mp4", "audio/x-m4a"),
            "wav", mediaType("audio/wav", "audio/wav", "audio/x-wav"),
            "webm", mediaType("audio/webm", "audio/webm", "video/webm"));

    private final ObjectProvider<ChatModel> chatModelProvider;

    private final ObjectProvider<ImageModel> imageModelProvider;

    private final ObjectProvider<TranscriptionModel> transcriptionModelProvider;

    private final ObjectProvider<TextToSpeechModel> textToSpeechModelProvider;

    public MultimodalService(ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<ImageModel> imageModelProvider,
            ObjectProvider<TranscriptionModel> transcriptionModelProvider,
            ObjectProvider<TextToSpeechModel> textToSpeechModelProvider) {
        this.chatModelProvider = chatModelProvider;
        this.imageModelProvider = imageModelProvider;
        this.transcriptionModelProvider = transcriptionModelProvider;
        this.textToSpeechModelProvider = textToSpeechModelProvider;
    }

    public ModelStatus status() {
        return new ModelStatus(
                this.chatModelProvider.getIfAvailable() != null,
                this.imageModelProvider.getIfAvailable() != null,
                this.transcriptionModelProvider.getIfAvailable() != null,
                this.textToSpeechModelProvider.getIfAvailable() != null);
    }

    /** 使用 ChatClient，把图片作为 UserMessage 的 Media 附件发送给模型。 */
    public MultimodalReply describeImage(MultipartFile file, String question) {
        validateText(question, "question");
        MimeType mimeType = validateImage(file);
        Resource resource = toResource(file);
        ChatModel chatModel = require(this.chatModelProvider, "图像理解", "SPRING_AI_CHAT_MODEL=openai");

        String answer = invokeModel("图像理解模型调用失败", () -> ChatClient.create(chatModel)
            .prompt()
            .user(user -> user.text(question.trim()).media(mimeType, resource))
            .call()
            .content());

        if (!StringUtils.hasText(answer)) {
            throw new MultimodalOperationException("图像理解模型没有返回可用文本");
        }

        return new MultimodalReply(file.getOriginalFilename(), mimeType.toString(), question.trim(),
                answer);
    }

    /** 调用统一 ImageModel，并保留 URL 与 base64 两种官方输出形式。 */
    public ImageGenerationReply generateImage(ImageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        validateText(request.prompt(), "prompt");
        validateImageOptions(request.n(), request.width(), request.height());

        ImageModel imageModel = require(this.imageModelProvider, "图片生成", "SPRING_AI_IMAGE_MODEL=openai");
        ImageOptions options = null;
        if (request.n() != null || request.width() != null || request.height() != null) {
            ImageOptionsBuilder optionsBuilder = ImageOptionsBuilder.builder();
            if (request.n() != null) {
                optionsBuilder.n(request.n());
            }
            if (request.width() != null) {
                optionsBuilder.width(request.width());
            }
            if (request.height() != null) {
                optionsBuilder.height(request.height());
            }
            options = optionsBuilder.build();
        }

        ImagePrompt prompt = options == null
                ? new ImagePrompt(request.prompt().trim())
                : new ImagePrompt(request.prompt().trim(), options);
        ImageResponse response = invokeModel("图片生成模型调用失败", () -> imageModel.call(prompt));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new MultimodalOperationException("图片生成模型没有返回图片");
        }
        List<ImageOutput> outputs = response.getResults().stream().map(this::toImageOutput).toList();
        return new ImageGenerationReply(outputs);
    }

    /** 使用统一 TranscriptionModel 进行同步语音转写。 */
    public TranscriptionReply transcribe(MultipartFile file, String language) {
        MimeType mimeType = validateAudio(file);
        Resource resource = toResource(file);
        TranscriptionModel transcriptionModel = require(this.transcriptionModelProvider, "语音转写",
                "SPRING_AI_TRANSCRIPTION_MODEL=openai");

        String normalizedLanguage = normalizeOptional(language);
        AudioTranscriptionOptions options = null;
        if (normalizedLanguage != null) {
            // Options 的构造器会填默认 model/format，不能用全新 Options 覆盖已配置模型。
            var builder = OpenAiAudioTranscriptionOptions.builder();
            if (transcriptionModel instanceof OpenAiAudioTranscriptionModel openAi) {
                builder.from(openAi.getOptions());
            }
            options = builder.language(normalizedLanguage).build();
        }
        AudioTranscriptionOptions requestOptions = options;
        String text = invokeModel("语音转写模型调用失败", () -> transcriptionModel.transcribe(resource, requestOptions));
        if (!StringUtils.hasText(text)) {
            throw new MultimodalOperationException("语音转写模型没有返回可用文本");
        }
        return new TranscriptionReply(file.getOriginalFilename(), mimeType.toString(), text);
    }

    /** 使用可移植 TextToSpeechOptions；voice、format、speed 由具体供应商解释。 */
    public SpeechAudio synthesize(SpeechRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        validateText(request.text(), "text");
        validateSpeed(request.speed());
        String voice = normalizeOptional(request.voice());
        String format = normalizeSpeechFormat(request.format());
        TextToSpeechModel model = require(this.textToSpeechModelProvider, "文字转语音",
                "SPRING_AI_SPEECH_MODEL=openai");

        TextToSpeechOptions defaults = invokeModel("读取文字转语音模型配置失败", model::getOptions);
        TextToSpeechOptions options = mergeSpeechOptions(defaults, voice, format, request.speed());
        TextToSpeechResponse response = invokeModel("文字转语音模型调用失败",
                () -> model.call(new TextToSpeechPrompt(request.text().trim(), options)));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()
                || response.getResult() == null) {
            throw new MultimodalOperationException("文字转语音模型没有返回音频");
        }
        byte[] audio = response.getResult().getOutput();
        if (audio == null || audio.length == 0) {
            throw new MultimodalOperationException("文字转语音模型返回了空音频");
        }
        String actualFormat = options.getFormat();
        return new SpeechAudio(audio, actualFormat, audioContentType(actualFormat));
    }

    /** 使用统一 TTS 流式契约输出二进制块；实际分块粒度由供应商实现决定。 */
    public Flux<byte[]> streamSpeech(String text) {
        validateText(text, "text");
        TextToSpeechModel model = require(this.textToSpeechModelProvider, "文字转语音",
                "SPRING_AI_SPEECH_MODEL=openai");
        Flux<byte[]> stream;
        try {
            stream = model.stream(text.trim());
        }
        catch (RuntimeException exception) {
            throw operationFailure("文字转语音流式调用失败", exception);
        }
        if (stream == null) {
            throw new MultimodalOperationException("文字转语音流式模型没有返回音频流");
        }
        return stream.<byte[]>handle((bytes, sink) -> {
            if (bytes == null || bytes.length == 0) {
                sink.error(new MultimodalOperationException("文字转语音流式模型返回了空音频块"));
            }
            else {
                sink.next(bytes);
            }
        })
            .switchIfEmpty(Flux.error(new MultimodalOperationException("文字转语音流式模型没有返回音频")))
            .onErrorMap(exception -> operationFailure("文字转语音流式调用失败", exception));
    }

    private ImageOutput toImageOutput(ImageGeneration generation) {
        if (generation == null || generation.getOutput() == null) {
            throw new MultimodalOperationException("图片生成模型返回了无效图片结果");
        }
        Image image = generation.getOutput();
        if (!StringUtils.hasText(image.getUrl()) && !StringUtils.hasText(image.getB64Json())) {
            throw new MultimodalOperationException("图片生成模型返回的图片缺少 URL 或 base64 数据");
        }
        return new ImageOutput(image.getUrl(), image.getB64Json());
    }

    private MimeType validateImage(MultipartFile file) {
        return validateMedia(file, "图片", IMAGE_FILE_TYPES, "仅支持 PNG、JPEG、GIF 或 WebP 图片");
    }

    private MimeType validateAudio(MultipartFile file) {
        return validateMedia(file, "音频", AUDIO_FILE_TYPES,
                "仅支持 mp3、mp4、mpeg、mpga、m4a、wav 或 webm 音频");
    }

    private void validateUpload(MultipartFile file, String description) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(description + "文件不能为空");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException(description + "文件不能超过 12 MB");
        }
    }

    private MimeType validateMedia(MultipartFile file, String description,
            Map<String, MediaFileType> supportedTypes, String unsupportedMessage) {
        validateUpload(file, description);
        String extension = normalizedExtension(file);
        MediaFileType fileType = supportedTypes.get(extension);
        if (fileType == null) {
            throw new IllegalArgumentException(unsupportedMessage);
        }

        MimeType declaredMimeType = resolveDeclaredMimeType(file);
        if (declaredMimeType == null || MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(mimeValue(declaredMimeType))) {
            return fileType.canonicalMimeType();
        }
        if (!fileType.acceptedMimeTypes().contains(mimeValue(declaredMimeType))) {
            throw new IllegalArgumentException(description + "扩展名 ." + extension + " 与 MIME 类型 "
                    + declaredMimeType + " 不一致");
        }
        return fileType.canonicalMimeType();
    }

    private MimeType resolveDeclaredMimeType(MultipartFile file) {
        if (!StringUtils.hasText(file.getContentType())) {
            return null;
        }
        try {
            return MimeTypeUtils.parseMimeType(file.getContentType());
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("文件 MIME 类型无效", exception);
        }
    }

    private String normalizedExtension(MultipartFile file) {
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (!StringUtils.hasText(extension)) {
            throw new IllegalArgumentException("文件名必须包含受支持的扩展名");
        }
        return extension.toLowerCase(Locale.ROOT);
    }

    private String mimeValue(MimeType mimeType) {
        return (mimeType.getType() + "/" + mimeType.getSubtype()).toLowerCase(Locale.ROOT);
    }

    private Resource toResource(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String safeFilename = "upload." + normalizedExtension(file).replaceAll("[^a-z0-9]", "");
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return safeFilename;
                }
            };
        }
        catch (IOException exception) {
            throw new IllegalArgumentException("读取上传文件失败", exception);
        }
    }

    private void validateImageOptions(Integer n, Integer width, Integer height) {
        if (n != null && (n < 1 || n > 10)) {
            throw new IllegalArgumentException("n 必须在 1 到 10 之间");
        }
        if ((width == null) != (height == null)) {
            throw new IllegalArgumentException("width 和 height 必须同时提供");
        }
        if (width != null && (width < 1 || height < 1)) {
            throw new IllegalArgumentException("图片宽高必须为正数");
        }
    }

    private void validateSpeed(Double speed) {
        if (speed != null && (!Double.isFinite(speed) || speed < 0.25 || speed > 4.0)) {
            throw new IllegalArgumentException("speed 必须在 0.25 到 4.0 之间");
        }
    }

    private String normalizeSpeechFormat(String format) {
        String normalized = normalizeOptional(format);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!SPEECH_FORMATS.contains(normalized)) {
            throw new IllegalArgumentException("format 仅支持 mp3、opus、aac、flac、wav 或 pcm");
        }
        return normalized;
    }

    private TextToSpeechOptions mergeSpeechOptions(TextToSpeechOptions defaults,
            String requestVoice, String requestFormat, Double requestSpeed) {
        TextToSpeechOptions.Builder builder = TextToSpeechOptions.builder();
        String model = defaults == null ? null : normalizeOptional(defaults.getModel());
        String voice = requestVoice != null
                ? requestVoice
                : defaults == null ? null : normalizeOptional(defaults.getVoice());
        String format = requestFormat != null
                ? requestFormat
                : defaults == null ? null : normalizeOptional(defaults.getFormat());
        Double speed = requestSpeed != null ? requestSpeed : defaults == null ? null : defaults.getSpeed();

        if (model != null) {
            builder.model(model);
        }
        if (voice != null) {
            builder.voice(voice);
        }
        builder.format(validateEffectiveSpeechFormat(format));
        if (speed != null) {
            builder.speed(speed);
        }
        return builder.build();
    }

    private String validateEffectiveSpeechFormat(String format) {
        String normalized = format == null ? DEFAULT_SPEECH_FORMAT : format.toLowerCase(Locale.ROOT);
        if (!SPEECH_FORMATS.contains(normalized)) {
            throw new MultimodalOperationException("文字转语音模型配置了不受支持的音频格式：" + format);
        }
        return normalized;
    }

    private void validateText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        if (value.trim().length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + MAX_TEXT_LENGTH + " 个字符");
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String audioContentType(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "wav" -> AudioMimeTypes.WAV;
            case "opus" -> AudioMimeTypes.OPUS;
            case "aac" -> AudioMimeTypes.AAC;
            case "flac" -> AudioMimeTypes.FLAC;
            case "pcm" -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
            default -> AudioMimeTypes.MP3;
        };
    }

    private <T> T invokeModel(String message, Supplier<T> invocation) {
        try {
            return invocation.get();
        }
        catch (RuntimeException exception) {
            throw operationFailure(message, exception);
        }
    }

    private RuntimeException operationFailure(String message, Throwable exception) {
        if (exception instanceof MultimodalOperationException operationException) {
            return operationException;
        }
        return new MultimodalOperationException(message, exception);
    }

    private <T> T require(ObjectProvider<T> provider, String feature, String switchHint) {
        T model = provider.getIfAvailable();
        if (model == null) {
            throw new FeatureUnavailableException("尚未启用" + feature + "模型。请设置 " + switchHint
                    + " 并提供对应 API Key 后重启应用。");
        }
        return model;
    }

    private static MediaFileType mediaType(String canonicalMimeType, String... acceptedMimeTypes) {
        return new MediaFileType(MimeTypeUtils.parseMimeType(canonicalMimeType), Set.of(acceptedMimeTypes));
    }

    public record ModelStatus(boolean chatEnabled, boolean imageEnabled,
            boolean transcriptionEnabled, boolean speechEnabled) {
    }

    public record MultimodalReply(String filename, String mimeType, String question, String answer) {
    }

    public record ImageRequest(String prompt, Integer n, Integer width, Integer height) {
    }

    public record ImageGenerationReply(List<ImageOutput> images) {
    }

    public record ImageOutput(String url, String base64) {
    }

    public record TranscriptionReply(String filename, String mimeType, String text) {
    }

    public record SpeechRequest(String text, String voice, String format, Double speed) {
    }

    public record SpeechAudio(byte[] audio, String format, String contentType) {
    }

    private record MediaFileType(MimeType canonicalMimeType, Set<String> acceptedMimeTypes) {
    }

    /** 避免在代码示例中散落字符串 MIME 类型，同时不依赖已移除的旧 Speech API。 */
    private static final class AudioMimeTypes {

        private static final String MP3 = "audio/mpeg";

        private static final String WAV = "audio/wav";

        private static final String OPUS = "audio/opus";

        private static final String AAC = "audio/aac";

        private static final String FLAC = "audio/flac";

        private AudioMimeTypes() {
        }

    }

}
