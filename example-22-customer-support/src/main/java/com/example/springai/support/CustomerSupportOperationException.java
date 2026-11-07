package com.example.springai.support;

public class CustomerSupportOperationException extends RuntimeException {

    public CustomerSupportOperationException(String message) {
        super(message);
    }

    public CustomerSupportOperationException(String message, Throwable cause) {
        super(message, cause);
    }

}
