package com.example.springai;

import java.util.Base64;
import java.util.List;

import com.example.springai.model.OfflineContentChatModel;
import com.example.springai.model.OfflineImageModel;
import com.example.springai.model.OfflineSpeechModel;
import com.example.springai.service.ContentStudioService;
import com.example.springai.service.ContentStudioService.ContentRequest;
import com.example.springai.support.ContentStudioException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageResponse;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentStudioServiceTest {

    @Test
    void createsTextImageAndSpeechPackageOffline() throws Exception {
        var service = service(new OfflineImageModel(), new OfflineSpeechModel());

        var result = service.create(new ContentRequest(
                "Spring AI 多模态", "Java 开发者", "professional"));

        assertThat(result.title()).contains("Spring AI");
        assertThat(result.article()).contains("ChatModel", "ImageModel", "TextToSpeechModel");
        assertThat(result.imageBase64()).isNotBlank();
        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(result.imageBase64())));
        assertThat(image.getWidth()).isEqualTo(1);
        try (var audio = javax.sound.sampled.AudioSystem.getAudioInputStream(
            new java.io.ByteArrayInputStream(Base64.getDecoder().decode(result.audioBase64())))) {
            assertThat(audio.getFormat().getSampleRate()).isEqualTo(8000f);
            assertThat(audio.getFrameLength()).isEqualTo(4000);
        }
        assertThat(result.mode()).isEqualTo("contract-demo");
        assertThat(result.warnings()).hasSize(2);
    }

    @Test
    void invalidStyleAndEmptyTopicAreRejected() {
        var service = service(new OfflineImageModel(), new OfflineSpeechModel());

        assertThatThrownBy(() -> service.create(new ContentRequest("主题", "开发者", "copy-artist")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("style 仅支持");
        assertThatThrownBy(() -> service.create(new ContentRequest(" ", "开发者", "friendly")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("topic 不能为空");
    }

    @Test
    void emptyImageResponseIsRejected() {
        ImageModel emptyImage = prompt -> new ImageResponse(List.of());

        assertThatThrownBy(() -> service(emptyImage, new OfflineSpeechModel())
            .create(new ContentRequest("主题", "开发者", "friendly")))
            .isInstanceOf(ContentStudioException.class)
            .hasMessage("图片模型没有返回图片");
    }

    @Test
    void emptySpeechResponseIsRejected() {
        TextToSpeechModel emptySpeech = new TextToSpeechModel() {
            @Override
            public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
                return new TextToSpeechResponse(List.of(new Speech(new byte[0])));
            }

            @Override
            public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
                return Flux.just(call(prompt));
            }
        };

        assertThatThrownBy(() -> service(new OfflineImageModel(), emptySpeech)
            .create(new ContentRequest("主题", "开发者", "story")))
            .isInstanceOf(ContentStudioException.class)
            .hasMessage("文字转语音模型没有返回音频");
    }

    private ContentStudioService service(ImageModel imageModel, TextToSpeechModel speechModel) {
        return new ContentStudioService(new OfflineContentChatModel(), imageModel, speechModel);
    }

}
