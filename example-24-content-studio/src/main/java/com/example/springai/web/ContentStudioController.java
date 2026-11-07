package com.example.springai.web;

import com.example.springai.service.ContentStudioService;
import com.example.springai.service.ContentStudioService.ContentPackage;
import com.example.springai.service.ContentStudioService.ContentRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/content")
public class ContentStudioController {

    private final ContentStudioService service;

    public ContentStudioController(ContentStudioService service) {
        this.service = service;
    }

    @PostMapping("/packages")
    public ContentPackage create(@RequestBody ContentRequest request) {
        return this.service.create(request);
    }

}
