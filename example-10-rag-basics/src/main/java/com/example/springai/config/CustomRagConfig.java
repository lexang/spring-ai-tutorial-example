package com.example.springai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomRagConfig {

    @Bean("customRagChatClient")
    public ChatClient customRagChatClient(ChatClient.Builder builder, VectorStore vectorStore) {

        // 自定义提示模板
        PromptTemplate customPromptTemplate = PromptTemplate.builder()
                .template("""
                {query}

                参考信息如下:

                =====================
                {question_answer_context}
                =====================

                请基于上述参考信息回答问题。

                规则:
                1. 如果参考信息中没有答案，请明确说明"根据提供的信息无法回答该问题"
                2. 不要编造信息
                3. 尽可能引用具体的参考信息
                4. 使用简洁清晰的语言
                """)
                .build();

        // 创建使用自定义模板的 advisor
        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .promptTemplate(customPromptTemplate)
                .searchRequest(SearchRequest.builder()
                        .similarityThreshold(0.75)
                        .topK(5)
                        .build())
                .build();

        return builder.clone()
                .defaultAdvisors(qaAdvisor)
                .build();
    }
}
