package com.example.springai;

import java.util.List;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.observation.ChatClientObservationDocumentation;
import org.springframework.ai.document.Document;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import com.example.springai.advisor.RequestContextAdvisor;
import com.example.springai.model.DeterministicEmbeddingModel;
import com.example.springai.model.OfflineChatModel;

import static org.assertj.core.api.Assertions.assertThat;

/** 离线验证官方 observation 名称和生命周期。 */
class ObservabilityContractTest {

    @Test
    void syncChatClientAdvisorAndModelObservationsAreRecorded() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        ChatClient client = client(registry);

        assertThat(client.prompt("观察同步调用").call().content()).contains("离线模型响应");

        TestObservationRegistryAssert.assertThat(registry)
            .doesNotHaveAnyRemainingCurrentObservation()
            .hasObservationWithNameEqualTo("spring.ai.chat.client")
            .that()
            .hasLowCardinalityKeyValue("spring.ai.kind", "chat_client")
            .hasLowCardinalityKeyValue(ChatClientObservationDocumentation.LowCardinalityKeyNames.STREAM.asString(),
                "false")
            .hasBeenStarted()
            .hasBeenStopped();
        TestObservationRegistryAssert.assertThat(registry)
            .hasObservationWithNameEqualTo("spring.ai.advisor")
            .that()
            .hasLowCardinalityKeyValue("spring.ai.advisor.name", "request-context")
            .hasLowCardinalityKeyValue("gen_ai.operation.name", AiOperationType.FRAMEWORK.value())
            .hasBeenStarted()
            .hasBeenStopped();
        TestObservationRegistryAssert.assertThat(registry)
            .hasObservationWithNameEqualTo("gen_ai.client.operation")
            .that()
            .hasLowCardinalityKeyValue("gen_ai.operation.name", AiOperationType.CHAT.value())
            .hasLowCardinalityKeyValue("gen_ai.system", "offline")
            .hasLowCardinalityKeyValue("gen_ai.response.model", "offline-demo")
            .hasBeenStarted()
            .hasBeenStopped();
    }

    @Test
    void streamObservationStopsAfterFluxCompletion() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        ChatClient client = client(registry);

        List<String> chunks = client.prompt("观察流式调用").stream().content().collectList().block();

        assertThat(chunks).containsExactly("离线流式响应：第一段。", "第二段。");
        TestObservationRegistryAssert.assertThat(registry)
            .doesNotHaveAnyRemainingCurrentObservation()
            .hasObservationWithNameEqualTo("spring.ai.chat.client")
            .that()
            .hasLowCardinalityKeyValue(ChatClientObservationDocumentation.LowCardinalityKeyNames.STREAM.asString(),
                "true")
            .hasBeenStarted()
            .hasBeenStopped();
    }

    @Test
    void vectorStoreUsesTheSameObservationRegistry() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        SimpleVectorStore store = SimpleVectorStore.builder(new DeterministicEmbeddingModel())
            .observationRegistry(registry)
            .build();

        store.add(List.of(Document.builder().id("doc-1").text("观察向量库").build()));
        registry.clear();
        assertThat(store.similaritySearch(SearchRequest.builder().query("向量库").topK(1).build())).hasSize(1);

        TestObservationRegistryAssert.assertThat(registry)
            .doesNotHaveAnyRemainingCurrentObservation()
            .hasObservationWithNameEqualTo("db.vector.client.operation")
            .that()
            .hasLowCardinalityKeyValue("db.operation.name", "query")
            .hasLowCardinalityKeyValue("db.system", "simple")
            .hasBeenStarted()
            .hasBeenStopped();
    }

    private ChatClient client(TestObservationRegistry registry) {
        return ChatClient.builder(new OfflineChatModel(registry), registry, null, null)
            .defaultAdvisors(advisors -> {
                advisors.advisors(new RequestContextAdvisor());
                advisors.param(ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(), false);
            })
            .build();
    }

}
