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

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.ai.tool.annotation.Tool;

/** 可直接注册到 ChatClient 的只读日期工具。 */
public final class DateTools {

    @Tool(description = "查询当前 UTC 日期，返回 ISO 日期字符串")
    public String currentDate() {
        return LocalDate.now(Clock.systemUTC()).toString();
    }
}
