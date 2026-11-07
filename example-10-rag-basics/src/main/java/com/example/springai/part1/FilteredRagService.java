package com.example.springai.part1;

import com.example.springai.support.RagInputValidator;
import com.example.springai.support.RagResponseValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class FilteredRagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public FilteredRagService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder.clone().build();
        this.vectorStore = vectorStore;
    }

    /**
     * 只检索特定产品的文档。
     */
    public String askWithFilter(String question, String product) {
        return ask(question, product, null);
    }

    /**
     * 按产品和类别组合过滤。
     */
    public String askWithComplexFilter(String question, String product, String category) {
        return ask(question, product, category);
    }

    public String ask(String question, String product, String category) {
        String validatedQuestion = RagInputValidator.requireQuestion(question);
        SearchRequest.Builder searchRequest = SearchRequest.builder()
                .similarityThreshold(0.50)
                .topK(5);
        RagFilters.productAndCategory(product, category)
                .ifPresent(searchRequest::filterExpression);

        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(this.vectorStore)
                .searchRequest(searchRequest.build())
                .build();

        String content = this.chatClient.prompt()
                .user(validatedQuestion)
                .advisors(advisor)
                .call()
                .content();
        return RagResponseValidator.requireContent(content);
    }
}
