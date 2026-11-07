package com.example.springai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.DocumentWriter;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EtlInterfaceContractTest {

    @Test
    void expressiveMethodsDelegateToJavaFunctionalInterfaces() {
        DocumentReader reader = () -> List.of(new Document("原始内容"));
        DocumentTransformer transformer = documents -> documents.stream()
                .map(document -> new Document(document.getText() + "-已转换"))
                .toList();
        AtomicReference<List<Document>> written = new AtomicReference<>();
        DocumentWriter writer = written::set;

        List<Document> transformed = transformer.transform(reader.read());
        writer.write(transformed);

        assertThat(written.get()).singleElement()
                .satisfies(document -> assertThat(document.getText()).isEqualTo("原始内容-已转换"));
    }
}
