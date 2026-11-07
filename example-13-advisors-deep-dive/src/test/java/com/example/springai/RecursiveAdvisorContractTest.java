package com.example.springai;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.springai.advisor.NonBlankResponseAdvisor;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.DefaultAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.function.FunctionToolCallback;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecursiveAdvisorContractTest {

    private static final String SCHEMA = """
            {"type":"object","properties":{"answer":{"type":"string"}},"required":["answer"]}
            """;

    @Test
    void retryReentersOnlyDownstreamAndAccumulatesUsageAndContext() {
        List<String> trace = new ArrayList<>();
        List<Prompt> prompts = new ArrayList<>();
        ChatModel model = prompt -> {
            prompts.add(prompt);
            trace.add("model");
            return response(prompts.size() == 1 ? " " : "有效回答", 15);
        };
        ChatClientResponse result = ChatClient.builder(model)
            .defaultAdvisors(trace("outer", -20, trace), new NonBlankResponseAdvisor(1, 0),
                    trace("inner", 20, trace))
            .build().prompt("问题")
            .advisors(spec -> spec.param("traceId", "trace-006"))
            .call().chatClientResponse();

        assertThat(trace).containsExactly("outer-before", "inner-before", "model", "inner-after",
                "inner-before", "model", "inner-after", "outer-after");
        assertThat(prompts.get(1).getUserMessage().getText()).contains("上次回答为空");
        assertThat(result.chatResponse().getMetadata().getUsage().getTotalTokens()).isEqualTo(30);
        assertThat(result.chatResponse().getMetadata().getModel()).isEqualTo("fixture");
        assertThat(result.context()).containsEntry("traceId", "trace-006")
            .containsEntry(NonBlankResponseAdvisor.ATTEMPT, 1);
    }

    @Test
    void retryLimitAndZeroAreExactAndExceptionsDoNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel blank = prompt -> {
            calls.incrementAndGet();
            return response(" ", 15);
        };
        assertThatThrownBy(() -> client(blank, 3).prompt("问题").call().content())
            .hasMessage("模型在 4 次调用后仍未返回非空回答");
        assertThat(calls).hasValue(4);
        calls.set(0);
        assertThatThrownBy(() -> client(blank, 0).prompt("问题").call().content())
            .hasMessage("模型在 1 次调用后仍未返回非空回答");
        assertThat(calls).hasValue(1);
        calls.set(0);
        ChatModel failing = prompt -> {
            calls.incrementAndGet();
            throw new IllegalStateException("模型连接失败");
        };
        assertThatThrownBy(() -> client(failing, 3).prompt("问题").call().content())
            .hasMessage("模型连接失败");
        assertThat(calls).hasValue(1);
        assertThatThrownBy(() -> new NonBlankResponseAdvisor(4, 0))
            .hasMessage("追加重试次数必须在 0 到 3 之间");
    }

    @Test
    void accumulatorDoesNotLeakAcrossCallsAndPreservesUsageWhenFinalRoundOmitsIt() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt -> response(calls.incrementAndGet() % 2 == 1 ? " " : "成功",
                calls.get() % 2 == 1 ? 15 : 0);
        ChatClient client = client(model, 1);
        for (int i = 0; i < 2; i++) {
            assertThat(client.prompt("问题").call().chatResponse().getMetadata().getUsage().getTotalTokens())
                .isEqualTo(15);
        }
        assertThat(calls).hasValue(4);
    }

    @Test
    void toolRequestIsReturnedWithoutRetryWhenAutomaticToolExecutionIsDisabled() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt -> {
            calls.incrementAndGet();
            return toolRequest();
        };
        ChatResponse response = client(model, 3).prompt("工具问题")
            .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
            .call().chatResponse();
        assertThat(response.hasToolCalls()).isTrue();
        assertThat(calls).hasValue(1);
    }

    @Test
    void copyRetainsRegistryButDoesNotCloneAdvisorInstancesAndRejectsForeignAdvisor() {
        List<String> trace = new ArrayList<>();
        CallAdvisor outer = trace("outer", 0, trace);
        CallAdvisor inner = trace("inner", 1, trace);
        ObservationRegistry registry = ObservationRegistry.create();
        DefaultAroundAdvisorChain chain = DefaultAroundAdvisorChain.builder(registry)
            .pushAll(List.of(outer, inner)).build();
        CallAdvisorChain copy = chain.copy(outer);
        assertThat(copy.getCallAdvisors()).containsExactly(inner);
        assertThat(copy.getObservationRegistry()).isSameAs(registry);
        assertThatThrownBy(() -> chain.copy(trace("foreign", 0, trace)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not part of the chain");
    }

    @Test
    void validationSupportsTypeSchemaMapperAndDefaultFourCalls() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt -> {
            calls.incrementAndGet();
            return response("不是 JSON", 15);
        };
        ChatClientResponse result = ChatClient.builder(model)
            .defaultAdvisors(StructuredOutputValidationAdvisor.builder()
                .outputJsonSchema(SCHEMA).jsonMapper(JsonMapper.builder().build()).build())
            .build().prompt("生成答案").call().chatClientResponse();
        assertThat(calls).hasValue(4);
        // Advisor 耗尽次数后返回最后结果；仅 .chatClientResponse() 不会抛校验异常。
        assertThat(result.chatResponse().getMetadata().getUsage().getTotalTokens()).isEqualTo(60);
        assertThat(result.chatResponse().getResult().getOutput().getText()).isEqualTo("不是 JSON");

        Answer answer = ChatClient.create(prompt -> response("{\"answer\":\"成功\"}", 15))
            .prompt("生成答案").call().entity(Answer.class, spec -> spec.validateSchema());
        assertThat(answer.answer()).isEqualTo("成功");
        assertThat(StructuredOutputValidationAdvisor.builder().outputType(Answer.class).build()).isNotNull();
        assertThatThrownBy(() -> StructuredOutputValidationAdvisor.builder().outputType(Answer.class)
            .outputJsonSchema(SCHEMA).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StructuredOutputValidationAdvisor.builder().build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validationReturnsToolRequestImmediatelyAndRejectsStreamingBeforeModelCall() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt -> {
            calls.incrementAndGet();
            return toolRequest();
        };
        var advisor = StructuredOutputValidationAdvisor.builder().outputJsonSchema(SCHEMA).build();
        ChatResponse response = ChatClient.builder(model).defaultAdvisors(advisor).build()
            .prompt("工具问题").advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
            .call().chatResponse();
        assertThat(response.hasToolCalls()).isTrue();
        assertThat(calls).hasValue(1);
        StepVerifier.create(advisor.adviseStream(ChatClientRequest.builder().prompt(new Prompt("问题")).build(),
                DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP).push(advisor).build()))
            .expectError(UnsupportedOperationException.class).verify();
        assertThat(calls).hasValue(1);
    }

    @Test
    void streamingToolLoopAccumulatesUsageEvenIfFinalRoundHasNoUsage() {
        AtomicInteger rounds = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new AssertionError("本测试只验证流式工具循环");
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.defer(() -> rounds.incrementAndGet() == 1 ? Flux.just(toolRequest())
                        : Flux.just(response("最终", 0), response("回答", 0)));
            }

            @Override
            public ToolCallingChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }
        };
        ToolCallback tool = FunctionToolCallback.builder("lookup", (LookupRequest ignored) -> {
            executions.incrementAndGet();
            return "工具结果";
        }).inputType(LookupRequest.class).description("离线查询").build();
        ChatClient client = ChatClient.create(model);
        List<ChatClientResponse> chunks = client.prompt("查询")
            .options(ToolCallingChatOptions.builder().toolCallbacks(tool))
            .stream().chatClientResponse().collectList().block();
        assertThat(rounds).hasValue(2);
        assertThat(executions).hasValue(1);
        assertThat(chunks).isNotEmpty();
        String text = chunks.stream().filter(c -> c.chatResponse().getResult() != null)
            .map(c -> c.chatResponse().getResult().getOutput().getText()).reduce("", String::concat);
        assertThat(text).isEqualTo("最终回答");
        ChatResponse tail = chunks.get(chunks.size() - 1).chatResponse();
        assertThat(tail.getResults()).isEmpty();
        assertThat(tail.getMetadata().getUsage().getTotalTokens()).isEqualTo(15);
        List<ChatClientResponse> aggregated = new ArrayList<>();
        new ChatClientMessageAggregator().aggregateChatClientResponse(Flux.fromIterable(chunks), aggregated::add)
            .blockLast();
        assertThat(aggregated.get(0).chatResponse().getMetadata().getUsage().getTotalTokens()).isEqualTo(15);
    }

    @Test
    void returnDirectStopsAfterOneModelRoundAndPreservesUsage() {
        AtomicInteger calls = new AtomicInteger();
        DirectTools tools = new DirectTools();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                calls.incrementAndGet();
                return toolRequest();
            }

            @Override
            public ToolCallingChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }
        };
        ChatResponse response = ChatClient.builder(model)
            .defaultAdvisors(ToolCallingAdvisor.builder().build()).build()
            .prompt("查询").tools(tools).call().chatResponse();
        assertThat(calls).hasValue(1);
        assertThat(tools.calls).hasValue(1);
        assertThat(response.hasToolCalls()).isFalse();
        assertThat(response.getResult().getOutput().getText()).contains("直接结果");
        assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(15);
    }

    private static ChatClient client(ChatModel model, int retries) {
        return ChatClient.builder(model).defaultAdvisors(new NonBlankResponseAdvisor(retries, 0)).build();
    }

    private static ChatResponse response(String text, int total) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().model("fixture").usage(new DefaultUsage(total, 0, total)).build());
    }

    private static ChatResponse toolRequest() {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("tool-006", "function", "lookup", "{}")))
                .build())), ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5, 15)).build());
    }

    private static CallAdvisor trace(String name, int order, List<String> entries) {
        return new CallAdvisor() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public int getOrder() {
                return order;
            }

            @Override
            public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
                entries.add(name + "-before");
                ChatClientResponse response = chain.nextCall(request);
                entries.add(name + "-after");
                return response;
            }
        };
    }

    public record Answer(String answer) {
    }

    public record LookupRequest(String key) {
    }

    public static final class DirectTools {
        private final AtomicInteger calls = new AtomicInteger();

        @Tool(description = "离线查询", returnDirect = true)
        public String lookup() {
            this.calls.incrementAndGet();
            return "直接结果";
        }
    }

}
