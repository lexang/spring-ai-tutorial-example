package com.example.springai;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.example.springai.service.MultimodalService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实 SDK 只请求本机 HTTP fixture，不访问 OpenAI，也不识别真实音频。 */
@SpringBootTest(properties = {
        "spring.ai.model.chat=none", "spring.ai.model.image=none", "spring.ai.model.audio.speech=none",
        "spring.ai.model.audio.transcription=openai", "spring.ai.model.embedding=none", "spring.ai.model.moderation=none",
        "spring.ai.openai.api-key=test-only-key", "spring.ai.openai.audio.transcription.api-key=test-only-key",
        "spring.ai.openai.audio.transcription.model=gpt-transcribe",
        "spring.ai.openai.audio.transcription.response-format=json", "spring.ai.openai.max-retries=0"
})
class OpenAiTranscriptionSdkTests {
    static final AtomicReference<String> requestBody = new AtomicReference<>();
    static final HttpServer server = startServer();
    @Autowired MultimodalService service;

    @DynamicPropertySource static void localEndpoint(DynamicPropertyRegistry properties) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        properties.add("spring.ai.openai.base-url", () -> url);
        properties.add("spring.ai.openai.audio.transcription.base-url", () -> url);
    }

    @AfterAll static void stopServer() { server.stop(0); }

    @Test void languageOverridePreservesConfiguredModelAndJsonFormat() {
        var reply = service.transcribe(new MockMultipartFile("file", "test.wav", "audio/wav", new byte[] {1, 2}), "zh");
        assertThat(reply.text()).isEqualTo("固定转写结果");
        assertThat(requestBody.get()).contains("gpt-transcribe", "json", "zh", "upload.wav").doesNotContain("whisper-1");
    }

    private static HttpServer startServer() {
        try {
            HttpServer value = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            value.createContext("/", exchange -> {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = "{\"text\":\"固定转写结果\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            value.start();
            return value;
        }
        catch (java.io.IOException exception) { throw new IllegalStateException("本机 SDK 测试服务启动失败", exception); }
    }
}
