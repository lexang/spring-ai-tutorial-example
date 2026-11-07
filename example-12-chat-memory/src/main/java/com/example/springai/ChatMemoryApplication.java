package com.example.springai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI 2.0 Chat Memory 示例应用。
 *
 * <p>默认不创建 ChatModel 和 EmbeddingModel，因此内存窗口、JDBC 存储及其测试
 * 可以完全离线运行。模型对话和向量记忆由环境变量显式启用。</p>
 */
@SpringBootApplication
public class ChatMemoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatMemoryApplication.class, args);
    }
}
