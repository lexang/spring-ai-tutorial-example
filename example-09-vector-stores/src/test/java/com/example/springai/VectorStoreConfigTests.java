package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VectorStoreConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(VectorStoreConfig.class)
            .withBean(EmbeddingModel.class, () -> mock(EmbeddingModel.class))
            .withPropertyValues("app.vector-store.initialize-sample-data=false");

    @Test
    void shouldCreateSimpleVectorStoreByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(VectorStore.class);
            assertThat(context.getBean(VectorStore.class)).isInstanceOf(SimpleVectorStore.class);
        });
    }

    @Test
    void shouldLeaveVectorStoreToPgvectorAutoConfigurationWhenSelected() {
        this.contextRunner
                .withPropertyValues("app.vector-store.type=pgvector")
                .run(context -> assertThat(context).doesNotHaveBean(VectorStore.class));
    }
}
