package com.example.springai.model;

import java.util.List;

public final class CodeModels {

    private CodeModels() {
    }

    public record CodeIssue(String severity, int line, String message, String suggestion) {
    }

    public record CodeReview(String summary, List<CodeIssue> issues, int score, List<String> suggestions) {
    }

    public record TestPlan(String framework, List<String> testCases, List<String> notes) {
    }

    public record GeneratedImplementation(String language, String code, String explanation,
            List<String> warnings) {
    }

}
