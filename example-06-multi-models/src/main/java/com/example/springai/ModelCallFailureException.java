package com.example.springai;

/** 区分输入与模型失败，不暴露供应商错误原文。 */
final class ModelCallFailureException extends RuntimeException {
    ModelCallFailureException(String message, Throwable cause) { super(message, cause); }
}
