package com.example.springai.web;

import com.example.springai.model.CodeModels.CodeReview;
import com.example.springai.model.CodeModels.GeneratedImplementation;
import com.example.springai.model.CodeModels.TestPlan;
import com.example.springai.service.CodeEngineeringService;
import com.example.springai.service.CodeEngineeringService.CodeRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/code")
public class CodeEngineeringController {

    private final CodeEngineeringService service;

    public CodeEngineeringController(CodeEngineeringService service) {
        this.service = service;
    }

    @PostMapping("/review")
    public CodeReview review(@RequestBody CodeRequest request) {
        return this.service.review(request);
    }

    @PostMapping("/tests")
    public TestPlan tests(@RequestBody CodeRequest request) {
        return this.service.generateTests(request);
    }

    @PostMapping("/implement")
    public GeneratedImplementation implement(@RequestBody CodeRequest request) {
        return this.service.generateImplementation(request);
    }

}
