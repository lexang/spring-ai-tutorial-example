package com.example.springai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI ETL Pipeline 示例应用
 *
 * 演示如何使用 Spring AI 的 ETL 组件处理文档数据：
 * - DocumentReader: 从各种格式读取文档
 * - DocumentTransformer: 对文档进行转换和增强
 * - DocumentWriter: 将文档写入向量存储
 */
@SpringBootApplication
public class EtlPipelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(EtlPipelineApplication.class, args);
    }
}
