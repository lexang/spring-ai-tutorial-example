package com.example.springai.part1;

import com.example.springai.support.RagInputValidator;
import com.example.springai.support.RagResponseValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ModularRagService {
    private final ChatClient strictChatClient;
    private final ChatClient allowEmptyContextChatClient;

    public ModularRagService(
            @Qualifier("modularRagClient") ChatClient strictChatClient,
            @Qualifier("allowEmptyContextRagClient") ChatClient allowEmptyContextChatClient) {
        this.strictChatClient = strictChatClient;
        this.allowEmptyContextChatClient = allowEmptyContextChatClient;
    }

    public String ask(String question) {
        String content = this.strictChatClient.prompt()
                .user(RagInputValidator.requireQuestion(question))
                .call()
                .content();
        return RagResponseValidator.requireContent(content);
    }

    public String askAllowingEmptyContext(String question) {
        String content = this.allowEmptyContextChatClient.prompt()
                .user(RagInputValidator.requireQuestion(question))
                .call()
                .content();
        return RagResponseValidator.requireContent(content);
    }

    /**
     * 请求级过滤会覆盖检索器自身配置的过滤表达式。
     */
    public String askFiltered(String question, String product, String category) {
        var filter = RagFilters.productAndCategory(product, category);
        if (filter.isEmpty()) {
            return ask(question);
        }

        String content = this.strictChatClient.prompt()
                .user(RagInputValidator.requireQuestion(question))
                .advisors(a -> a.param(
                        VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                        filter.get()
                ))
                .call()
                .content();
        return RagResponseValidator.requireContent(content);
    }
}
