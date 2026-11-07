package com.example.springai;

import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

public class TokenExample {
    public void countTokens() {
        TokenCountEstimator estimator = new JTokkitTokenCountEstimator();

        String text = "Spring AI 是一个很棒的框架！";
        int tokens = estimator.estimate(text);

        System.out.println("文本: " + text);
        System.out.println("Token 数: " + tokens);


    }
}
