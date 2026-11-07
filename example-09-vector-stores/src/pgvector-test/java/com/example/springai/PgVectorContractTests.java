package com.example.springai;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** -Ppgvector 加入测试；默认不连接数据库，显式启用后只创建独立随机测试表。 */
class PgVectorContractTests {

    @Test void disabledInitializationDoesNotIssueDdl() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PgVectorStore store = PgVectorStore.builder(jdbc, new FixedEmbeddingModel())
                .dimensions(3).initializeSchema(false).vectorTableValidationsEnabled(false).build();
        store.afterPropertiesSet();
        verifyNoInteractions(jdbc);
        assertThat(store.getNativeClient()).contains(jdbc);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_PGVECTOR_INTEGRATION_TESTS", matches = "true")
    void realDatabaseRoundTripPersistsUpdatesFiltersAndDeletes() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(requiredEnvironment("PGVECTOR_TEST_JDBC_URL"));
        dataSource.setUsername(requiredEnvironment("PGVECTOR_TEST_USERNAME"));
        dataSource.setPassword(requiredEnvironment("PGVECTOR_TEST_PASSWORD"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String tableName = "pgvector_it_" + UUID.randomUUID().toString().replace("-", "");
        if (!tableName.matches("pgvector_it_[a-f0-9]{32}")) { throw new IllegalStateException("测试表名无效"); }
        PgVectorStore store = PgVectorStore.builder(jdbc, new FixedEmbeddingModel())
                .vectorTableName(tableName).dimensions(3)
                .indexType(PgVectorStore.PgIndexType.NONE).initializeSchema(true).vectorTableValidationsEnabled(true).build();
        store.afterPropertiesSet();
        String id = UUID.randomUUID().toString();
        try {
            store.add(List.of(new Document(id, "Java 初版", java.util.Map.of("category", "技术"))));
            store.add(List.of(new Document(id, "Java 更新版", java.util.Map.of("category", "技术")),
                    new Document("Docker 容器", java.util.Map.of("category", "运维"))));
            var request = SearchRequest.builder().query("Java").topK(5).similarityThreshold(0.7)
                    .filterExpression(new FilterExpressionBuilder().eq("category", "技术").build()).build();
            assertThat(store.similaritySearch(request)).singleElement()
                    .extracting(Document::getText).isEqualTo("Java 更新版");
            PgVectorStore reopened = PgVectorStore.builder(jdbc, new FixedEmbeddingModel())
                    .vectorTableName(tableName).dimensions(3).initializeSchema(false).build();
            assertThat(reopened.similaritySearch(request)).hasSize(1);
            store.delete(List.of(id));
            assertThat(store.similaritySearch(request)).isEmpty();
            store.delete(new FilterExpressionBuilder().eq("category", "运维").build());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM public." + tableName, Long.class)).isZero();
        }
        finally {
            // 仅删除本测试创建且已验证名称的随机表，不操作教学或生产表。
            jdbc.execute("DROP TABLE IF EXISTS public." + tableName);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) { throw new IllegalStateException("缺少专用测试数据库环境变量: " + name); }
        return value;
    }

    static class FixedEmbeddingModel implements EmbeddingModel {
        @Override public int dimensions() { return 3; }
        @Override public float[] embed(Document document) { return vector(document.getText()); }
        @Override public EmbeddingResponse call(EmbeddingRequest request) {
            return new EmbeddingResponse(IntStream.range(0, request.getInstructions().size())
                    .mapToObj(i -> new Embedding(vector(request.getInstructions().get(i)), i)).toList());
        }
        private static float[] vector(String text) { return text.contains("Java") ? new float[] {1, 0, 0} : new float[] {0, 1, 0}; }
    }
}
