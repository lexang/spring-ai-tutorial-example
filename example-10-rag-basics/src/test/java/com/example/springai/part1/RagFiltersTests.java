package com.example.springai.part1;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThat;

class RagFiltersTests {

    @Test
    void shouldBuildCombinedFilterUsingStructuredValues() {
        String product = "Spring AI' || category == '监控";

        Filter.Expression expression = RagFilters.productAndCategory(product, "RAG").orElseThrow();

        assertThat(expression.type()).isEqualTo(Filter.ExpressionType.AND);
        Filter.Expression productExpression = (Filter.Expression) expression.left();
        Filter.Expression categoryExpression = (Filter.Expression) expression.right();
        assertThat(((Filter.Value) productExpression.right()).value()).isEqualTo(product);
        assertThat(((Filter.Value) categoryExpression.right()).value()).isEqualTo("RAG");
    }

    @Test
    void shouldReturnEmptyWhenNoFilterWasProvided() {
        assertThat(RagFilters.productAndCategory(" ", null)).isEmpty();
    }
}
