package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "server.port=0"
})
class EtlOfflineApplicationTest {

    @Test
    void readerTransformerAndFileWriterCanStartWithoutModels(ApplicationContext context) {
        assertThat(context.getBean("documentReaderController")).isNotNull();
        assertThat(context.getBean("documentTransformerController")).isNotNull();
        assertThat(context.getBean("documentWriterController")).isNotNull();
        assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
    }
}
