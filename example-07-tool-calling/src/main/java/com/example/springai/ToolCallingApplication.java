package com.example.springai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI Tool Calling 示例应用（教程 07 上下篇）
 *
 * 本示例演示:
 * - 使用 @Tool 注解定义工具方法
 * - 信息检索类工具的实现
 * - 操作执行类工具的实现
 * - 工具参数的定义和验证
 *
 * @author Spring AI Tutorials
 */
@SpringBootApplication
public class ToolCallingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToolCallingApplication.class, args);
        System.out.println("""

            ========================================
            Spring AI Tool Calling 示例已启动!
            ========================================

            试试这些接口:

            1. 时间工具:
               curl "http://localhost:8080/api/datetime?question=明天是星期几"
               curl "http://localhost:8080/api/datetime?question=10分钟后提醒我"

            2. 计算器工具:
               curl "http://localhost:8080/api/calculator?expression=25+17"
               curl "http://localhost:8080/api/calculator?expression=100除以4"

            3. 天气工具:
               curl "http://localhost:8080/api/weather?question=北京今天天气怎么样"
               curl "http://localhost:8080/api/weather?question=上海未来3天天气"

            ========================================
            """);
    }

}
