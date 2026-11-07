package com.example.springai.pipeline;

import com.example.springai.pipeline.EtlPipelineService.PipelineRun;
import com.example.springai.support.DocumentResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 完整 ETL 流程的 HTTP 入口。
 */
@RestController
@RequestMapping("/api/etl/pipeline")
public class EtlPipelineController {

    private static final Logger log = LoggerFactory.getLogger(EtlPipelineController.class);

    private final EtlPipelineService pipelineService;

    public EtlPipelineController(EtlPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping("/process")
    public Map<String, Object> processDocuments(
            @RequestParam(defaultValue = "text") String format,
            @RequestParam(defaultValue = "false") boolean enableEnrich,
            @RequestParam(defaultValue = "3") int enrichLimit) {

        PipelineRun run = this.pipelineService.process(format, enableEnrich, enrichLimit);
        log.info("ETL 完成：格式={}，读取={}，转换={}，增强={}", run.format(),
                run.extractedDocumentCount(), run.transformedDocumentCount(), run.enrichedDocumentCount());
        return toResponse(run, enableEnrich);
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestParam(defaultValue = "text") String format) {
        PipelineRun run = pipelineService.preview(format);
        Map<String, Object> result = toResponse(run, false);
        result.put("pipeline", List.of("Extract", "Transform"));
        result.put("loaded", false);
        result.put("note", "只预览固定 classpath 样例的分块，不调用模型、不写入向量库");
        return result;
    }

    @PostMapping("/multi-format")
    public Map<String, Object> processMultiFormatDocuments() {
        List<PipelineRun> runs = List.of("text", "json", "markdown").stream()
                .map(format -> this.pipelineService.process(format, false, 0))
                .toList();

        Map<String, Integer> counts = new LinkedHashMap<>();
        runs.forEach(run -> counts.put(run.format(), run.transformedDocumentCount()));
        int total = runs.stream().mapToInt(PipelineRun::transformedDocumentCount).sum();

        return Map.of(
                "formats", counts,
                "totalLoadedDocuments", total,
                "status", "三种固定 classpath 样例已完成读取、分割和向量写入。"
        );
    }

    @GetMapping("/search")
    public Map<String, Object> searchDocuments(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {

        List<Document> documents = this.pipelineService.search(query, topK);
        Map<String, Object> result = DocumentResponses.result(documents, 1000);
        result.put("query", query.trim());
        result.put("topK", topK);
        return result;
    }

    @DeleteMapping("/clear")
    public Map<String, Object> clearVectorStore() {
        int deleted = this.pipelineService.clearLoadedDocuments();
        return Map.of(
                "deletedDocumentCount", deleted,
                "remainingTrackedDocuments", this.pipelineService.loadedDocumentCount(),
                "note", "只删除本次应用进程通过 ETL 端点写入并跟踪到的文档 ID。"
        );
    }

    @PostMapping("/functional")
    public Map<String, Object> functionalStylePipeline() {
        PipelineRun run = this.pipelineService.functionalPipeline();
        Map<String, Object> result = toResponse(run, false);
        result.put("code", "vectorStore.write(splitter.split(reader.read()))");
        result.put("style", "DocumentReader → DocumentTransformer → DocumentWriter");
        return result;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStatistics() {
        return Map.of(
                "vectorStore", this.pipelineService.vectorStoreName(),
                "trackedDocumentCount", this.pipelineService.loadedDocumentCount(),
                "scope", "只统计当前进程经本示例服务成功写入的文档，不代表外部向量库全量数据。"
        );
    }

    private Map<String, Object> toResponse(PipelineRun run, boolean enrichmentRequested) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", run.format());
        result.put("pipeline", List.of("Extract", "Transform", "Load"));
        result.put("statistics", Map.of(
                "extractedDocuments", run.extractedDocumentCount(),
                "transformedDocuments", run.transformedDocumentCount(),
                "enrichedDocuments", run.enrichedDocumentCount(),
                "enrichmentRequested", enrichmentRequested
        ));
        result.put("sampleDocuments", DocumentResponses.views(run.documents().stream().limit(3).toList(), 500));
        return result;
    }
}
