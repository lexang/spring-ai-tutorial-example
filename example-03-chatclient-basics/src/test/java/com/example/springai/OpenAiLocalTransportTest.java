/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.springai;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实运行 OpenAI SDK 的同步和 SSE 解析，只访问本机确定性 HTTP 服务。 */
class OpenAiLocalTransportTest {

    @Test
    void syncAndStreamingWorkWithoutAWebFluxStarterAndUseSdkCustomizer() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger customHeaders = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            if ("chatclient-2.0".equals(exchange.getRequestHeaders().getFirst("X-Spring-AI-Example"))) {
                customHeaders.incrementAndGet();
            }
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean streaming = Pattern.compile("\"stream\"\\s*:\\s*true").matcher(request).find();
            String body = streaming ? """
                    data: {"id":"local","object":"chat.completion.chunk","created":1,"model":"local","choices":[{"index":0,"delta":{"role":"assistant","content":"流"},"finish_reason":null}]}

                    data: {"id":"local","object":"chat.completion.chunk","created":1,"model":"local","choices":[{"index":0,"delta":{"content":"式"},"finish_reason":"stop"}]}

                    data: [DONE]

                    """ : """
                    {"id":"local","object":"chat.completion","created":1,"model":"local","choices":[{"index":0,"message":{"role":"assistant","content":"同步"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2,"completion_tokens":1,"total_tokens":3}}
                    """;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", streaming ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ChatClientBasicsApplication.class)
                .web(WebApplicationType.NONE).run(
                        "--spring.ai.openai.api-key=local-test-unused",
                        "--spring.ai.openai.base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                        "--spring.ai.openai.chat.model=local",
                        "--spring.ai.openai.max-retries=0",
                        "--spring.ai.openai.timeout=5s",
                        "--demo.http.add-example-header=true",
                        "--spring.main.banner-mode=off",
                        "--logging.level.root=WARN")) {
            OpenAiChatModel model = context.getBean(OpenAiChatModel.class);
            ChatClient client = ChatClient.create(model);
            assertThat(client.prompt("同步请求").call().content()).isEqualTo("同步");
            assertThat(String.join("", client.prompt("流式请求").stream().content()
                    .collectList().block(Duration.ofSeconds(15)))).isEqualTo("流式");
            assertThat(requests).hasValue(2);
            assertThat(customHeaders).hasValue(2);
        } finally {
            server.stop(0);
        }
    }
}
