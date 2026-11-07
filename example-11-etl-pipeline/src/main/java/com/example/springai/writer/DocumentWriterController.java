package com.example.springai.writer;

import com.example.springai.support.EtlOperationException;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.writer.FileDocumentWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 演示 FileDocumentWriter。输出文件名固定，防止请求参数造成任意路径写入。
 */
@RestController
@RequestMapping("/api/etl/writer")
public class DocumentWriterController {

    private final Resource textResource;

    private final Path outputDirectory;

    private final ReentrantLock writeLock = new ReentrantLock();

    public DocumentWriterController(
            @Value("classpath:samples/sample.txt") Resource textResource,
            @Value("${app.etl.output-directory}") String outputDirectory) {
        this.textResource = textResource;
        this.outputDirectory = Path.of(outputDirectory).toAbsolutePath().normalize();
    }

    @PostMapping("/file")
    public Map<String, Object> writeFile(
            @RequestParam(defaultValue = "true") boolean markers,
            @RequestParam(defaultValue = "NONE") String metadataMode,
            @RequestParam(defaultValue = "false") boolean append) {

        MetadataMode parsedMode = parseMetadataMode(metadataMode);
        this.writeLock.lock();
        try {
            Path outputFile = prepareOutputFile();
            List<Document> documents = TokenTextSplitter.builder()
                    .withChunkSize(120)
                    .withMinChunkSizeChars(40)
                    .withMinChunkLengthToEmbed(5)
                    .withMaxNumChunks(100)
                    .withKeepSeparator(true)
                    .withPunctuationMarks(List.of('.', '?', '!', '\n', '。', '？', '！'))
                    .build()
                    .split(new TextReader(this.textResource).read());

            for (int index = 0; index < documents.size(); index++) {
                Document document = documents.get(index);
                document.getMetadata().put(FileDocumentWriter.METADATA_START_PAGE_NUMBER, index + 1);
                document.getMetadata().put(FileDocumentWriter.METADATA_END_PAGE_NUMBER, index + 1);
                document.getMetadata().put("source", "sample.txt");
            }

            new FileDocumentWriter(outputFile.toString(), markers, parsedMode, append).write(documents);
            return Map.of(
                    "writer", "FileDocumentWriter",
                    "documentCount", documents.size(),
                    "markers", markers,
                    "metadataMode", parsedMode.name(),
                    "append", append,
                    "outputFile", outputFile.toString(),
                    "fileSize", Files.size(outputFile),
                    "encoding", Charset.defaultCharset().name()
            );
        }
        catch (EtlOperationException exception) {
            throw exception;
        }
        catch (IOException | RuntimeException exception) {
            throw new EtlOperationException("写入 ETL 示例文件失败，请检查输出目录权限。", exception);
        }
        finally {
            this.writeLock.unlock();
        }
    }

    private Path prepareOutputFile() throws IOException {
        if (Files.isSymbolicLink(this.outputDirectory)) {
            throw new EtlOperationException("ETL 输出目录不能是符号链接。");
        }
        Files.createDirectories(this.outputDirectory);
        if (!Files.isDirectory(this.outputDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new EtlOperationException("ETL 输出路径必须是目录。");
        }

        Path outputFile = this.outputDirectory.resolve("etl-output.txt").normalize();
        if (!outputFile.startsWith(this.outputDirectory)) {
            throw new IllegalArgumentException("输出文件必须位于已配置的 ETL 输出目录中。");
        }
        if (Files.isSymbolicLink(outputFile)) {
            throw new EtlOperationException("ETL 输出文件不能是符号链接。");
        }
        return outputFile;
    }

    private MetadataMode parseMetadataMode(String metadataMode) {
        try {
            return MetadataMode.valueOf(metadataMode.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "metadataMode 仅支持 NONE、ALL、EMBED 或 INFERENCE。", exception);
        }
    }
}
