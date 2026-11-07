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

import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;

/** 可复用的摘要 Logger，不把消息正文、请求头和租户标识写入 DEBUG 日志。 */
public final class ClientLoggers {

    private ClientLoggers() {
    }

    public static SimpleLoggerAdvisor summaries() {
        return SimpleLoggerAdvisor.builder()
                .requestToString(request -> "消息数=" + request.prompt().getInstructions().size())
                .responseToString(response -> {
                    if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                        return "响应为空";
                    }
                    String text = response.getResult().getOutput().getText();
                    return "响应长度=" + (text == null ? 0 : text.length());
                })
                .build();
    }
}
