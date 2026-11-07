package com.example.springai;

import com.example.springai.service.AdvisorPipelineService;
import com.example.springai.support.FeatureUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "server.port=0"
})
class AdvisorsApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AdvisorPipelineService advisorPipelineService;

    @Test
    void applicationStartsWithoutModelOrApiKey() {
        assertThat(this.applicationContext.getBeansOfType(ChatModel.class)).isEmpty();
        assertThat(this.advisorPipelineService.status().enabled()).isFalse();
        assertThat(this.advisorPipelineService.status().advisors()).hasSize(6);
    }

    @Test
    void modelEndpointExplainsHowToEnableTheFeature() {
        assertThatThrownBy(() -> this.advisorPipelineService.call("你好"))
            .isInstanceOf(FeatureUnavailableException.class)
            .hasMessageContaining("SPRING_AI_CHAT_MODEL")
            .hasMessageContaining("MINIMAX_API_KEY");
    }

}
