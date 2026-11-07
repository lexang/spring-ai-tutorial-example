package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.chat.model.ChatModel;
import static org.assertj.core.api.Assertions.assertThat;

/** 只验证在线装配，使用本机无服务端点和非凭据 fixture，不请求供应商。 */
@SpringBootTest(properties = {"spring.profiles.active=live", "spring.ai.openai.api-key=fixture-not-a-secret",
    "spring.ai.openai.base-url=http://127.0.0.1:9"})
class LiveConfigurationTest {
    @Autowired ApplicationContext context;
    @Test void liveModeHasOnlyTheProviderModel() {
        assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
        assertThat(context.containsBean("deterministicTranslationChatModel")).isFalse();
        assertThat(context.containsBean("translationChatClient")).isTrue();
    }
}
