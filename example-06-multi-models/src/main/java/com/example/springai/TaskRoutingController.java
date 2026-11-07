package com.example.springai;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routing")
public final class TaskRoutingController {
    private final TaskRoutingService routing;
    public TaskRoutingController(TaskRoutingService routing) { this.routing = routing; }

    @PostMapping("/plan")
    public TaskRoutingService.RoutePlan plan(@RequestBody RoutingRequest request) {
        return this.routing.plan(request.task());
    }

    @PostMapping("/chat")
    public TaskRoutingService.RoutedReply chat(@RequestBody RoutingRequest request) {
        return this.routing.chat(request.task(), request.message());
    }

    public record RoutingRequest(TaskRoutingService.Task task, String message) { }
}
