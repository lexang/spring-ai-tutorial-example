package com.minimax.examples.service;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FileToolRegistrationTests {
    @TempDir Path directory;

    @Test void fileToolsAreNotBeansByDefault() {
        new ApplicationContextRunner().withUserConfiguration(FileService.class)
                .withPropertyValues("app.data-directory=" + directory)
                .run(context -> assertThat(context).doesNotHaveBean(FileService.class));
    }

    @Test void explicitEnablementRetainsTheOptionalImplementation() {
        new ApplicationContextRunner().withUserConfiguration(FileService.class)
                .withPropertyValues("app.file-tools-enabled=true", "app.data-directory=" + directory)
                .run(context -> assertThat(context).hasSingleBean(FileService.class));
    }
}
