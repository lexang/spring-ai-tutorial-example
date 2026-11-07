package com.example.springai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI Embeddings 示例应用
 *
 * 本示例演示:
 * - 文本向量化
 * - 相似度计算
 * - 语义搜索
 * - 文本聚类
 *
 * @author Spring AI Tutorials
 */
@SpringBootApplication
public class EmbeddingsApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmbeddingsApplication.class, args);
        System.out.println("""

            ========================================
            Spring AI Embeddings 示例已启动!
            ========================================

            试试这些接口:

            1. 文本嵌入:
               curl "http://localhost:8080/api/embed?text=人工智能"

            2. 相似度计算:
               curl "http://localhost:8080/api/similarity?text1=猫是宠物&text2=狗是宠物"

            3. 语义搜索:
               curl "http://localhost:8080/api/search?query=Java编程"

            ========================================
            """);
    }

}
