package com.example.springai.reader;

import com.example.springai.support.DocumentResponses;
import com.example.springai.support.EtlOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonMetadataGenerator;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 演示 Spring AI 2.0 官方 ETL 文档列出的主要 DocumentReader。
 *
 * <p>所有 Reader 都只读取应用预先配置的 classpath 资源。生产应用不要把用户提交的任意 URL
 * 直接交给 Resource 或 Reader，以免引入 SSRF 和本地文件访问风险。</p>
 */
@RestController
@RequestMapping("/api/etl/reader")
public class DocumentReaderController {

    private static final Logger log = LoggerFactory.getLogger(DocumentReaderController.class);

    private static final Pattern JSON_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final Resource textResource;

    private final Resource jsonResource;

    private final Resource markdownResource;

    private final Resource htmlResource;

    private final Resource pdfResource;

    public DocumentReaderController(
            @Value("classpath:samples/sample.txt") Resource textResource,
            @Value("classpath:samples/sample.json") Resource jsonResource,
            @Value("classpath:samples/sample.md") Resource markdownResource,
            @Value("classpath:samples/sample.html") Resource htmlResource,
            @Value("classpath:samples/sample.pdf") Resource pdfResource) {
        this.textResource = textResource;
        this.jsonResource = jsonResource;
        this.markdownResource = markdownResource;
        this.htmlResource = htmlResource;
        this.pdfResource = pdfResource;
    }

    @GetMapping("/text")
    public Map<String, Object> readText() {
        TextReader reader = new TextReader(this.textResource);
        reader.getCustomMetadata().put("type", "tutorial");

        List<Document> documents = reader.read();
        log.info("TextReader 读取完成，共 {} 个文档", documents.size());

        Map<String, Object> result = DocumentResponses.result(documents, 300);
        result.put("reader", "TextReader");
        result.put("note", "TextReader 默认按 UTF-8 将整个资源读成一个 Document，并自动写入 charset 与 source 元数据。");
        return result;
    }

    @GetMapping("/json")
    public Map<String, Object> readJson(@RequestParam(defaultValue = "title,content") String keys) {
        String[] extractedKeys = parseJsonKeys(keys);
        JsonReader reader = new JsonReader(this.jsonResource, extractedKeys);
        List<Document> documents = reader.read();

        Map<String, Object> result = DocumentResponses.result(documents, 1000);
        result.put("reader", "JsonReader");
        result.put("extractedKeys", List.of(extractedKeys));
        return result;
    }

    @GetMapping("/json/metadata")
    public Map<String, Object> readJsonWithMetadata() {
        JsonMetadataGenerator metadataGenerator = jsonMap -> {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", "sample.json");
            copyIfPresent(jsonMap, metadata, "category");
            copyIfPresent(jsonMap, metadata, "author");
            copyIfPresent(jsonMap, metadata, "date");
            return metadata;
        };

        JsonReader reader = new JsonReader(this.jsonResource, metadataGenerator, "title", "content");
        List<Document> documents = reader.read();

        Map<String, Object> result = DocumentResponses.result(documents, 1000);
        result.put("reader", "JsonReader + JsonMetadataGenerator");
        result.put("metadataFields", List.of("source", "category", "author", "date"));
        return result;
    }

    @GetMapping("/json/pointer")
    public Map<String, Object> readJsonWithPointer(@RequestParam(defaultValue = "/0") String pointer) {
        validateJsonPointer(pointer);
        JsonReader reader = new JsonReader(this.jsonResource, "title", "content");

        try {
            List<Document> documents = reader.get(pointer);
            Map<String, Object> result = DocumentResponses.result(documents, 1000);
            result.put("reader", "JsonReader");
            result.put("pointer", pointer);
            result.put("standard", "RFC 6901 JSON Pointer");
            return result;
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JSON Pointer 无效或在示例文档中不存在：" + pointer, exception);
        }
    }

    @GetMapping("/markdown")
    public Map<String, Object> readMarkdown(
            @RequestParam(defaultValue = "true") boolean splitByHorizontalRule,
            @RequestParam(defaultValue = "true") boolean includeCodeBlock) {

        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(splitByHorizontalRule)
                .withIncludeCodeBlock(includeCodeBlock)
                .withIncludeBlockquote(true)
                .withAdditionalMetadata("source", "sample.md")
                .build();

        List<Document> documents = new MarkdownDocumentReader(this.markdownResource, config).read();
        Map<String, Object> result = DocumentResponses.result(documents, 1000);
        result.put("reader", "MarkdownDocumentReader");
        result.put("splitByHorizontalRule", splitByHorizontalRule);
        result.put("includeCodeBlock", includeCodeBlock);
        return result;
    }

    @GetMapping("/html")
    public Map<String, Object> readHtml(
            @RequestParam(defaultValue = "article .content") String selector,
            @RequestParam(defaultValue = "false") boolean groupByElement) {

        validateSelector(selector);
        JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
                .charset("UTF-8")
                .selector(selector)
                .separator("\n\n")
                .groupByElement(groupByElement)
                .includeLinkUrls(true)
                .metadataTags(List.of("author", "date", "description"))
                .additionalMetadata("source", "sample.html")
                .build();

        List<Document> documents = new JsoupDocumentReader(this.htmlResource, config).read();
        Map<String, Object> result = DocumentResponses.result(documents, 600);
        result.put("reader", "JsoupDocumentReader");
        result.put("selector", selector);
        result.put("groupByElement", groupByElement);
        return result;
    }

    @GetMapping("/pdf/page")
    public Map<String, Object> readPdfByPage(
            @RequestParam(defaultValue = "1") int pagesPerDocument,
            @RequestParam(defaultValue = "0") int pageTopMargin) {

        requireRange("pagesPerDocument", pagesPerDocument, 1, 20);
        requireRange("pageTopMargin", pageTopMargin, 0, 500);
        requireResource(this.pdfResource, "PDF 示例文件不存在。");

        try {
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPagesPerDocument(pagesPerDocument)
                    .withPageTopMargin(pageTopMargin)
                    .build();
            List<Document> documents = new PagePdfDocumentReader(this.pdfResource, config).read();

            Map<String, Object> result = DocumentResponses.result(documents, 500);
            result.put("reader", "PagePdfDocumentReader");
            result.put("pagesPerDocument", pagesPerDocument);
            result.put("pageTopMargin", pageTopMargin);
            return result;
        }
        catch (RuntimeException exception) {
            throw new EtlOperationException("按页读取 PDF 失败，请确认文件格式和页边距参数有效。", exception);
        }
    }

    @GetMapping("/pdf/paragraph")
    public Map<String, Object> readPdfByParagraph() {
        requireResource(this.pdfResource, "PDF 示例文件不存在。");

        try {
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageTopMargin(0)
                    .build();
            List<Document> documents = new ParagraphPdfDocumentReader(this.pdfResource, config).read();

            Map<String, Object> result = DocumentResponses.result(documents, 500);
            result.put("reader", "ParagraphPdfDocumentReader");
            result.put("note", "该 Reader 依赖 PDF catalog/目录结构；并非所有 PDF 都能按段落读取。");
            return result;
        }
        catch (RuntimeException exception) {
            throw new EtlOperationException(
                    "按段落读取 PDF 失败；该文件可能没有可用目录结构，请改用 /pdf/page。", exception);
        }
    }

    @GetMapping("/tika")
    public Map<String, Object> readWithTika() {
        try {
            List<Document> documents = new TikaDocumentReader(this.htmlResource).read();
            Map<String, Object> result = DocumentResponses.result(documents, 600);
            result.put("reader", "TikaDocumentReader");
            result.put("note", "Tika 可识别 DOCX、PPTX、XLSX、PDF、HTML、RTF、ODT 等多种格式；本端点使用固定 HTML 样例。");
            return result;
        }
        catch (RuntimeException exception) {
            throw new EtlOperationException("Tika 无法读取示例文档。", exception);
        }
    }

    private String[] parseJsonKeys(String keys) {
        List<String> parsedKeys = Arrays.stream(keys.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .distinct()
                .toList();

        if (parsedKeys.isEmpty() || parsedKeys.size() > 10) {
            throw new IllegalArgumentException("keys 必须包含 1 到 10 个逗号分隔的 JSON 字段名。");
        }
        if (parsedKeys.stream().anyMatch(key -> !JSON_KEY_PATTERN.matcher(key).matches())) {
            throw new IllegalArgumentException("JSON 字段名只能包含字母、数字、下划线和连字符，且长度不超过 64。");
        }
        return parsedKeys.toArray(String[]::new);
    }

    private void validateJsonPointer(String pointer) {
        if (pointer.length() > 256 || pointer.contains("\r") || pointer.contains("\n")) {
            throw new IllegalArgumentException("JSON Pointer 长度不能超过 256，且不能包含换行符。");
        }
        if (!pointer.isEmpty() && !pointer.startsWith("/")) {
            throw new IllegalArgumentException("JSON Pointer 必须为空字符串（根节点）或以 / 开头。");
        }
    }

    private void validateSelector(String selector) {
        if (selector.isBlank() || selector.length() > 120 || selector.contains("\r") || selector.contains("\n")) {
            throw new IllegalArgumentException("CSS selector 不能为空、不能换行，且长度不能超过 120。");
        }
    }

    private void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " 必须在 " + min + " 到 " + max + " 之间。");
        }
    }

    private void requireResource(Resource resource, String message) {
        if (!resource.exists()) {
            throw new EtlOperationException(message);
        }
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }
}
