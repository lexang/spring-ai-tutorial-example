package com.example.springai.model;

public final class ContentModels {

    private ContentModels() {
    }

    public record ContentPlan(String title, String article, String imagePrompt, String speechText) {
    }

}
