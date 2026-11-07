package com.example.springai.support;

public class CodeEngineeringException extends RuntimeException {

    public CodeEngineeringException(String message) {
        super(message);
    }

    public CodeEngineeringException(String message, Throwable cause) {
        super(message, cause);
    }

}
