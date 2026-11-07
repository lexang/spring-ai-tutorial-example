package com.example.springai.part1;

import com.example.springai.support.RagInputValidator;
import com.example.springai.support.RagResponseValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class SimpleRagService {

    private final ChatClient chatClient;

    public SimpleRagService(ChatClient.Builder chatClientBuilder,
                            VectorStore vectorStore) {
        // 配置 RAG advisor
        this.chatClient = chatClientBuilder.clone()
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .build();
    }

    public String ask(String question) {
        // 直接提问，advisor 会自动检索相关文档
        String content = this.chatClient.prompt()
                .user(RagInputValidator.requireQuestion(question))
                .call()
                .content();
        return RagResponseValidator.requireContent(content);
    }
}
