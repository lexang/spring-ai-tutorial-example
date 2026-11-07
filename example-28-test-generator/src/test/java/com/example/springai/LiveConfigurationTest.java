package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import static org.assertj.core.api.Assertions.assertThat;

/** 在线生成与 Judge 都应可装配，不使用离线 Judge 冒充在线评估。 */
@SpringBootTest(properties = {"spring.profiles.active=live", "spring.ai.openai.api-key=fixture-not-a-secret",
    "spring.ai.openai.base-url=http://127.0.0.1:9"})
class LiveConfigurationTest {
    @Autowired ApplicationContext context;
    @Test void onlineGenerationAndJudgeAreBothConfigured() {
        assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
        assertThat(context.getBeansOfType(RelevancyEvaluator.class)).hasSize(1);
        assertThat(context.containsBean("judgeChatModel")).isFalse();
        assertThat(context.containsBean("liveRelevancyEvaluator")).isTrue();
    }
}
