package com.example.springai.ragmodules.preretrieval;

import com.example.springai.support.RagChatClientBuilders;
import com.example.springai.support.RagInputValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QueryExpansionService {

    private static final Logger logger = LoggerFactory.getLogger(QueryExpansionService.class);

    private final QueryExpander queryExpander;

    public QueryExpansionService(ChatClient.Builder builder) {
        this.queryExpander = new DistinctQueryExpander(MultiQueryExpander.builder()
                .chatClientBuilder(RagChatClientBuilders.forTransformation(builder))
                .numberOfQueries(3)
                .includeOriginal(true)
                .build());
    }

    /**
     * 扩展查询
     */
    public List<Query> expandQuery(String originalQuery) {
        Query query = new Query(RagInputValidator.requireQuestion(originalQuery));
        List<Query> expandedQueries = queryExpander.expand(query);

        logger.info("查询扩展后共得到 {} 个查询（含原始查询）", expandedQueries.size());

        return expandedQueries;
    }
}
