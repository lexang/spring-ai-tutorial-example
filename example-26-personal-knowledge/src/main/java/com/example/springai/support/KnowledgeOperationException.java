package com.example.springai.support;

public class KnowledgeOperationException extends RuntimeException {
    public KnowledgeOperationException(String message) { super(message); }
    public KnowledgeOperationException(String message, Throwable cause) { super(message, cause); }
}
