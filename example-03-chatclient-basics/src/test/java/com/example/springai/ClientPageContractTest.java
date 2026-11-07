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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.ToolAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientPageContractTest {

    @Test
    void explicitPromptMessagesDoNotInheritUserTemplateMetadata() {
        Model model = new Model();
        ChatClient client = ChatClient.builder(model).defaultUser(u -> u.metadata("defaultKey", "value")).build();
        client.prompt().user("模板用户字段").call().content();
        assertThat(model.last().getUserMessage().getMetadata()).containsEntry("defaultKey", "value");
        client.prompt("显式 Prompt 消息").call().content();
        assertThat(model.last().getUserMessage().getMetadata()).doesNotContainKey("defaultKey");
        client.prompt(new Prompt(UserMessage.builder().text("显式 metadata")
                .metadata(Map.of("explicit", true)).build())).call().content();
        assertThat(model.last().getUserMessage().getMetadata())
                .containsEntry("explicit", true).doesNotContainKey("defaultKey");
    }

    @Test
    void autoRegisteredToolLoopExecutesTheDeclaredTool() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override public ChatResponse call(Prompt prompt) {
                if (calls.incrementAndGet() == 1) {
                    return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                            .toolCalls(List.of(new AssistantMessage.ToolCall(
                                    "date-call", "function", "currentDate", "{}"))).build())));
                }
                assertThat(prompt.getInstructions()).anyMatch(ToolResponseMessage.class::isInstance);
                return Model.response("日期工具已执行");
            }
            @Override public OpenAiChatOptions getOptions() { return OpenAiChatOptions.builder().build(); }
        };
        ClientExamplesController controller = new ClientExamplesController(ChatClient.builder(model),
                new MemoryConversationService(ChatClient.builder(model)));
        assertThat(controller.tools("今天的 UTC 日期是什么")).isEqualTo("日期工具已执行");
        assertThat(calls).hasValue(2);
    }

    @Test
    void globalToolAdvisorSwitchIsAppliedByAutoConfiguration() {
        Model model = new Model();
        Probe probe = new Probe();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChatClientAutoConfiguration.class))
                .withBean(ChatModel.class, () -> model)
                .withPropertyValues("spring.ai.chat.client.tool-calling.enabled=false")
                .run(context -> {
                    context.getBean(ChatClient.Builder.class).defaultAdvisors(probe).build()
                            .prompt("禁用自动工具循环").call().content();
                    assertThat(probe.automaticTools).isZero();
                });
    }

    @Test
    void defaultToolsAndToolContextAreMergedWithRequestSettings() {
        Model model = new Model();
        ChatClient client = ChatClient.builder(model).defaultTools(new EchoTool())
                .defaultToolContext(Map.of("tenant", "demo", "channel", "default")).build();
        client.prompt("工具配置").tools(new SecondTool())
                .toolContext(Map.of("channel", "request")).call().content();
        OpenAiChatOptions options = (OpenAiChatOptions) model.last().getOptions();
        assertThat(options.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder("echo", "second");
        assertThat(options.getToolContext()).containsEntry("tenant", "demo").containsEntry("channel", "request");
    }

    @Test
    void threePromptOverloadsCarryTheSameUserText() {
        Model model = new Model();
        ChatClient client = ChatClient.create(model);
        client.prompt().user("你好").call().content();
        client.prompt("你好").call().content();
        client.prompt(new Prompt(new UserMessage("你好"))).call().content();
        assertThat(model.prompts).allSatisfy(prompt ->
                assertThat(prompt.getUserMessage().getText()).isEqualTo("你好"));
        assertThat(model.prompts).hasSize(3);
    }

    @Test
    void resourceTemplateCustomDelimiterAndNoOpAreExecutable() {
        Model model = new Model();
        ClientExamplesController controller = examples(model);
        controller.template("Spring", "详细");
        assertThat(model.last().getSystemMessage().getText()).contains("详细");
        assertThat(model.last().getUserMessage().getText()).isEqualTo("请介绍 Spring");
        controller.customTemplate("JSON {name}");
        assertThat(model.last().getUserMessage().getText()).contains("JSON {name}");
        controller.literal("{\"topic\":\"<topic>\"}");
        assertThat(model.last().getUserMessage().getText()).isEqualTo("{\"topic\":\"<topic>\"}");
        ChatClient.create(model).prompt().templateRenderer(new org.springframework.ai.template.NoOpTemplateRenderer())
                .user(u -> u.text("{name}").param("name", "不应替换")).call().content();
        assertThat(model.last().getUserMessage().getText()).isEqualTo("{name}");
    }

    @Test
    void genericEntityAndAggregatedStreamHaveTheSameStructure() {
        Model model = new Model();
        model.answer = "[{\"actor\":\"示例演员\",\"movies\":[\"电影一\"]}]";
        ClientExamplesController controller = examples(model);
        assertThat(controller.films("示例演员")).hasSize(1);
        model.chunks = List.of("[{\"actor\":\"示例演员\",", "\"movies\":[\"电影一\"]}]");
        assertThat(controller.streamedFilms("示例演员").block())
                .containsExactly(new ClientExamplesController.ActorFilms("示例演员", List.of("电影一")));
    }

    @Test
    void analyzeUsesTheSuppliedTextAndReturnsResponseMetadata() {
        Model model = new Model();
        model.answer = "{\"sentiment\":\"positive\",\"confidence\":0.9,\"keywords\":[\"服务\"]}";
        Map<String, Object> result = examples(model).analyze("这家店服务很好");
        assertThat(model.last().getUserMessage().getText()).contains("这家店服务很好");
        assertThat(result).containsKeys("analysis", "usage");
    }

    @Test
    void defaultsAndRequestMutationAreIndependent() {
        Model model = new Model();
        ChatClient original = ChatClient.builder(model).defaultSystem("原始角色")
                .defaultOptions(OpenAiChatOptions.builder().temperature(0.4)).build();
        ChatClient derivative = original.mutate().defaultSystem("派生角色").build();
        derivative.prompt("问题").call().content();
        assertThat(model.last().getSystemMessage().getText()).isEqualTo("派生角色");
        original.prompt("问题").call().content();
        assertThat(model.last().getSystemMessage().getText()).isEqualTo("原始角色");
        var request = original.prompt().system("请求角色").user("请求文本");
        ChatClient fromRequest = request.mutate().build();
        fromRequest.prompt().call().content();
        assertThat(model.last().getUserMessage().getText()).isEqualTo("请求文本");
        assertThat(model.last().getSystemMessage().getText()).isEqualTo("请求角色");
        assertThat(model.last().getOptions().getTemperature()).isEqualTo(0.4);
    }

    @Test
    void toolAdvisorRegistrationCanBeDisabledOrReplaced() {
        Model model = new Model();
        Probe probe = new Probe();
        ChatClient client = ChatClient.builder(model).defaultAdvisors(probe).build();
        client.prompt("无静态工具").call().content();
        assertThat(probe.automaticTools).isEqualTo(1);
        client.prompt("手动处理").advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false)).call().content();
        assertThat(probe.automaticTools).isZero();
        client.prompt("自定义工具处理").advisors(new Marker()).call().content();
        assertThat(probe.automaticTools).isZero();
    }

    @Test
    void memoryUsesConversationIdAndSupportsClear() {
        Model model = new Model();
        MemoryConversationService memory = new MemoryConversationService(ChatClient.builder(model));
        memory.chat("a", "我的名字是小明");
        memory.chat("a", "我叫什么");
        assertThat(model.last().getInstructions()).hasSize(3);
        memory.chat("b", "另一会话");
        assertThat(model.last().getInstructions()).hasSize(1);
        memory.clear("a");
        memory.chat("a", "重新开始");
        assertThat(model.last().getInstructions()).hasSize(1);
        assertThatThrownBy(() -> memory.chat(" ", "消息")).isInstanceOf(InvalidDemoRequestException.class);
    }

    @Test
    void usageAndTimingDoNotDependOnLoggerDebug() {
        Model model = new Model();
        UsageTimingAdvisor metrics = new UsageTimingAdvisor();
        ChatClient client = ChatClient.builder(model).defaultAdvisors(metrics).build();
        client.prompt("成功").call().content();
        model.failure = new IllegalStateException("失败测试");
        assertThatThrownBy(() -> client.prompt("失败").call().content()).isInstanceOf(IllegalStateException.class);
        assertThat(metrics.snapshot().calls()).isEqualTo(2);
        assertThat(metrics.snapshot().failures()).isEqualTo(1);
        assertThat(metrics.snapshot().reportedTokens()).isEqualTo(6);
        assertThat(metrics.snapshot().elapsedNanos()).isPositive();
    }

    private ClientExamplesController examples(Model model) {
        return new ClientExamplesController(ChatClient.builder(model),
                new MemoryConversationService(ChatClient.builder(model)));
    }

    private static class Probe implements CallAdvisor {
        private long automaticTools;
        @Override public String getName() { return "Probe"; }
        @Override public int getOrder() { return Integer.MIN_VALUE + 10; }
        @Override public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            this.automaticTools = chain.getCallAdvisors().stream()
                    .filter(ToolCallingAdvisor.class::isInstance).count();
            return chain.nextCall(request);
        }
    }

    private static final class Marker extends Probe implements ToolAdvisor { }

    static class EchoTool {
        @Tool(description = "回显") public String echo(String text) { return text; }
    }

    static class SecondTool {
        @Tool(description = "第二个回显") public String second(String text) { return text; }
    }

    private static final class Model implements ChatModel {
        private final List<Prompt> prompts = new ArrayList<>();
        private String answer = "测试回答";
        private List<String> chunks = List.of("第一块", "第二块");
        private RuntimeException failure;

        private Prompt last() { return this.prompts.get(this.prompts.size() - 1); }
        @Override public ChatResponse call(Prompt prompt) {
            this.prompts.add(prompt);
            if (this.failure != null) throw this.failure;
            return response(this.answer);
        }
        @Override public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.defer(() -> {
                this.prompts.add(prompt);
                return Flux.fromIterable(this.chunks).map(Model::response);
            });
        }
        @Override public OpenAiChatOptions getOptions() { return OpenAiChatOptions.builder().build(); }
        private static ChatResponse response(String text) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                    ChatResponseMetadata.builder().usage(new DefaultUsage(4, 2, 6)).build());
        }
    }
}
