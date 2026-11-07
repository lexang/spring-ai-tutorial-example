package com.example.springai;

import com.example.springai.service.AdvisorPipelineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设置 RUN_ADVISOR_INTEGRATION_TESTS=true 和有效模型密钥后才调用真实服务。
 */
@EnabledIfEnvironmentVariable(
        named = "RUN_ADVISOR_INTEGRATION_TESTS",
        matches = "(?i)true")
@SpringBootTest(properties = {
        "spring.ai.model.chat=anthropic",
        "server.port=0"
})
class ConfiguredModelIntegrationTest {

    @Autowired
    private AdvisorPipelineService advisorPipelineService;

    @Test
    void realModelRunsThroughAdvisorPipeline() {
        var reply = this.advisorPipelineService.call("请只回复：Advisor 已连接");

        assertThat(reply.response()).isNotBlank();
        assertThat(reply.response()).contains("Advisor");
    }

}
