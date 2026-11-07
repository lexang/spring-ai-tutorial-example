package com.example.springai.exception;

import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;

public class CustomExceptionProcessor implements ToolExecutionExceptionProcessor {

    @Override
    public String process(ToolExecutionException exception) {
        Throwable cause = exception.getCause();

        if (cause instanceof IllegalArgumentException) {
            // 返回友好的错误消息给模型
            return "错误: " + cause.getMessage();
        }

//        if (cause instanceof DatabaseException) {
//            // 数据库异常不发送给模型，直接抛出
//            throw exception;
//        }
        if (cause instanceof CustomerNotFoundException) {
            return "未找到指定的客户记录，请检查客户 ID";
        }

        // 默认错误消息
        return "工具执行失败: " + exception.getMessage();
    }
}
