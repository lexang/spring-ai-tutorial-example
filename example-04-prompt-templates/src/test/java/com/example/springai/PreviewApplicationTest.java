package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("preview")
class PreviewApplicationTest {

    @Autowired
    private ExpertAssistant assistant;

    @Test
    void applicationPreviewsWithoutApiKeyAndGenerationIsDisabled() {
        assertThat(this.assistant.preview("问题 {name}", "Spring").user()).isEqualTo("问题 {name}");
        assertThatThrownBy(() -> this.assistant.ask("问题", "Spring")).isInstanceOf(IllegalStateException.class);
    }
}
