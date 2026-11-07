package com.example.springai.ragmodules.preretrieval;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistinctQueryExpanderTests {

    @Test
    void shouldRemoveDuplicateQueriesAndPreserveFirstOccurrenceOrder() {
        Query original = new Query("原始查询");
        Query variant = original.mutate().text("查询变体").build();
        QueryExpander delegate = query -> List.of(query, variant, variant, query);

        List<Query> expanded = new DistinctQueryExpander(delegate).expand(original);

        assertThat(expanded).containsExactly(original, variant);
    }

    @Test
    void shouldFallBackToOriginalQueryWhenDelegateReturnsEmptyList() {
        Query original = new Query("原始查询");

        List<Query> expanded = new DistinctQueryExpander(query -> List.of()).expand(original);

        assertThat(expanded).containsExactly(original);
    }

    @Test
    void shouldRejectNullListFromDelegate() {
        Query original = new Query("原始查询");

        assertThatThrownBy(() -> new DistinctQueryExpander(query -> null).expand(original))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("查询扩展器返回了 null 列表");
    }

    @Test
    void shouldRejectNullQueryElement() {
        Query original = new Query("原始查询");

        assertThatThrownBy(() -> new DistinctQueryExpander(query -> java.util.Arrays.asList(query, null))
                .expand(original))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("查询扩展结果不能包含 null");
    }
}
