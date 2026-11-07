package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationHandler;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatCode;

/** 验证默认配置不需要外部服务即可启动。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ObservabilityApplicationTest {
    @Autowired SimpleVectorStore vectorStore;
    @Autowired ObservationRegistry observationRegistry;

    @Test
    void configuredVectorStoreDoesNotAttachPrivateQueryToTags() {
        AtomicReference<String> tags = new AtomicReference<>();
        observationRegistry.observationConfig().observationHandler(new ObservationHandler<VectorStoreObservationContext>() {
            @Override public boolean supportsContext(Observation.Context context) {
                return context instanceof VectorStoreObservationContext;
            }
            @Override public void onStop(VectorStoreObservationContext context) {
                tags.set(context.getHighCardinalityKeyValues().toString());
            }
        });
        vectorStore.similaritySearch(SearchRequest.builder().query("private-query-sentinel").topK(1).build());
        org.assertj.core.api.Assertions.assertThat(tags.get()).isNotNull().doesNotContain("private-query-sentinel", "db.vector.query.content");
    }

    @Test
    void contextLoadsWithoutExternalCredentials() {
        assertThatCode(() -> {
            // SpringBootTest 已完成上下文装配；此处保留明确的可读断言。
        }).doesNotThrowAnyException();
    }

}
