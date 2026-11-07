package com.minimax.examples.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogServiceTest {

    private final CatalogService catalog = new CatalogService();

    @Test
    void exposesResourcePromptAndCompletionData() {
        assertThat(this.catalog.systemInfo()).contains("Java=");
        assertThat(this.catalog.codeReview("Java").messages()).hasSize(1);
        assertThat(this.catalog.completeLanguage("py")).containsExactly("Python");
    }

}
