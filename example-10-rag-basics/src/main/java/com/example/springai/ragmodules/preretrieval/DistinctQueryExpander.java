package com.example.springai.ragmodules.preretrieval;

import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;

/**
 * 对查询扩展结果做保序去重，避免重复 Query 在模块化 RAG 的结果映射阶段形成重复键。
 */
public final class DistinctQueryExpander implements QueryExpander {

    private final QueryExpander delegate;

    public DistinctQueryExpander(QueryExpander delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("被包装的查询扩展器不能为 null");
        }
        this.delegate = delegate;
    }

    @Override
    public List<Query> expand(Query query) {
        if (query == null) {
            throw new IllegalArgumentException("原始查询不能为 null");
        }

        List<Query> expandedQueries = this.delegate.expand(query);
        if (expandedQueries == null) {
            throw new IllegalStateException("查询扩展器返回了 null 列表");
        }

        LinkedHashSet<Query> uniqueQueries = new LinkedHashSet<>();
        for (Query expandedQuery : expandedQueries) {
            if (expandedQuery == null) {
                throw new IllegalStateException("查询扩展结果不能包含 null");
            }
            uniqueQueries.add(expandedQuery);
        }

        if (uniqueQueries.isEmpty()) {
            uniqueQueries.add(query);
        }
        return List.copyOf(uniqueQueries);
    }
}
