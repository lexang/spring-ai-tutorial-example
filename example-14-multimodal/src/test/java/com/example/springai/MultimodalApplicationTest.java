package com.example.springai;

import com.example.springai.service.MultimodalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.image=none",
        "spring.ai.model.audio.transcription=none",
        "spring.ai.model.audio.speech=none",
        "spring.ai.model.embedding=none",
        "spring.ai.model.moderation=none"
})
class MultimodalApplicationTest {

    @Autowired
    private MultimodalService multimodalService;

    @Test
    void applicationStartsWithoutApiKeyOrModelBeans() {
        MultimodalService.ModelStatus status = this.multimodalService.status();

        assertThat(status.chatEnabled()).isFalse();
        assertThat(status.imageEnabled()).isFalse();
        assertThat(status.transcriptionEnabled()).isFalse();
        assertThat(status.speechEnabled()).isFalse();
    }

}
