package com.example.springai.web;

import com.example.springai.model.TranslationModels.TranslationRequest;
import com.example.springai.model.TranslationModels.TranslationResult;
import com.example.springai.service.TranslationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/translation")
public class TranslationController {

    private final TranslationService service;

    public TranslationController(TranslationService service) {
        this.service = service;
    }

    @PostMapping
    public TranslationResult translate(@RequestBody TranslationRequest request) {
        return this.service.translate(request);
    }

}
