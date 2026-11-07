/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.springai;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientBuilderConfigurer;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurerContractTest {

    @Test
    void configurerAppliesCustomizersButRegistryMustBeSuppliedSeparately() {
        Model model = new Model();
        ObservationRegistry registry = ObservationRegistry.create();
        AtomicInteger observed = new AtomicInteger();
        registry.observationConfig().observationHandler(new ObservationHandler<ChatClientObservationContext>() {
            @Override public boolean supportsContext(Observation.Context context) {
                return context instanceof ChatClientObservationContext;
            }
            @Override public void onStop(ChatClientObservationContext context) { observed.incrementAndGet(); }
        });
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChatClientAutoConfiguration.class))
                .withBean(ChatModel.class, () -> model)
                .withBean(ObservationRegistry.class, () -> registry)
                .withBean(ChatClientBuilderCustomizer.class, () ->
                        builder -> builder.defaultUser(u -> u.metadata("configured", true)))
                .run(context -> {
                    var configurer = context.getBean(ChatClientBuilderConfigurer.class);
                    configurer.configure(ChatClient.builder(model)).build().prompt().user("手动创建").call().content();
                    assertThat(model.prompt.getUserMessage().getMetadata()).containsEntry("configured", true);
                    assertThat(observed).hasValue(0);
                    configurer.configure(ChatClient.builder(model, registry, null, null, null))
                            .build().prompt().user("显式传入观测").call().content();
                    assertThat(observed).hasValue(1);
                    context.getBean(ChatClient.Builder.class).build().prompt().user("自动配置").call().content();
                    assertThat(observed).hasValue(2);
                });
    }

    @Test
    void aPrimaryChatClientDoesNotResolveAmbiguousChatModels() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChatClientAutoConfiguration.class))
                .withUserConfiguration(TwoModels.class)
                .run(context -> assertThatThrownBy(() -> context.getBean(ChatClient.Builder.class))
                        .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class));
    }

    @Test
    void compatibleEndpointsRemainIndependent() {
        var first = OpenAiCompatibleModels.create("http://127.0.0.1:18081/v1", "unused-a", "local-a",
                ObservationRegistry.NOOP);
        var second = OpenAiCompatibleModels.create("http://127.0.0.1:18082/v1", "unused-b", "local-b",
                ObservationRegistry.NOOP);
        assertThat(first).isNotSameAs(second);
        assertThat(first.getOptions().getModel()).isEqualTo("local-a");
        assertThat(second.getOptions().getModel()).isEqualTo("local-b");
        // 这里只验证构造与配置隔离，不连接上述本地端口。
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoModels {
        @Bean ChatModel first() { return new Model(); }
        @Bean ChatModel second() { return new Model(); }
        @Bean @Primary ChatClient preferred(@Qualifier("first") ChatModel model) {
            return ChatClient.create(model);
        }
    }

    static class Model implements ChatModel {
        Prompt prompt;
        @Override public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage("测试回答"))));
        }
    }
}
