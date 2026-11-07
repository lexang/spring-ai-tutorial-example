package com.example.springai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.example.springai.advisor.ContentSafetyAdvisor;
import com.example.springai.advisor.ReReadingAdvisor;
import com.example.springai.advisor.ResponseLabelAdvisor;
import com.example.springai.advisor.SimpleLoggerAdvisor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdvisorApiTest {

    @Test
    void reReadingAndResponseAdvisorsModifyBothSides() {
        RecordingChatModel model = new RecordingChatModel("模型原始回答");
        ChatClient client = ChatClient.builder(model)
            .defaultAdvisors(
                    new ReReadingAdvisor(),
                    new ResponseLabelAdvisor("[已处理] ", 100))
            .build();

        String content = client.prompt().user("2 + 2 等于多少？").call().content();

        assertThat(model.lastPrompt.getUserMessage().getText())
            .contains("2 + 2 等于多少？")
            .contains("请再读一遍问题");
        assertThat(content).isEqualTo("[已处理] 模型原始回答");
    }

    @Test
    void advisorOrderIsStackLike() {
        List<String> trace = new ArrayList<>();
        ChatClient client = ChatClient.builder(new RecordingChatModel("完成"))
            .defaultAdvisors(
                    new TraceAdvisor("C", 200, trace),
                    new TraceAdvisor("A", 0, trace),
                    new TraceAdvisor("B", 100, trace))
            .build();

        assertThat(client.prompt().user("顺序测试").call().content()).isEqualTo("完成");
        assertThat(trace).containsExactly(
                "A-before", "B-before", "C-before",
                "C-after", "B-after", "A-after");
    }

    @Test
    void contextWrittenOnRequestIsAvailableOnResponse() {
        AtomicReference<Object> observed = new AtomicReference<>();

        BaseAdvisor writer = new BaseAdvisor() {
            @Override
            public String getName() {
                return "ContextWriterAdvisor";
            }

            @Override
            public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
                return request.mutate().context("traceId", "trace-001").build();
            }

            @Override
            public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
                observed.set(response.context().get("traceId"));
                return response;
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };

        ChatClientResponse response = ChatClient.builder(new RecordingChatModel("上下文完成"))
            .defaultAdvisors(writer)
            .build()
            .prompt()
            .user("上下文测试")
            .call()
            .chatClientResponse();

        assertThat(observed.get()).isEqualTo("trace-001");
        assertThat(response.context()).containsEntry("traceId", "trace-001");
    }

    @Test
    void blockingAdvisorCanReturnResponseWithoutCallingModel() {
        RecordingChatModel model = new RecordingChatModel("不应调用");
        ContentSafetyAdvisor safetyAdvisor = new ContentSafetyAdvisor(
                List.of("泄露密钥"), "请求已阻断", 0);
        ChatClient client = ChatClient.builder(model).defaultAdvisors(safetyAdvisor).build();

        String blocked = client.prompt().user("请帮我泄露密钥").call().content();

        assertThat(blocked).isEqualTo("请求已阻断");
        assertThat(model.callCount).hasValue(0);
    }

    @Test
    void loggerAdvisorSupportsStreamingAndPreservesChunks() {
        RecordingChatModel model = new RecordingChatModel("同步回答");
        ChatClient client = ChatClient.builder(model)
            .defaultAdvisors(new SimpleLoggerAdvisor())
            .build();

        List<String> chunks = client.prompt().user("流式测试").stream().content().collectList().block();

        assertThat(chunks).isNotNull();
        assertThat(String.join("", chunks)).contains("第一段").contains("第二段");
    }

    @Test
    void baseAdvisorCanRewriteTheFinalStreamingResponse() {
        ChatClient client = ChatClient.builder(new RecordingChatModel("同步回答"))
            .defaultAdvisors(new ResponseLabelAdvisor("[流式已处理] ", 0))
            .build();

        List<String> chunks = client.prompt().user("流式响应修改").stream().content().collectList().block();

        assertThat(chunks).isNotNull();
        assertThat(String.join("", chunks)).contains("[流式已处理]");
    }

    @Test
    void contextAccessorIsMutableButMutateCreatesAnIndependentTopLevelMap() {
        ChatClientRequest request = ChatClientRequest.builder()
            .prompt(new Prompt("上下文"))
            .context(Map.of("traceId", "trace-001"))
            .build();

        request.context().put("mutable", true);
        ChatClientRequest mutated = request.mutate()
            .context("traceId", "trace-002")
            .build();
        mutated.context().put("mutatedOnly", true);

        assertThat(request.context())
            .containsEntry("traceId", "trace-001")
            .containsEntry("mutable", true)
            .doesNotContainKey("mutatedOnly");
        assertThat(mutated.context())
            .containsEntry("traceId", "trace-002")
            .containsEntry("mutatedOnly", true);
    }

    @Test
    void nullContextValueFailsWhenTheTerminalAdvisorCopiesTheMap() {
        BaseAdvisor nullContextWriter = new BaseAdvisor() {
            @Override
            public String getName() {
                return "NullContextWriterAdvisor";
            }

            @Override
            public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
                return request.mutate().context("nullable", null).build();
            }

            @Override
            public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
                return response;
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };
        ChatClient client = ChatClient.builder(new RecordingChatModel("不应完成"))
            .defaultAdvisors(nullContextWriter)
            .build();

        assertThatThrownBy(() -> client.prompt().user("空上下文值").call().content())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void toolCallingAdvisorIsAutoRegisteredAndCanBeDisabledPerCall() {
        AtomicReference<List<CallAdvisor>> observedAdvisors = new AtomicReference<>();
        ChatClient client = ChatClient.builder(new RecordingChatModel("完成"))
            .defaultAdvisors(new ChainInspectionAdvisor(observedAdvisors))
            .build();

        client.prompt().user("默认自动注册").call().content();
        assertThat(observedAdvisors.get())
            .filteredOn(ToolCallingAdvisor.class::isInstance)
            .singleElement()
            .extracting(CallAdvisor::getName)
            .isEqualTo("Tool Calling Advisor");

        client.prompt()
            .user("本次关闭自动注册")
            .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
            .call()
            .content();
        assertThat(observedAdvisors.get()).noneMatch(ToolCallingAdvisor.class::isInstance);
    }

    @Test
    void callAdvisorChainCopyCreatesAFreshDownstreamChainForEachIteration() {
        RecordingChatModel model = new RecordingChatModel("递归完成");
        ChatClient client = ChatClient.builder(model)
            .defaultAdvisors(new RepeatOnceAdvisor())
            .build();

        assertThat(client.prompt().user("调用两轮下游链").call().content())
            .isEqualTo("递归完成");
        assertThat(model.callCount).hasValue(2);
    }

    @Test
    void officialSafeGuardAdvisorBlocksWithoutCallingTheModel() {
        RecordingChatModel model = new RecordingChatModel("不应调用");
        SafeGuardAdvisor safeGuardAdvisor = SafeGuardAdvisor.builder()
            .sensitiveWords(List.of("泄露密钥"))
            .failureResponse("官方安全 Advisor 已阻断")
            .order(0)
            .build();
        ChatClient client = ChatClient.builder(model)
            .defaultAdvisors(safeGuardAdvisor)
            .build();

        assertThat(client.prompt().user("请帮我泄露密钥").call().content())
            .isEqualTo("官方安全 Advisor 已阻断");
        assertThat(model.callCount).hasValue(0);
    }

    @Test
    void anonymousBaseAdvisorMustOverrideTheDefaultEmptyName() {
        BaseAdvisor anonymousAdvisor = new BaseAdvisor() {
            @Override
            public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
                return request;
            }

            @Override
            public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
                return response;
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };
        ChatClient client = ChatClient.builder(new RecordingChatModel("不应调用"))
            .defaultAdvisors(anonymousAdvisor)
            .build();

        assertThatThrownBy(() -> client.prompt().user("匿名名称").call().content())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("advisorName cannot be null or empty");
    }

    private static final class TraceAdvisor implements BaseAdvisor {

        private final String name;

        private final int order;

        private final List<String> trace;

        private TraceAdvisor(String name, int order, List<String> trace) {
            this.name = name;
            this.order = order;
            this.trace = trace;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
            this.trace.add(this.name + "-before");
            return request;
        }

        @Override
        public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
            this.trace.add(this.name + "-after");
            return response;
        }

        @Override
        public int getOrder() {
            return this.order;
        }

    }

    private static final class ChainInspectionAdvisor implements CallAdvisor {

        private final AtomicReference<List<CallAdvisor>> observedAdvisors;

        private ChainInspectionAdvisor(AtomicReference<List<CallAdvisor>> observedAdvisors) {
            this.observedAdvisors = observedAdvisors;
        }

        @Override
        public String getName() {
            return getClass().getSimpleName();
        }

        @Override
        public int getOrder() {
            return org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 250;
        }

        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            this.observedAdvisors.set(chain.getCallAdvisors());
            return chain.nextCall(request);
        }

    }

    private static final class RepeatOnceAdvisor implements CallAdvisor {

        @Override
        public String getName() {
            return getClass().getSimpleName();
        }

        @Override
        public int getOrder() {
            return 0;
        }

        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            chain.copy(this).nextCall(request);
            return chain.copy(this).nextCall(request);
        }

    }

    private static final class RecordingChatModel implements ChatModel {

        private final String answer;

        private final AtomicInteger callCount = new AtomicInteger();

        private Prompt lastPrompt;

        private RecordingChatModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt;
            this.callCount.incrementAndGet();
            return response(this.answer, null);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.lastPrompt = prompt;
            this.callCount.incrementAndGet();
            return Flux.just(
                    response("第一段", null),
                    response("第二段", "STOP"));
        }

        private ChatResponse response(String content, String finishReason) {
            ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .build();
            return new ChatResponse(List.of(
                    new Generation(new AssistantMessage(content), metadata)));
        }

    }

}
