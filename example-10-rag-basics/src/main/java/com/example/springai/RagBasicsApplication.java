package com.example.springai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI 2.0 RAG 示例应用。
 *
 * <p>包含 QuestionAnswerAdvisor、RetrievalAugmentationAdvisor、查询转换、
 * 查询扩展、文档合并与后处理示例。</p>
 */
@SpringBootApplication
public class RagBasicsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagBasicsApplication.class, args);
    }
}
