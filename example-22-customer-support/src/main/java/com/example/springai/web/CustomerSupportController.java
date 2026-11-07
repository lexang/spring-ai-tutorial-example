package com.example.springai.web;

import com.example.springai.service.CustomerSupportService;
import com.example.springai.service.CustomerSupportService.ChatRequest;
import com.example.springai.service.CustomerSupportService.ChatResult;
import com.example.springai.service.CustomerSupportService.ClearResult;
import com.example.springai.service.CustomerSupportService.Status;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
public class CustomerSupportController {

    private final CustomerSupportService customerSupportService;

    public CustomerSupportController(CustomerSupportService customerSupportService) {
        this.customerSupportService = customerSupportService;
    }

    @GetMapping("/status")
    public Status status() {
        return this.customerSupportService.status();
    }

    @PostMapping("/chat")
    public ChatResult chat(@RequestBody ChatRequest request) {
        return this.customerSupportService.chat(request);
    }

    @DeleteMapping("/conversations")
    public ClearResult clear(@RequestParam String customerId, @RequestParam String conversationId) {
        return this.customerSupportService.clear(customerId, conversationId);
    }

}
