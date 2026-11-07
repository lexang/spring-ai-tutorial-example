package com.example.springai;

/**
 * 表示模型调用完成，但没有返回本示例所需的文本结果。
 */
public final class EmptyChatResponseException extends IllegalStateException {

    public EmptyChatResponseException(String message) {
        super(message);
    }

}
