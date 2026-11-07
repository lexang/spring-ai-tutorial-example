package com.minimax.examples.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileServiceTest {

    @Test
    void readsWritesListsAndDeletesInsideConfiguredDirectory() throws Exception {
        Path directory = Files.createTempDirectory("mcp-file-test");
        FileService service = new FileService(directory);

        service.writeFile("note.txt", "中文内容");
        assertThat(service.listFiles().fileNames()).containsExactly("note.txt");
        assertThat(service.readFile("note.txt")).isEqualTo("中文内容");
        assertThat(service.deleteFile("note.txt")).contains("note.txt");
        assertThat(service.listFiles().fileNames()).isEmpty();
    }

    @Test
    void rejectsPathTraversal() throws Exception {
        FileService service = new FileService(Files.createTempDirectory("mcp-file-test"));
        assertThatThrownBy(() -> service.readFile("../secret.txt"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.writeFile("subdir/file.txt", "x"))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
