package com.example.springai.support;

/**
 * 可选模型能力尚未启用。
 */
public class FeatureUnavailableException extends RuntimeException {

    public FeatureUnavailableException(String message) {
        super(message);
    }
}
