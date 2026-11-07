package com.example.springai.ragmodules.preretrieval;

import com.example.springai.support.RagChatClientBuilders;
import com.example.springai.support.RagInputValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MultilingualRagService {

    private static final Logger logger = LoggerFactory.getLogger(MultilingualRagService.class);

    private final TranslationQueryTransformer translationTransformer;

    public MultilingualRagService(ChatClient.Builder builder) {
        // 创建翻译转换器 (目标语言: 英语)
        this.translationTransformer = TranslationQueryTransformer.builder()
                .chatClientBuilder(RagChatClientBuilders.forTransformation(builder))
                .targetLanguage("english")
                .build();
    }

    /**
     * 翻译查询
     */
    public Query translateQuery(String query) {
        Query originalQuery = new Query(RagInputValidator.requireQuestion(query));
        Query translatedQuery = translationTransformer.transform(originalQuery);

        logger.info("查询翻译结果：{}", translatedQuery.text());

        return translatedQuery;
    }
}
