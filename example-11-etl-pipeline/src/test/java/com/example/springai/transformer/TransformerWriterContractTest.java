package com.example.springai.transformer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.transformer.ContentFormatTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.writer.FileDocumentWriter;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransformerWriterContractTest {

    @Test
    void smallTextIsNotSplitOnlyBecauseItContainsPunctuation() {
        Document source = Document.builder()
                .text("第一句。第二句！第三句？")
                .metadata("source", "small.txt")
                .build();
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(1000)
                .withMinChunkSizeChars(0)
                .withPunctuationMarks(List.of('。', '！', '？'))
                .build();

        List<Document> chunks = splitter.split(source);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.getText()).isEqualTo(source.getText());
            assertThat(chunk.getMetadata())
                    .containsEntry("source", "small.txt")
                    .containsEntry("parent_document_id", source.getId())
                    .containsEntry("chunk_index", 0)
                .containsEntry("total_chunks", 1);
        });
    }

    @Test
    void maxNumChunksLimitsTheMainLoopButRemainingTextCanBecomeAnotherChunk() {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(1)
                .withMinChunkSizeChars(0)
                .withMinChunkLengthToEmbed(0)
                .withMaxNumChunks(1)
                .withPunctuationMarks(List.of('.'))
                .build();

        List<Document> chunks = splitter.split(new Document("token ".repeat(40)));

        // 2.0 implementation appends the unprocessed tail after the guarded loop.
        assertThat(chunks).hasSize(2);
    }

    @Test
    void splitterCopiesMetadataAndContentFormatterToEveryChunk() {
        DefaultContentFormatter formatter = DefaultContentFormatter.builder()
                .withMetadataTemplate("{key}={value}")
                .withTextTemplate("META[{metadata_string}] TEXT[{content}]")
                .build();
        Document source = Document.builder()
                .text("Spring AI 数据处理。".repeat(120))
                .metadata("source", "long.txt")
                .build();
        source.setContentFormatter(formatter);

        List<Document> chunks = TokenTextSplitter.builder()
                .withChunkSize(40)
                .withMinChunkSizeChars(10)
                .withPunctuationMarks(List.of('。'))
                .build()
                .split(source);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getMetadata()).containsEntry("source", "long.txt");
            assertThat(chunk.getFormattedContent(MetadataMode.ALL))
                    .startsWith("META[")
                    .contains("source=long.txt", "TEXT[");
        });
    }

    @Test
    void contentFormatTransformerChangesFormatterWithoutChangingRawText() {
        Document document = new Document("正文", java.util.Map.of("title", "标题"));
        DefaultContentFormatter formatter = DefaultContentFormatter.builder()
                .withExcludedInferenceMetadataKeys("title")
                .build();

        new ContentFormatTransformer(formatter).transform(List.of(document));

        assertThat(document.getText()).isEqualTo("正文");
        assertThat(document.getFormattedContent(MetadataMode.ALL)).contains("title: 标题", "正文");
        assertThat(document.getFormattedContent(MetadataMode.INFERENCE))
                .doesNotContain("title: 标题")
                .contains("正文");
    }

    @Test
    void fileDocumentWriterSupportsMarkersMetadataAndAppend(@TempDir Path tempDirectory) throws Exception {
        Path output = tempDirectory.resolve("output.txt");
        List<Document> documents = List.of(Document.builder()
                .text("第一段正文")
                .metadata("page_number", 1)
                .metadata("end_page_number", 1)
                .metadata("source", "sample.pdf")
                .build());

        new FileDocumentWriter(output.toString(), true, MetadataMode.ALL, false).write(documents);
        new FileDocumentWriter(output.toString(), false, MetadataMode.NONE, true)
                .write(List.of(new Document("追加正文")));

        String written = Files.readString(output, Charset.defaultCharset());
        assertThat(written)
                .contains("### Doc: 0, pages:[1,1]")
                .contains("source: sample.pdf")
                .contains("第一段正文")
                .contains("追加正文");
    }
}
