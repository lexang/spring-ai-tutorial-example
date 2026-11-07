package com.example.springai;

/** 对外提供中文错误，供应商原始异常仅保留在内部 cause 中。 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
