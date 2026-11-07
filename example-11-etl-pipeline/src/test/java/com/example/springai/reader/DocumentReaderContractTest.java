package com.example.springai.reader;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentReaderContractTest {

    @Test
    void textReaderUsesUtf8AndPreservesCustomMetadata() {
        ByteArrayResource resource = resource("第一行\n第二行", "sample.txt");
        TextReader reader = new TextReader(resource);
        reader.getCustomMetadata().put("category", "教程");

        List<Document> documents = reader.read();

        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document.getText()).isEqualTo("第一行\n第二行");
            assertThat(document.getMetadata())
                    .containsEntry(TextReader.CHARSET_METADATA, "UTF-8")
                    .containsEntry("category", "教程");
        });
    }

    @Test
    void jsonReaderSupportsSelectedFieldsMetadataGeneratorAndJsonPointer() {
        String json = """
                [
                  {"title":"第一篇","content":"正文一","category":"基础"},
                  {"title":"第二篇","content":"正文二","category":"进阶"}
                ]
                """;
        JsonReader reader = new JsonReader(
                resource(json, "sample.json"),
                jsonMap -> Map.of("category", jsonMap.get("category")),
                "title", "content"
        );

        List<Document> allDocuments = reader.read();
        List<Document> pointedDocuments = reader.get("/1");

        assertThat(allDocuments).hasSize(2);
        assertThat(allDocuments.get(0).getText()).contains("第一篇", "正文一");
        assertThat(allDocuments.get(0).getMetadata()).containsEntry("category", "基础");
        assertThat(pointedDocuments).singleElement()
                .satisfies(document -> assertThat(document.getText()).contains("第二篇", "正文二"));
        assertThatThrownBy(() -> reader.get("/99"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jsonReaderFallsBackToWholeObjectWhenSelectedKeysAreAbsent() {
        JsonReader reader = new JsonReader(resource("{\"title\":\"标题\",\"content\":\"正文\"}", "one.json"),
                "missing");

        assertThat(reader.read()).singleElement()
                .satisfies(document -> assertThat(document.getText()).contains("title", "正文"));
    }

    @Test
    void jsoupReaderSupportsGroupingMetadataAndLinks() {
        String html = """
                <html>
                  <head>
                    <title>ETL 示例</title>
                    <meta name="author" content="教程团队">
                  </head>
                  <body>
                    <section>第一节 <a href="https://spring.io/">Spring</a></section>
                    <section>第二节</section>
                  </body>
                </html>
                """;
        JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
                .selector("section")
                .groupByElement(true)
                .includeLinkUrls(true)
                .metadataTags(List.of("author"))
                .additionalMetadata("source", "sample.html")
                .build();

        List<Document> documents = new JsoupDocumentReader(resource(html, "sample.html"), config).read();

        assertThat(documents).hasSize(2);
        assertThat(documents).extracting(Document::getText)
                .containsExactly("第一节 Spring", "第二节");
        assertThat(documents.get(0).getMetadata())
                .containsEntry("title", "ETL 示例")
                .containsEntry("author", "教程团队")
                .containsEntry("source", "sample.html")
                .containsKey("linkUrls");
    }

    @Test
    void jsoupReaderDoesNotInventBaseUriForRelativeLinks() {
        String html = "<html><head><title>链接</title></head><body>"
                + "<p class='content'><a href='/relative'>相对链接</a></p>"
                + "</body></html>";
        JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
                .selector(".content")
                .includeLinkUrls(true)
                .build();

        List<Document> documents = new JsoupDocumentReader(resource(html, "links.html"), config).read();

        assertThat(documents).singleElement().satisfies(document -> {
            @SuppressWarnings("unchecked")
            List<String> links = (List<String>) document.getMetadata().get("linkUrls");
            assertThat(links).containsExactly("");
        });
    }

    @Test
    void markdownReaderCanSplitAtHorizontalRules() {
        String markdown = """
                # 第一部分

                正文一

                ---

                # 第二部分

                正文二
                """;
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withAdditionalMetadata("source", "sample.md")
                .build();

        List<Document> documents = new MarkdownDocumentReader(resource(markdown, "sample.md"), config).read();

        assertThat(documents).hasSizeGreaterThanOrEqualTo(2);
        assertThat(documents.stream().map(Document::getText).toList())
                .anySatisfy(text -> assertThat(text).contains("正文一"))
                .anySatisfy(text -> assertThat(text).contains("正文二"));
        assertThat(documents).allSatisfy(document ->
                assertThat(document.getMetadata()).containsEntry("source", "sample.md"));
    }

    @Test
    void markdownReaderStoresHeadingMetadataAndEndsFencedCodeDocument() {
        String markdown = "# 标题\n\n正文\n\n```java\n代码\n```\n\n后文";
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withIncludeCodeBlock(true)
                .build();

        List<Document> documents = new MarkdownDocumentReader(resource(markdown, "sample.md"), config).read();

        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).getText()).contains("正文", "代码");
        assertThat(documents.get(0).getMetadata())
                .containsEntry("title", "标题")
                .containsEntry("category", "code_block")
                .containsEntry("lang", "java");
        assertThat(documents.get(1).getText()).isEqualTo("后文");
    }

    @Test
    void tikaReaderReturnsOneDocumentWithOnlySourceMetadata() {
        String html = "<html><head><title>Tika</title></head><body><p>正文</p></body></html>";

        List<Document> documents = new TikaDocumentReader(resource(html, "sample.html")).read();

        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document.getText()).contains("正文");
            assertThat(document.getMetadata().get("source"))
                    .asString()
                    .startsWith("Invalid source URI:");
        });
    }

    @Test
    void pagePdfReaderOmitsEndPageMetadataForSinglePageDocuments() {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)
                .build();

        List<Document> documents = new PagePdfDocumentReader(
                new ClassPathResource("samples/sample.pdf"), config).read();

        assertThat(documents).hasSize(2);
        assertThat(documents).allSatisfy(document -> assertThat(document.getMetadata())
                .containsKey(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER)
                .doesNotContainKey(PagePdfDocumentReader.METADATA_END_PAGE_NUMBER));
    }

    @Test
    void paragraphPdfReaderRequiresPdfOutline() {
        assertThatThrownBy(() -> new ParagraphPdfDocumentReader(
                new ClassPathResource("samples/sample.pdf"),
                PdfDocumentReaderConfig.defaultConfig()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document outline");
    }

    private ByteArrayResource resource(String content, String description) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), description);
    }
}
