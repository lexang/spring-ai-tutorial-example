package com.example.springai;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** 确认离线示例的 Spring Boot wiring 可以在无外部服务时启动。 */
@SpringBootTest
class ApplicationContextTest {

    @Autowired
    private RelevancyEvaluator relevancyEvaluator;

    @Autowired
    private FactCheckingEvaluator factCheckingEvaluator;

    @Test
    void evaluationBeansAreAvailable() {
        assertThat(this.relevancyEvaluator).isNotNull();
        assertThat(this.factCheckingEvaluator).isNotNull();
    }

}
