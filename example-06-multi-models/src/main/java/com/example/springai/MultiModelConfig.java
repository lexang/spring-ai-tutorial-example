package com.example.springai;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientBuilderConfigurer;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 多模型配置类
 *
 * 创建多个不同配置的 ChatClient Bean
 *
 * @author Spring AI Tutorial
 */
@Configuration
public class MultiModelConfig {

    @Bean
    @Primary
    public OpenAiChatModel openAiChatModel(
            @Value("${app.models.openai.api-key}") String apiKey,
            @Value("${app.models.openai.model}") String model,
            ObjectProvider<ObservationRegistry> observationRegistry) {
        return OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey(apiKey)
                        .model(model)
                        .temperature(0.7)
                        .build())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .build();
    }

    @Bean
    public AnthropicChatModel miniMaxChatModel(
            @Value("${app.models.minimax.api-key}") String apiKey,
            @Value("${app.models.minimax.base-url}") String baseUrl,
            @Value("${app.models.minimax.model}") String model,
            ObjectProvider<ObservationRegistry> observationRegistry) {
        return AnthropicChatModel.builder()
                .options(AnthropicChatOptions.builder()
                        .apiKey(apiKey)
                        .baseUrl(baseUrl)
                        .model(model)
                        .maxTokens(2048)
                        .build())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .build();
    }

    /**
     * 简洁任务 ChatClient
     * <p>
     * 特点:
     * - 继承已配置的 OpenAI 模型
     * - 温度参数 0.3 只描述配置，不保证确定输出
     * - 适用于简单问答、翻译等任务
     */
    @Bean
    @Qualifier("fast")
    public ChatClient fastChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("你是一个快速响应的助手，简洁明了地回答问题。")
                .defaultOptions(OpenAiChatOptions.builder()
                        .temperature(0.3)          // 降低随机性
                        .maxTokens(500))           // 限制输出长度
                .build();
    }

    /**
     * 通用任务 ChatClient
     * <p>
     * 特点:
     * - 继承已配置的 OpenAI 模型
     * - 默认温度配置 0.7，不保证回答质量
     * - 适用于一般性任务
     */
    @Bean
    @Qualifier("standard")
    public ChatClient standardChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("你是一个友好的 AI 助手，提供有帮助的回答。")
                .build();
    }

    /**
     * 写作任务 ChatClient
     * <p>
     * 特点:
     * - 继承已配置的 OpenAI 模型
     * - 配置温度 1.2，实际效果需按模型验证
     * - 适用于创意写作、头脑风暴等任务
     */
    @Bean
    @Qualifier("creative")
    public ChatClient creativeChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("你是一个富有创造力的助手，帮助用户进行创意思考和写作。")
                .defaultOptions(OpenAiChatOptions.builder()
                        .temperature(1.2)          // 提高创造性
                        .maxTokens(1000))          // 允许更长的创意输出
                .build();
    }

    /**
     * 分析任务 ChatClient
     * <p>
     * 特点:
     * - 继承已配置的 OpenAI 模型
     * - 配置温度 0.2，不表示事实正确性保证
     * - 适用于数据分析、逻辑推理等任务
     */
    @Bean
    @Qualifier("analytical")
    public ChatClient analyticalChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("你是一个逻辑严谨的分析助手，提供深入的分析和推理。")
                .defaultOptions(OpenAiChatOptions.builder()
                        .temperature(0.2))          // 请求参数，不保证输出质量
                .build();
    }


    // 使用多个 AI 模型时，您可以为每个模型定义单独的 bean
    @Bean
    @Qualifier("minimax")
    public ChatClient miniMaxChatClient(AnthropicChatModel miniMaxChatModel,
            ChatClientBuilderConfigurer configurer,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatClientObservationConvention> chatClientObservationConvention,
            ObjectProvider<AdvisorObservationConvention> advisorObservationConvention,
            ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilder) {
        ChatClient.Builder builder = ChatClient.builder(miniMaxChatModel,
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
                chatClientObservationConvention.getIfUnique(),
                advisorObservationConvention.getIfUnique(),
                toolCallingAdvisorBuilder.getIfAvailable());
        return configurer.configure(builder)
                .defaultSystem("你是一个擅长长文本分析的助手。")
                .build();
    }

}
