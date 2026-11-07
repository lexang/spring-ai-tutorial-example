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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** 检查实际 SDK 图像报文与 Base64 解析；只访问本机，不生成真实图片。 */
@SpringBootTest(properties = {
        "spring.ai.model.chat=none", "spring.ai.model.image=openai", "spring.ai.model.audio.speech=none",
        "spring.ai.model.audio.transcription=none", "spring.ai.model.embedding=none", "spring.ai.model.moderation=none",
        "spring.ai.openai.api-key=test-only-key", "spring.ai.openai.image.api-key=test-only-key",
        "spring.ai.openai.image.model=gpt-image-1-mini", "spring.ai.openai.max-retries=0"
})
class OpenAiImageSdkTests {
    static final AtomicReference<String> requestBody = new AtomicReference<>();
    static final HttpServer server = startServer();
    @Autowired MultimodalService service;

    @DynamicPropertySource static void localEndpoint(DynamicPropertyRegistry properties) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        properties.add("spring.ai.openai.base-url", () -> url);
        properties.add("spring.ai.openai.image.base-url", () -> url);
    }

    @AfterAll static void stopServer() { server.stop(0); }

    @Test void imageOptionsDoNotInjectRetiredStyleOrResponseFormat() {
        var reply = service.generateImage(new MultimodalService.ImageRequest("图像请求测试", 1, 1024, 1024));
        assertThat(reply.images()).hasSize(1);
        assertThat(reply.images().get(0).base64()).isEqualTo("Zml4dHVyZQ==");
        assertThat(reply.images().get(0).url()).isNull();
        assertThat(requestBody.get()).contains("1024x1024").doesNotContain("\"style\"", "\"response_format\"");
        assertThat(java.util.regex.Pattern.compile("\"model\"\\s*:\\s*\"gpt-image-1-mini\"")
                .matcher(requestBody.get()).find()).isTrue();
    }

    private static HttpServer startServer() {
        try {
            HttpServer value = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            value.createContext("/", exchange -> {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = "{\"created\":1,\"data\":[{\"b64_json\":\"Zml4dHVyZQ==\"}]}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            value.start();
            return value;
        }
        catch (java.io.IOException exception) { throw new IllegalStateException("本机图像 SDK 测试服务启动失败", exception); }
    }
}
