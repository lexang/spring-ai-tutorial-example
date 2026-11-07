package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件系统工具类
 *
 * 提供安全的文件系统操作功能
 *
 * 安全特性:
 * - 使用基础目录限制访问范围
 * - 路径验证防止路径遍历攻击
 */
public class FileSystemTools {

    private static final Logger log = LoggerFactory.getLogger(FileSystemTools.class);

    /** 规范化后的绝对基础目录。 */
    private final Path baseDir;

    /**
     * 构造函数
     *
     * @param baseDir 基础目录,所有操作限制在此目录下
     */
    public FileSystemTools(String baseDir) {
        if (baseDir == null || baseDir.isBlank()) {
            throw new IllegalArgumentException("基础目录不能为空");
        }

        Path configuredDir = Path.of(baseDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(configuredDir)) {
            throw new IllegalArgumentException("基础目录不存在或不是目录: " + configuredDir);
        }

        try {
            // 解析基础目录本身，后续的真实路径检查可以拦截目录内的符号链接越界。
            this.baseDir = configuredDir.toRealPath();
        }
        catch (IOException ex) {
            throw new IllegalArgumentException("无法解析基础目录: " + configuredDir, ex);
        }

        log.info("FileSystemTools 初始化,基础目录: {}", this.baseDir);
    }

    /**
     * 列出指定目录下的所有文件和文件夹
     *
     * @param directory 目录路径,相对于基础目录
     * @return 文件和文件夹列表
     */
    @Tool(description = "列出指定目录下的所有文件和文件夹")
    public List<String> listFiles(
            @ToolParam(description = "目录路径,相对于基础目录,例如: docs, src/main")
            String directory) {

        log.info("工具被调用: listFiles, 参数: directory={}", directory);

        try {
            Path path = resolvePath(directory);

            if (!Files.exists(path)) {
                return List.of("错误: 目录不存在");
            }

            if (!Files.isDirectory(path)) {
                return List.of("错误: 不是一个目录");
            }

            Path realPath = resolveRealPath(path);
            List<String> files;
            // Files.list() 返回惰性 Stream，必须显式关闭，避免重复调用时耗尽目录句柄。
            try (Stream<Path> entries = Files.list(realPath)) {
                files = entries
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .collect(Collectors.toList());
            }

            log.info("列出文件成功,共 {} 个文件", files.size());
            return files;

        }
        catch (SecurityException ex) {
            log.warn("拒绝访问目录: {}", directory);
            return List.of("错误: 无权访问该路径");
        }
        catch (IOException e) {
            log.error("列出文件失败", e);
            return List.of("错误: 读取目录失败");
        }
    }

    /**
     * 读取文件内容
     *
     * @param filePath 文件路径,相对于基础目录
     * @return 文件内容
     */
    @Tool(description = "读取指定文件的内容,返回文件的文本内容")
    public String readFile(
            @ToolParam(description = "文件路径,相对于基础目录,例如: README.md, docs/guide.txt")
            String filePath) {

        log.info("工具被调用: readFile, 参数: filePath={}", filePath);

        try {
            Path path = resolvePath(filePath);

            if (!Files.exists(path)) {
                return "错误: 文件不存在";
            }

            Path realPath = resolveRealPath(path);
            if (!Files.isRegularFile(realPath)) {
                return "错误: 不是一个文件";
            }

            // 限制文件大小,防止读取过大文件
            long fileSize = Files.size(realPath);
            if (fileSize > 1024 * 1024) { // 1MB 限制
                return "错误: 文件过大,超过 1MB 限制";
            }

            String content = Files.readString(realPath);
            log.info("读取文件成功,大小: {} 字节", fileSize);
            return content;

        }
        catch (SecurityException ex) {
            log.warn("拒绝访问文件: {}", filePath);
            return "错误: 无权访问该路径";
        }
        catch (IOException e) {
            log.error("读取文件失败", e);
            return "错误: 读取文件失败";
        }
    }

    /**
     * 获取基础目录
     *
     * @return 基础目录路径
     */
    public String getBaseDir() {
        return baseDir.toString();
    }

    /**
     * 先做词法路径检查，再在目标存在时做真实路径检查。
     */
    private Path resolvePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return baseDir;
        }

        Path path = baseDir.resolve(relativePath).normalize();
        if (!path.startsWith(baseDir)) {
            log.warn("路径遍历攻击尝试: {}", relativePath);
            throw new SecurityException("路径超出基础目录");
        }
        return path;
    }

    /**
     * 防止基础目录内的符号链接把访问转移到目录外。
     */
    private Path resolveRealPath(Path path) throws IOException {
        Path realPath = path.toRealPath();
        if (!realPath.startsWith(baseDir)) {
            log.warn("符号链接越界访问尝试: {}", path);
            throw new SecurityException("路径超出基础目录");
        }
        return realPath;
    }
}
