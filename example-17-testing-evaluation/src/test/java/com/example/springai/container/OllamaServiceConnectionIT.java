package com.example.springai.container;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;

import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 可选的 Testcontainers Service Connection 验证。
 *
 * <p>该类使用 Maven container profile 的 Failsafe 执行；设置
 * RUN_OLLAMA_TESTCONTAINER_TESTS=true 且本机可访问 Docker 后再运行。测试只验证
 * Spring Boot 收到由 Spring AI 工厂创建的连接详情，不下载模型。</p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "RUN_OLLAMA_TESTCONTAINER_TESTS", matches = "true")
class OllamaServiceConnectionIT {

    @Container
    @ServiceConnection
    static final OllamaContainer ollama = new OllamaContainer("ollama/ollama:0.23.1");

    @Autowired
    private OllamaConnectionDetails connectionDetails;

    @Test
    void serviceConnectionExposesContainerEndpoint() {
        assertThat(this.connectionDetails.getBaseUrl()).startsWith("http://");
    }

}
