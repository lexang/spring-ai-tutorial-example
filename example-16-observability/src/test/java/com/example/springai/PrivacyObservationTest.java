package com.example.springai;

import com.example.springai.config.PrivacyVectorObservationConvention;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyObservationTest {
    @Test void privateQueryAndFilterAreNotConventionTags() {
        var context = new VectorStoreObservationContext.Builder("simple", "query")
                .dimensions(3).queryRequest(SearchRequest.builder().query("私人查询")
                        .filterExpression("tenant == 'private-tenant'").topK(3).build()).build();
        var convention = new PrivacyVectorObservationConvention();
        assertThat(convention.getHighCardinalityKeyValues(context).toString())
                .doesNotContain("私人查询", "private-tenant", "db.vector.query.content", "db.vector.query.filter");
        assertThat(convention.getLowCardinalityKeyValues(context).toString()).contains("db.operation.name", "query");
    }
}
