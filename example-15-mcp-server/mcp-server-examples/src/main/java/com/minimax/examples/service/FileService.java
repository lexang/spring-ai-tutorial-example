package com.minimax.examples.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.minimax.examples.service.ModelClasses.FileListResult;
import com.minimax.examples.service.ModelClasses.WriteFileResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 只允许访问配置目录下的单层文件，避免把 MCP 工具暴露成任意文件系统访问。
 */
@Service
@ConditionalOnProperty(name = "app.file-tools-enabled", havingValue = "true")
public class FileService {

    private final Path dataDirectory;

    @Autowired
    public FileService(@Value("$" + "{app.data-directory}") String dataDirectory) throws IOException {
        this(Path.of(dataDirectory));
    }

    public FileService(Path dataDirectory) throws IOException {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        Files.createDirectories(this.dataDirectory);
    }

    @McpTool(name = "list-files", description = "列出 MCP 数据目录中的文件")
    public FileListResult listFiles() throws IOException {
        try (Stream<Path> paths = Files.list(this.dataDirectory)) {
            List<String> names = paths.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .sorted()
                .toList();
            return new FileListResult(names);
        }
    }

    @McpTool(name = "read-file", description = "读取数据目录中的文本文件")
    public String readFile(
            @McpToolParam(description = "文件名，不允许目录分隔符", required = true) String fileName)
            throws IOException {
        Path path = resolveSafe(fileName);
        if (!Files.isRegularFile(path)) {
            throw new IOException("文件不存在: " + fileName);
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return content.length() > 10_000
            ? content.substring(0, 10_000) + "\n[内容已截断]"
            : content;
    }

    @McpTool(name = "write-file", description = "写入数据目录中的文本文件")
    public WriteFileResult writeFile(
            @McpToolParam(description = "文件名，不允许目录分隔符", required = true) String fileName,
            @McpToolParam(description = "UTF-8 文本内容", required = true) String content)
            throws IOException {
        Path path = resolveSafe(fileName);
        String safeContent = content == null ? "" : content;
        Files.writeString(path, safeContent, StandardCharsets.UTF_8);
        return new WriteFileResult(fileName, safeContent.getBytes(StandardCharsets.UTF_8).length, "文件写入成功");
    }

    @McpTool(name = "delete-file", description = "删除数据目录中的文本文件")
    public String deleteFile(
            @McpToolParam(description = "文件名，不允许目录分隔符", required = true) String fileName)
            throws IOException {
        Path path = resolveSafe(fileName);
        if (!Files.deleteIfExists(path)) {
            throw new IOException("文件不存在: " + fileName);
        }
        return "文件删除成功: " + fileName;
    }

    public Path dataDirectory() {
        return this.dataDirectory;
    }

    private Path resolveSafe(String fileName) {
        if (fileName == null || fileName.isBlank()
                || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("文件名不能为空，且不能包含路径分隔符或 ..");
        }
        Path path = this.dataDirectory.resolve(fileName).normalize();
        if (!path.startsWith(this.dataDirectory) || !path.getParent().equals(this.dataDirectory)) {
            throw new IllegalArgumentException("文件路径超出数据目录");
        }
        return path;
    }

}
