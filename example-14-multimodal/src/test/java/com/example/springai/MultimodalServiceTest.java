package com.example.springai;

import java.util.List;

import com.example.springai.service.MultimodalService;
import com.example.springai.service.MultimodalService.ImageGenerationReply;
import com.example.springai.service.MultimodalService.ImageRequest;
import com.example.springai.service.MultimodalService.MultimodalReply;
import com.example.springai.service.MultimodalService.SpeechAudio;
import com.example.springai.service.MultimodalService.SpeechRequest;
import com.example.springai.service.MultimodalService.TranscriptionReply;
import com.example.springai.support.FeatureUnavailableException;
import com.example.springai.support.MultimodalOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultimodalServiceTest {

    @Test
    void nonFiniteSpeechSpeedIsRejectedBeforeResolvingAModel() {
        MultimodalService value = service(null, null, null, null);
        assertThatThrownBy(() -> value.synthesize(new SpeechRequest("文本", null, null, Double.NaN)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("speed");
    }

    @Test
    void imageDescriptionUsesUserMediaAndQuestion() {
        RecordingChatModel chatModel = new RecordingChatModel("这是一张测试图片");
        MultimodalService service = service(chatModel, null, null, null);

        MockMultipartFile image = new MockMultipartFile("file", "photo.png", "image/png", new byte[] { 1, 2, 3 });
        MultimodalReply reply = service.describeImage(image, "请描述图片");

        assertThat(reply.answer()).isEqualTo("这是一张测试图片");
        assertThat(chatModel.lastPrompt.getUserMessage().getText()).isEqualTo("请描述图片");
        assertThat(chatModel.lastPrompt.getUserMessage().getMedia()).hasSize(1);
        assertThat(chatModel.lastPrompt.getUserMessage().getMedia().get(0).getMimeType())
            .isEqualTo(MimeTypeUtils.IMAGE_PNG);
        assertThat(chatModel.lastPrompt.getUserMessage().getMedia().get(0).getDataAsByteArray())
            .containsExactly(1, 2, 3);
    }

    @Test
    void imageGenerationMapsUrlAndBase64Outputs() {
        RecordingImageModel imageModel = new RecordingImageModel();
        MultimodalService service = service(null, imageModel, null, null);

        ImageGenerationReply reply = service.generateImage(new ImageRequest("一只戴眼镜的猫", 2, 512, 512));

        assertThat(reply.images()).extracting("url", "base64")
            .containsExactly(
                    org.assertj.core.groups.Tuple.tuple("https://example.test/image.png", null),
                    org.assertj.core.groups.Tuple.tuple(null, "encoded-image"));
        assertThat(imageModel.lastPrompt.getOptions().getN()).isEqualTo(2);
        assertThat(imageModel.lastPrompt.getOptions().getWidth()).isEqualTo(512);
        assertThat(imageModel.lastPrompt.getOptions().getHeight()).isEqualTo(512);
    }

    @Test
    void transcriptionUsesResourceAndOptionalLanguage() {
        RecordingTranscriptionModel transcriptionModel = new RecordingTranscriptionModel();
        MultimodalService service = service(null, null, transcriptionModel, null);

        MockMultipartFile audio = new MockMultipartFile("file", "speech.mp3", "audio/mpeg", new byte[] { 9, 8 });
        TranscriptionReply reply = service.transcribe(audio, "zh");

        assertThat(reply.text()).isEqualTo("这是测试转写");
        assertThat(transcriptionModel.lastPrompt.getInstructions().getFilename()).isEqualTo("upload.mp3");
        assertThat(transcriptionModel.lastPrompt.getOptions()).isInstanceOf(
                org.springframework.ai.openai.OpenAiAudioTranscriptionOptions.class);
        org.springframework.ai.openai.OpenAiAudioTranscriptionOptions options =
                (org.springframework.ai.openai.OpenAiAudioTranscriptionOptions) transcriptionModel.lastPrompt.getOptions();
        assertThat(options.getLanguage()).isEqualTo("zh");
    }

    @Test
    void speechUsesPortableOptionsAndStreamsBytes() {
        RecordingSpeechModel speechModel = new RecordingSpeechModel();
        MultimodalService service = service(null, null, null, speechModel);

        SpeechAudio audio = service.synthesize(new SpeechRequest("你好，Spring AI", "nova", "wav", 0.8));
        assertThat(audio.audio()).containsExactly(4, 5, 6);
        assertThat(audio.format()).isEqualTo("wav");
        assertThat(audio.contentType()).isEqualTo("audio/wav");
        assertThat(speechModel.lastPrompt.getOptions().getVoice()).isEqualTo("nova");
        assertThat(speechModel.lastPrompt.getOptions().getFormat()).isEqualTo("wav");
        assertThat(speechModel.lastPrompt.getOptions().getSpeed()).isEqualTo(0.8);

        StepVerifier.create(service.streamSpeech("流式语音"))
            .assertNext(bytes -> assertThat(bytes).containsExactly(4, 5, 6))
            .verifyComplete();
    }

    @Test
    void speechStreamPreservesEveryChunkInOrder() {
        TextToSpeechModel model = new TextToSpeechModel() {
            @Override
            public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
                return new TextToSpeechResponse(List.of(new Speech(new byte[] { 1, 2, 3, 4 })));
            }

            @Override
            public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
                return Flux.just(
                        new TextToSpeechResponse(List.of(new Speech(new byte[] { 1, 2 }))),
                        new TextToSpeechResponse(List.of(new Speech(new byte[] { 3, 4 }))));
            }
        };

        StepVerifier.create(service(null, null, null, model).streamSpeech("多块音频"))
                .assertNext(bytes -> assertThat(bytes).containsExactly(1, 2))
                .assertNext(bytes -> assertThat(bytes).containsExactly(3, 4))
                .verifyComplete();
    }

    @Test
    void transcriptionWithoutStreamingOverrideFailsExplicitly() {
        StepVerifier.create(new RecordingTranscriptionModel()
                .stream(new AudioTranscriptionPrompt(new org.springframework.core.io.ByteArrayResource(
                        new byte[] { 1, 2 }))))
                .expectError(UnsupportedOperationException.class)
                .verify();
    }

    @Test
    void invalidUploadAndUnavailableModelReturnActionableErrors() {
        MultimodalService service = service(null, null, null, null);
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.describeImage(empty, "看图"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("图片文件不能为空");
        assertThatThrownBy(() -> service.generateImage(new ImageRequest("测试", null, null, null)))
            .isInstanceOf(FeatureUnavailableException.class)
            .hasMessageContaining("图片生成模型");
    }

    @Test
    void uploadValidationRejectsMimeConflictsAndNormalizesOctetStream() {
        MultimodalService noModelService = service(null, null, null, null);
        MockMultipartFile conflictingImage = new MockMultipartFile(
                "file", "photo.png", "image/jpeg", new byte[] { 1 });
        MockMultipartFile conflictingAudio = new MockMultipartFile(
                "file", "speech.mp3", "audio/wav", new byte[] { 2 });

        assertThatThrownBy(() -> noModelService.describeImage(conflictingImage, "看图"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("扩展名 .png 与 MIME 类型 image/jpeg 不一致");
        assertThatThrownBy(() -> noModelService.transcribe(conflictingAudio, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("扩展名 .mp3 与 MIME 类型 audio/wav 不一致");

        RecordingTranscriptionModel transcriptionModel = new RecordingTranscriptionModel();
        MultimodalService service = service(null, null, transcriptionModel, null);
        MockMultipartFile inferredAudio = new MockMultipartFile(
                "file", "speech.wav", "application/octet-stream", new byte[] { 3 });

        TranscriptionReply reply = service.transcribe(inferredAudio, null);

        assertThat(reply.mimeType()).isEqualTo("audio/wav");
        assertThat(transcriptionModel.lastPrompt.getInstructions().getFilename()).isEqualTo("upload.wav");
    }

    @Test
    void speechRequestOverridesOnlySpecifiedFieldsAndKeepsModelDefaults() {
        TextToSpeechOptions defaults = TextToSpeechOptions.builder()
            .model("tts-test")
            .voice("alloy")
            .format("wav")
            .speed(1.2)
            .build();
        RecordingSpeechModel speechModel = new RecordingSpeechModel(defaults);
        MultimodalService service = service(null, null, null, speechModel);

        SpeechAudio audio = service.synthesize(new SpeechRequest("保留默认格式", "nova", null, null));

        assertThat(audio.format()).isEqualTo("wav");
        assertThat(audio.contentType()).isEqualTo("audio/wav");
        assertThat(speechModel.lastPrompt.getOptions().getModel()).isEqualTo("tts-test");
        assertThat(speechModel.lastPrompt.getOptions().getVoice()).isEqualTo("nova");
        assertThat(speechModel.lastPrompt.getOptions().getFormat()).isEqualTo("wav");
        assertThat(speechModel.lastPrompt.getOptions().getSpeed()).isEqualTo(1.2);
    }

    @Test
    void speechRejectsUnsupportedResponseFormat() {
        MultimodalService service = service(null, null, null, new RecordingSpeechModel());

        assertThatThrownBy(() -> service.synthesize(new SpeechRequest("测试", null, "ogg", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("format 仅支持 mp3、opus、aac、flac、wav 或 pcm");
    }

    @Test
    void emptyModelOutputsBecomeOperationErrors() {
        ChatModel emptyChatModel = prompt -> new ChatResponse(List.of());
        ImageModel emptyImageModel = prompt -> new ImageResponse(List.of());
        TranscriptionModel emptyTranscriptionModel = prompt ->
                new AudioTranscriptionResponse(new AudioTranscription(" "));
        TextToSpeechModel emptySpeechModel = new EmptySpeechModel();

        MockMultipartFile image = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[] { 1 });
        MockMultipartFile audio = new MockMultipartFile(
                "file", "speech.mp3", "audio/mpeg", new byte[] { 2 });

        assertThatThrownBy(() -> service(emptyChatModel, null, null, null).describeImage(image, "看图"))
            .isInstanceOf(MultimodalOperationException.class)
            .hasMessageContaining("图像理解模型");
        assertThatThrownBy(() -> service(null, emptyImageModel, null, null)
            .generateImage(new ImageRequest("画图", null, null, null)))
            .isInstanceOf(MultimodalOperationException.class)
            .hasMessage("图片生成模型没有返回图片");
        assertThatThrownBy(() -> service(null, null, emptyTranscriptionModel, null).transcribe(audio, null))
            .isInstanceOf(MultimodalOperationException.class)
            .hasMessage("语音转写模型没有返回可用文本");
        assertThatThrownBy(() -> service(null, null, null, emptySpeechModel)
            .synthesize(new SpeechRequest("生成语音", null, null, null)))
            .isInstanceOf(MultimodalOperationException.class)
            .hasMessage("文字转语音模型没有返回音频");
    }

    @Test
    void providerFailuresBecomeOperationErrorsWithoutLeakingProviderMessage() {
        RuntimeException providerFailure = new RuntimeException("供应商原始敏感错误");
        ChatModel failingChatModel = prompt -> {
            throw providerFailure;
        };
        ImageModel failingImageModel = prompt -> {
            throw providerFailure;
        };
        TranscriptionModel failingTranscriptionModel = prompt -> {
            throw providerFailure;
        };
        TextToSpeechModel failingSpeechModel = new FailingSpeechModel(providerFailure);
        MockMultipartFile image = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[] { 1 });
        MockMultipartFile audio = new MockMultipartFile(
                "file", "speech.mp3", "audio/mpeg", new byte[] { 2 });

        assertOperationFailure(() -> service(failingChatModel, null, null, null).describeImage(image, "看图"),
                "图像理解模型调用失败", providerFailure);
        assertOperationFailure(() -> service(null, failingImageModel, null, null)
            .generateImage(new ImageRequest("画图", null, null, null)), "图片生成模型调用失败", providerFailure);
        assertOperationFailure(() -> service(null, null, failingTranscriptionModel, null).transcribe(audio, null),
                "语音转写模型调用失败", providerFailure);
        assertOperationFailure(() -> service(null, null, null, failingSpeechModel)
            .synthesize(new SpeechRequest("生成语音", null, null, null)), "文字转语音模型调用失败", providerFailure);
    }

    @Test
    void emptyOrFailedSpeechStreamBecomesOperationError() {
        MultimodalService emptyService = service(null, null, null, new EmptySpeechModel());
        RuntimeException providerFailure = new RuntimeException("流式供应商错误");
        MultimodalService failingService = service(null, null, null, new FailingSpeechModel(providerFailure));

        StepVerifier.create(emptyService.streamSpeech("空流"))
            .expectErrorSatisfies(exception -> assertThat(exception)
                .isInstanceOf(MultimodalOperationException.class)
                .hasMessage("文字转语音流式模型没有返回音频"))
            .verify();
        StepVerifier.create(failingService.streamSpeech("失败流"))
            .expectErrorSatisfies(exception -> assertThat(exception)
                .isInstanceOf(MultimodalOperationException.class)
                .hasMessage("文字转语音流式调用失败")
                .hasCause(providerFailure))
            .verify();
    }

    @Test
    void officialSpringAi2ApiContractsCompileAndKeepExpectedValues() {
        Media media = Media.builder()
            .mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(new byte[] { 1, 2, 3 })
            .build();
        ImageOptions imageOptions = ImageOptionsBuilder.builder().style("vivid").build();
        OpenAiAudioSpeechOptions speechOptions = OpenAiAudioSpeechOptions.builder()
            .voice(OpenAiAudioSpeechOptions.Voice.NOVA)
            .responseFormat(OpenAiAudioSpeechOptions.AudioResponseFormat.WAV)
            .build();
        OpenAiAudioSpeechOptions mergedSpeechOptions = OpenAiAudioSpeechOptions.builder()
            .merge(TextToSpeechOptions.builder().format("flac").build())
            .build();
        OpenAiAudioTranscriptionOptions transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
            .responseFormat(com.openai.models.audio.AudioResponseFormat.TEXT)
            .build();

        assertThat(media.getDataAsByteArray()).containsExactly(1, 2, 3);
        assertThat(imageOptions.getStyle()).isEqualTo("vivid");
        assertThat(speechOptions.getVoice()).isEqualTo("nova");
        assertThat(speechOptions.getResponseFormat()).isEqualTo("wav");
        assertThat(mergedSpeechOptions.getFormat()).isEqualTo("flac");
        assertThat(transcriptionOptions.getResponseFormat())
            .isEqualTo(com.openai.models.audio.AudioResponseFormat.TEXT);
    }

    private void assertOperationFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable invocation,
            String expectedMessage, RuntimeException expectedCause) {
        assertThatThrownBy(invocation)
            .isInstanceOf(MultimodalOperationException.class)
            .hasMessage(expectedMessage)
            .hasCause(expectedCause)
            .hasMessageNotContaining("敏感错误");
    }

    private MultimodalService service(ChatModel chatModel, ImageModel imageModel,
            TranscriptionModel transcriptionModel, TextToSpeechModel speechModel) {
        return new MultimodalService(
                provider(ChatModel.class, chatModel),
                provider(ImageModel.class, imageModel),
                provider(TranscriptionModel.class, transcriptionModel),
                provider(TextToSpeechModel.class, speechModel));
    }

    private <T> ObjectProvider<T> provider(Class<T> type, T value) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (value != null) {
            beanFactory.registerSingleton(type.getName(), value);
        }
        return beanFactory.getBeanProvider(type);
    }

    private static final class RecordingChatModel implements ChatModel {

        private final String answer;

        private Prompt lastPrompt;

        private RecordingChatModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(this.answer))));
        }

    }

    private static final class RecordingImageModel implements ImageModel {

        private ImagePrompt lastPrompt;

        @Override
        public ImageResponse call(ImagePrompt request) {
            this.lastPrompt = request;
            return new ImageResponse(List.of(
                    new ImageGeneration(new Image("https://example.test/image.png", null)),
                    new ImageGeneration(new Image(null, "encoded-image"))));
        }

    }

    private static final class RecordingTranscriptionModel implements TranscriptionModel {

        private AudioTranscriptionPrompt lastPrompt;

        @Override
        public AudioTranscriptionResponse call(AudioTranscriptionPrompt transcriptionPrompt) {
            this.lastPrompt = transcriptionPrompt;
            return new AudioTranscriptionResponse(new AudioTranscription("这是测试转写"));
        }

    }

    private static final class RecordingSpeechModel implements TextToSpeechModel {

        private TextToSpeechPrompt lastPrompt;

        private final TextToSpeechOptions options;

        private RecordingSpeechModel() {
            this(TextToSpeechOptions.builder().build());
        }

        private RecordingSpeechModel(TextToSpeechOptions options) {
            this.options = options;
        }

        @Override
        public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
            this.lastPrompt = prompt;
            return new TextToSpeechResponse(List.of(new Speech(new byte[] { 4, 5, 6 })));
        }

        @Override
        public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
            this.lastPrompt = prompt;
            return Flux.just(new TextToSpeechResponse(List.of(new Speech(new byte[] { 4, 5, 6 }))));
        }

        @Override
        public TextToSpeechOptions getOptions() {
            return this.options;
        }

    }

    private static final class EmptySpeechModel implements TextToSpeechModel {

        @Override
        public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
            return new TextToSpeechResponse(List.of());
        }

        @Override
        public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
            return Flux.empty();
        }

    }

    private static final class FailingSpeechModel implements TextToSpeechModel {

        private final RuntimeException failure;

        private FailingSpeechModel(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
            throw this.failure;
        }

        @Override
        public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
            return Flux.error(this.failure);
        }

    }

}
