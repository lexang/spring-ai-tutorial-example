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

import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAI 2.0.2 的 SDK HTTP 定制点，按需开启一个无敏感信息的演示请求头。 */
@Configuration(proxyBeanMethods = false)
public class OpenAiTransportConfig {

    @Bean
    @ConditionalOnProperty(name = "demo.http.add-example-header", havingValue = "true")
    OpenAiHttpClientBuilderCustomizer exampleHeader() {
        return builder -> builder.interceptor(chain -> chain.proceed(chain.request().newBuilder()
                .header("X-Spring-AI-Example", "chatclient-2.0").build()));
    }
}
