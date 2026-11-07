package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI VectorStore 示例应用。
 *
 * <p>演示 SimpleVectorStore、文档写入、相似度搜索、元数据过滤、删除和基础 RAG 问答。</p>
 */
@SpringBootApplication
public class VectorStoresApplication {

    private static final Logger log = LoggerFactory.getLogger(VectorStoresApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(VectorStoresApplication.class, args);
        log.info("Spring AI VectorStore 示例已启动，请使用 requests.http 调用接口");
    }
}
