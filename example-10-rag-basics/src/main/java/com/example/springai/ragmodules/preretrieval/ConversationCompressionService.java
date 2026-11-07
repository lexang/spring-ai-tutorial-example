package com.example.springai.ragmodules.preretrieval;

import com.example.springai.support.RagChatClientBuilders;
import com.example.springai.support.RagInputValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 将对话历史和追问压缩为独立检索查询。
 */
@Service
public class ConversationCompressionService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationCompressionService.class);

    private final CompressionQueryTransformer compressionTransformer;

    public ConversationCompressionService(ChatClient.Builder builder) {
        this.compressionTransformer = CompressionQueryTransformer.builder()
                .chatClientBuilder(RagChatClientBuilders.forTransformation(builder))
                .build();
    }

    /**
     * 压缩对话历史
     */
    public Query compressConversation(String currentQuestion, List<Message> history) {
        if (history == null) {
            throw new IllegalArgumentException("对话历史不能为 null");
        }
        Query query = Query.builder()
                .text(RagInputValidator.requireQuestion(currentQuestion))
                .history(history)
                .build();

        Query compressedQuery = compressionTransformer.transform(query);

        logger.info("已将 {} 条历史消息压缩为独立查询：{}", history.size(), compressedQuery.text());

        return compressedQuery;
    }
}
