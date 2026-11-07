package com.example.springai.ragmodules.preretrieval;

import com.example.springai.support.RagChatClientBuilders;
import com.example.springai.support.RagInputValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 将用户问题重写为更适合检索的查询。
 */
@Service
public class QueryRewriteService {

    private static final Logger logger = LoggerFactory.getLogger(QueryRewriteService.class);

    private final RewriteQueryTransformer queryTransformer;

    public QueryRewriteService(ChatClient.Builder builder) {
        this.queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(RagChatClientBuilders.forTransformation(builder))
                .build();
    }

    /**
     * 重写查询
     */
    public Query rewriteQuery(String originalQuery) {
        Query query = new Query(RagInputValidator.requireQuestion(originalQuery));
        Query rewrittenQuery = queryTransformer.transform(query);

        logger.info("查询重写完成");

        return rewrittenQuery;
    }
}
