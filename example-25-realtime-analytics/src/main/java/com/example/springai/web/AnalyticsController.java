package com.example.springai.web;

import com.example.springai.model.AnalyticsModels.AnalysisRequest;
import com.example.springai.model.AnalyticsModels.AnalysisResult;
import com.example.springai.model.AnalyticsModels.Catalog;
import com.example.springai.model.AnalyticsModels.EventReceipt;
import com.example.springai.model.AnalyticsModels.EventRequest;
import com.example.springai.service.AnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/catalog")
    public Catalog catalog() {
        return this.service.catalog();
    }

    @PostMapping("/query")
    public AnalysisResult analyze(@RequestBody AnalysisRequest request) {
        return this.service.analyze(request);
    }

    @PostMapping("/events")
    public ResponseEntity<EventReceipt> addEvent(@RequestBody EventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.service.addEvent(request));
    }

}
