package com.example.springai;

/**
 * 表示模型调用完成，但没有返回本示例所需的文本结果。
 */
final class EmptyTemplateResponseException extends IllegalStateException {

    EmptyTemplateResponseException(String message) {
        super(message);
    }

}
