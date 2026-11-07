package com.example.springai.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentWriterControllerTest {

    @Test
    void writesOnlyTheFixedOutputFileAndReportsActualCharset(@TempDir Path outputDirectory) throws Exception {
        DocumentWriterController controller = new DocumentWriterController(
                resource("ETL 输出文本。".repeat(30), "sample.txt"), outputDirectory.toString());

        Map<String, Object> response = controller.writeFile(false, "NONE", false);

        Path outputFile = outputDirectory.resolve("etl-output.txt");
        assertThat(outputFile).exists();
        assertThat(Files.readString(outputFile, Charset.defaultCharset())).contains("ETL 输出文本");
        assertThat(response)
                .containsEntry("outputFile", outputFile.toAbsolutePath().normalize().toString())
                .containsEntry("encoding", Charset.defaultCharset().name());
    }

    @Test
    void rejectsAnOutputDirectoryThatIsASymbolicLink(@TempDir Path temporaryDirectory) throws Exception {
        Path target = temporaryDirectory.resolve("target");
        Files.createDirectory(target);
        Path link = temporaryDirectory.resolve("link");
        try {
            Files.createSymbolicLink(link, target);
        }
        catch (UnsupportedOperationException | SecurityException | IOException exception) {
            return;
        }

        DocumentWriterController controller = new DocumentWriterController(
                resource("ETL 输出文本。".repeat(30), "sample.txt"), link.toString());

        assertThatThrownBy(() -> controller.writeFile(false, "NONE", false))
                .isInstanceOf(com.example.springai.support.EtlOperationException.class)
                .hasMessageContaining("不能是符号链接");
    }

    private ByteArrayResource resource(String content, String description) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), description);
    }

}
