package com.example.springai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatModel;

import com.example.springai.model.DeterministicJudgeChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 评估器和离线 Judge 模型的配置。 */
@Configuration(proxyBeanMethods = false)
public class EvaluationConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.evaluation.offline", havingValue = "true", matchIfMissing = true)
    ChatModel evaluationChatModel() {
        return new DeterministicJudgeChatModel();
    }

    @Bean
    RelevancyEvaluator relevancyEvaluator(ChatModel evaluationChatModel) {
        return RelevancyEvaluator.builder()
            .chatClientBuilder(judgeClientBuilder(evaluationChatModel))
            .build();
    }

    @Bean
    FactCheckingEvaluator factCheckingEvaluator(ChatModel evaluationChatModel) {
        return FactCheckingEvaluator.builder(judgeClientBuilder(evaluationChatModel)).build();
    }

    private ChatClient.Builder judgeClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

}
