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

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.util.Assert;

/** 为不同兼容端点创建独立模型；密钥由调用方从外部配置提供。 */
public final class OpenAiCompatibleModels {

    private OpenAiCompatibleModels() {
    }

    public static OpenAiChatModel create(String baseUrl, String apiKey, String model,
            ObservationRegistry observationRegistry) {
        Assert.hasText(baseUrl, "baseUrl 不能为空");
        Assert.hasText(apiKey, "apiKey 不能为空");
        Assert.hasText(model, "model 不能为空");
        return OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder().baseUrl(baseUrl).apiKey(apiKey).model(model).build())
                .observationRegistry(observationRegistry).build();
    }
}
