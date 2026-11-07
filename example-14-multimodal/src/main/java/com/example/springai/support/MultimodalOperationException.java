package com.example.springai.support;

/** 模型或供应商调用失败、返回空结果时使用的统一异常。 */
public class MultimodalOperationException extends RuntimeException {

    public MultimodalOperationException(String message) {
        super(message);
    }

    public MultimodalOperationException(String message, Throwable cause) {
        super(message, cause);
    }

}
