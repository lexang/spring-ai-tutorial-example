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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.template.NoOpTemplateRenderer;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** ChatClient 官方页面中模板、集合、流式转换和应用案例的完整实现。 */
@RestController
@RequestMapping("/examples")
public class ClientExamplesController {

    private final ChatClient client;
    private final ChatClient templateClient;
    private final MemoryConversationService memory;

    public ClientExamplesController(ChatClient.Builder builder, MemoryConversationService memory) {
        this.client = builder.clone().build();
        this.templateClient = builder.clone()
                .defaultSystem(new ClassPathResource("prompts/client-system.st")).build();
        this.memory = memory;
    }

    @GetMapping("/template")
    public String template(@RequestParam String topic, @RequestParam(defaultValue = "简洁") String voice) {
        return text(this.templateClient.prompt()
                .system(s -> s.param("voice", input(voice, "voice")))
                .user(u -> u.text("请介绍 {topic}").param("topic", input(topic, "topic"))));
    }

    @GetMapping("/custom-template")
    public String customTemplate(@RequestParam String topic) {
        return text(this.client.prompt()
                .templateRenderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<').endDelimiterToken('>').build())
                .user(u -> u.text("请介绍 <topic>，可使用 JSON 示例。").param("topic", input(topic, "topic"))));
    }

    @PostMapping("/literal")
    public String literal(@RequestBody String body) {
        return text(this.client.prompt().templateRenderer(new NoOpTemplateRenderer()).user(input(body, "body")));
    }

    @GetMapping("/films")
    public List<ActorFilms> films(@RequestParam String actor) {
        List<ActorFilms> result = this.client.prompt("列出以下演员的作品，用 JSON 数组返回：" + input(actor, "actor"))
                .call().entity(new ParameterizedTypeReference<List<ActorFilms>>() { });
        return requireFilms(result);
    }

    @GetMapping("/films/stream")
    public Mono<List<ActorFilms>> streamedFilms(@RequestParam String actor) {
        BeanOutputConverter<List<ActorFilms>> converter =
                new BeanOutputConverter<>(new ParameterizedTypeReference<List<ActorFilms>>() { });
        return this.client.prompt()
                .user(u -> u.text("列出 {actor} 的作品。{format}")
                        .param("actor", input(actor, "actor")).param("format", converter.getFormat()))
                .stream().content().collectList()
                .map(chunks -> requireFilms(converter.convert(
                        ChatResponseSupport.requireContent(String.join("", chunks)))));
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody String body) {
        ResponseEntity<ChatResponse, ChatClientController.Analysis> result = this.client.prompt()
                .user(u -> u.text("分析以下文本的情感：{text}").param("text", input(body, "text")))
                .call().responseEntity(ChatClientController.Analysis.class);
        if (result.entity() == null) throw new EmptyChatResponseException("模型未返回分析实体");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("analysis", result.entity());
        response.put("usage", ChatResponseSupport.usageSummary(result.response()));
        return response;
    }

    @GetMapping("/memory")
    public String memory(@RequestParam String conversationId, @RequestParam String message) {
        return this.memory.chat(conversationId, message);
    }

    @GetMapping("/tools")
    public String tools(@RequestParam String question) {
        return text(this.client.prompt().tools(new DateTools()).user(input(question, "question")));
    }

    @GetMapping("/qa")
    public String ask(@RequestParam String question) {
        return text(this.client.prompt().system("你是知识问答助手，不确定时明确说明。")
                .user(input(question, "question")));
    }

    @PostMapping("/explain-code")
    public String explain(@RequestBody String code) {
        return text(this.client.prompt().system("你是代码分析助手，请解释代码的作用和限制。")
                .user(u -> u.text("代码：\n{code}").param("code", input(code, "code"))));
    }

    @PostMapping("/summarize")
    public String summarize(@RequestBody String body, @RequestParam(defaultValue = "100") int maxTokens) {
        return text(this.client.prompt().user("请概括以下内容：\n" + input(body, "text"))
                .options(OpenAiChatOptions.builder()
                        .maxTokens(DemoRequestValidation.requirePositiveTokenLimit(maxTokens))));
    }

    @GetMapping(value = "/typewriter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> typewriter(@RequestParam String message) {
        return this.client.prompt().user(input(message, "message")).stream().content()
                .map(chunk -> ServerSentEvent.builder(chunk).build());
    }

    private static String input(String value, String name) {
        return DemoRequestValidation.requireText(value, name);
    }

    private static String text(ChatClient.ChatClientRequestSpec request) {
        return ChatResponseSupport.requireContent(request.call().content());
    }

    private static List<ActorFilms> requireFilms(List<ActorFilms> films) {
        if (films == null || films.isEmpty()) throw new EmptyChatResponseException("模型未返回电影列表");
        return films;
    }

    public record ActorFilms(String actor, List<String> movies) { }
}
