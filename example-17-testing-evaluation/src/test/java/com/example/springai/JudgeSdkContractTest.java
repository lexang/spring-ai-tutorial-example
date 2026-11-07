package com.example.springai;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.example.springai.evaluation.EvaluationService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实供应商 SDK 请求本机固定 Judge，不访问外部模型、不判断实际语义。 */
@SpringBootTest(properties = {
        "app.evaluation.offline=false", "spring.ai.model.chat=anthropic", "spring.ai.model.embedding=none",
        "spring.ai.anthropic.api-key=test-only-key", "spring.ai.anthropic.chat.model=judge-fixture"
})
class JudgeSdkContractTest {
    static final AtomicReference<String> requestBody = new AtomicReference<>();
    static final HttpServer server = startServer();
    @Autowired EvaluationService service;
    @Autowired ChatModel model;

    @DynamicPropertySource static void localEndpoint(DynamicPropertyRegistry properties) {
        properties.add("spring.ai.anthropic.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterAll static void stopServer() { server.stop(0); }

    @Test void nonOfflineConfigurationCreatesTheSdkModelAndParsesJudgeAnswer() {
        assertThat(model).isInstanceOf(AnthropicChatModel.class);
        var result = service.evaluateRelevancy("问题", List.of("资料"), "回答");
        assertThat(result.mode()).isEqualTo("model-judge");
        assertThat(result.pass()).isTrue();
        assertThat(requestBody.get()).contains("judge-fixture", "Query:", "Context:");
    }

    private static HttpServer startServer() {
        try {
            var value = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            value.createContext("/", exchange -> {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = ("{\"id\":\"msg-fixture\",\"type\":\"message\",\"role\":\"assistant\","
                        + "\"model\":\"judge-fixture\",\"content\":[{\"type\":\"text\",\"text\":\"YES\"}],"
                        + "\"stop_reason\":\"end_turn\",\"stop_sequence\":null,\"usage\":{\"input_tokens\":3,\"output_tokens\":1}}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            value.start();
            return value;
        }
        catch (java.io.IOException exception) { throw new IllegalStateException("本机 Judge SDK 测试启动失败", exception); }
    }
}
